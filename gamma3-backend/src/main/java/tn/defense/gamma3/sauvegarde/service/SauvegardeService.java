package tn.defense.gamma3.sauvegarde.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.service.JournalAuditService;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.parametre.service.ParametreSystemeService;
import tn.defense.gamma3.sauvegarde.dto.SauvegardeDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * RÔLE : déclencher et lister les sauvegardes de la base PostgreSQL (Phase 2 de la
 * gouvernance des rôles, voir docs/PROPOSITION_GOUVERNANCE_ROLES.md §4.1 — demande explicite
 * de l'utilisateur, 2026-09-11).
 *
 * CONCEPTION (recommandations du document ci-dessus, suivies à la lettre) :
 *   1. Le dump est produit CÔTÉ SERVEUR (fichier horodaté sous {@code backups/}) — jamais
 *      téléchargé depuis le navigateur : un export volumineux transitant par le poste client
 *      d'une session web serait une exposition inutile sur un système militaire. Aucun
 *      endpoint de cette classe ne sert le contenu d'un fichier de sauvegarde.
 *   2. Chaque sauvegarde (succès ET échec) est journalisée dans le Journal d'Audit.
 *   3. Une politique de rétention purge automatiquement les plus anciennes sauvegardes
 *      au-delà d'un nombre configurable par le SUPER_ADMIN (voir ParametreSystemeService).
 *
 * MÉCANISME : le backend n'a pas d'accès direct au binaire {@code pg_dump} depuis l'hôte
 * (Windows, sans client PostgreSQL installé) — mais le conteneur Docker de la base
 * (image {@code postgres:16-alpine}) l'embarque nativement. Reprend donc le même patron déjà
 * utilisé cette session pour interroger SQL Server via Docker : {@code docker exec <conteneur>
 * pg_dump ...}, la sortie standard étant redirigée directement vers le fichier cible (jamais
 * chargée en mémoire côté backend, pour rester supportable même sur une grosse base).
 *
 * LIMITE ASSUMÉE : cette approche suppose (a) que le client {@code docker} est sur le PATH
 * du compte qui exécute le backend, et (b) que le conteneur configuré est démarré et
 * accessible — les deux sont déjà des prérequis du projet (voir start.ps1). Si l'équipe migre
 * un jour vers un déploiement sans Docker (PostgreSQL natif), il suffira de remplacer la
 * commande construite dans {@link #construireCommande()} par un appel direct à {@code pg_dump}
 * sur l'hôte.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SauvegardeService {

    private final JournalAuditService journalAuditService;
    private final ParametreSystemeService parametreSystemeService;

    @Value("${application.sauvegarde.repertoire:backups}")
    private String repertoireConfig;

    @Value("${application.sauvegarde.conteneur-docker:gamma3_postgres}")
    private String conteneurDocker;

    @Value("${application.sauvegarde.utilisateur-db:gamma3_user}")
    private String utilisateurDb;

    @Value("${application.sauvegarde.nom-db:gamma3_db}")
    private String nomDb;

    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String PREFIXE_FICHIER = "gamma3_";
    private static final String EXTENSION = ".sql";
    private static final Duration DELAI_MAX = Duration.ofMinutes(10);

    /**
     * Déclenche un dump complet de la base courante. Bloquant (l'appelant — le contrôleur —
     * attend la fin du processus) : acceptable pour une opération manuelle déclenchée par un
     * administrateur, qui veut justement une confirmation immédiate de succès/échec.
     *
     * @param acteur    le SUPER_ADMIN à l'origine de la demande (pour la traçabilité)
     * @param adresseIp adresse de l'appelant (pour la traçabilité)
     * @return les métadonnées du fichier produit
     * @throws IllegalStateException si la sauvegarde échoue, pour quelque raison que ce soit
     *                                (le message est sûr à renvoyer tel quel au client — voir
     *                                le contrôleur)
     */
    public SauvegardeDto declencher(User acteur, String adresseIp) {
        Path repertoire = repertoireAbsolu();
        try {
            Files.createDirectories(repertoire);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le répertoire de sauvegarde : " + e.getMessage(), e);
        }

        String nomFichier = PREFIXE_FICHIER + LocalDateTime.now().format(HORODATAGE) + EXTENSION;
        Path cible = repertoire.resolve(nomFichier);
        Path erreurTmp = repertoire.resolve(nomFichier + ".err");

        Instant debut = Instant.now();
        ProcessBuilder pb = new ProcessBuilder(construireCommande());
        pb.redirectOutput(cible.toFile());
        pb.redirectError(erreurTmp.toFile());

        int codeRetour;
        try {
            Process process = pb.start();
            boolean termine = process.waitFor(DELAI_MAX.toSeconds(), TimeUnit.SECONDS);
            if (!termine) {
                process.destroyForcibly();
                nettoyer(cible, erreurTmp);
                journaliserEchec(acteur, adresseIp, "Délai maximal de " + DELAI_MAX.toMinutes() + " min dépassé — processus interrompu.");
                throw new IllegalStateException("La sauvegarde a dépassé le délai maximal de " + DELAI_MAX.toMinutes() + " minutes et a été interrompue.");
            }
            codeRetour = process.exitValue();
        } catch (IOException e) {
            nettoyer(cible, erreurTmp);
            journaliserEchec(acteur, adresseIp, "Impossible de lancer le processus (docker/pg_dump indisponible) : " + e.getMessage());
            throw new IllegalStateException("Impossible de lancer la sauvegarde — vérifiez que Docker et le conteneur '" + conteneurDocker + "' sont démarrés : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            nettoyer(cible, erreurTmp);
            journaliserEchec(acteur, adresseIp, "Interrompu pendant l'attente du processus.");
            throw new IllegalStateException("La sauvegarde a été interrompue.", e);
        }

        Duration duree = Duration.between(debut, Instant.now());

        if (codeRetour != 0) {
            String extrait = lireExtrait(erreurTmp);
            nettoyer(cible, erreurTmp);
            journaliserEchec(acteur, adresseIp, "pg_dump a échoué (code " + codeRetour + ") : " + extrait);
            throw new IllegalStateException("La sauvegarde a échoué (code " + codeRetour + ") : " + extrait);
        }

        long taille;
        try {
            taille = Files.size(cible);
        } catch (IOException e) {
            taille = -1;
        }
        // Un code retour 0 mais un fichier vide/absent signale un problème silencieux côté
        // Docker (ex: conteneur introuvable — `docker exec` peut renvoyer 0 dans certains cas
        // d'erreur de configuration selon la version du client) : traité comme un échec.
        if (taille <= 0) {
            nettoyer(cible, erreurTmp);
            journaliserEchec(acteur, adresseIp, "Fichier de sauvegarde vide malgré un code retour 0 (conteneur '" + conteneurDocker + "' introuvable ou vide ?).");
            throw new IllegalStateException("La sauvegarde a produit un fichier vide — vérifiez que le conteneur '" + conteneurDocker + "' est démarré et contient bien la base '" + nomDb + "'.");
        }

        supprimerSiExiste(erreurTmp);
        journalAuditService.enregistrer(acteur.getMatricule(), acteur.getFullName(), TypeActionAudit.SAUVEGARDE_DECLENCHEE,
                "Fichier " + nomFichier + ", " + formatTaille(taille) + ", " + duree.toSeconds() + " s.", adresseIp);

        appliquerRetention(repertoire);

        return new SauvegardeDto(nomFichier, taille, dateModification(cible));
    }

    /** Liste les sauvegardes existantes sur disque, les plus récentes en premier. */
    public List<SauvegardeDto> lister() {
        Path repertoire = repertoireAbsolu();
        if (!Files.isDirectory(repertoire)) return List.of();
        try (Stream<Path> flux = Files.list(repertoire)) {
            return flux.filter(SauvegardeService::estUnFichierDeSauvegarde)
                    .map(p -> new SauvegardeDto(p.getFileName().toString(), tailleSure(p), dateModification(p)))
                    .sorted(Comparator.comparing(SauvegardeDto::dateCreation).reversed())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de lister les sauvegardes : " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------------------------
    // Détails d'implémentation
    // ------------------------------------------------------------------------------------

    private List<String> construireCommande() {
        return List.of("docker", "exec", conteneurDocker,
                "pg_dump", "-U", utilisateurDb, "-d", nomDb,
                "--no-owner", "--clean", "--if-exists");
    }

    private Path repertoireAbsolu() {
        return Paths.get(repertoireConfig).toAbsolutePath().normalize();
    }

    private static boolean estUnFichierDeSauvegarde(Path p) {
        String nom = p.getFileName().toString();
        return Files.isRegularFile(p) && nom.startsWith(PREFIXE_FICHIER) && nom.endsWith(EXTENSION);
    }

    /** Purge les sauvegardes les plus anciennes au-delà du nombre configuré à conserver. */
    private void appliquerRetention(Path repertoire) {
        int retention = parametreSystemeService.getRetentionSauvegardes();
        List<Path> fichiers;
        try (Stream<Path> flux = Files.list(repertoire)) {
            fichiers = flux.filter(SauvegardeService::estUnFichierDeSauvegarde)
                    .sorted(Comparator.comparing(this::dateModification))
                    .toList();
        } catch (IOException e) {
            log.warn("Impossible d'appliquer la politique de rétention des sauvegardes : {}", e.getMessage());
            return;
        }
        int aSupprimer = fichiers.size() - retention;
        for (int i = 0; i < aSupprimer; i++) {
            Path fichier = fichiers.get(i);
            try {
                Files.delete(fichier);
                log.info("Sauvegarde expirée supprimée (rétention = {} fichiers) : {}", retention, fichier.getFileName());
            } catch (IOException e) {
                log.warn("Impossible de supprimer la sauvegarde expirée {} : {}", fichier.getFileName(), e.getMessage());
            }
        }
    }

    private void journaliserEchec(User acteur, String adresseIp, String details) {
        journalAuditService.enregistrer(acteur.getMatricule(), acteur.getFullName(),
                TypeActionAudit.SAUVEGARDE_ECHEC, details, adresseIp);
    }

    private void nettoyer(Path... fichiers) {
        for (Path f : fichiers) supprimerSiExiste(f);
    }

    private void supprimerSiExiste(Path fichier) {
        try {
            Files.deleteIfExists(fichier);
        } catch (IOException e) {
            log.warn("Impossible de nettoyer le fichier {} après un échec de sauvegarde : {}", fichier, e.getMessage());
        }
    }

    /** Ne remonte jamais la sortie d'erreur brute intégrale au client (risque de fuite de
     *  détails d'infrastructure) — un extrait court suffit à diagnostiquer, le détail complet
     *  reste dans les logs applicatifs. */
    private String lireExtrait(Path fichierErreur) {
        try {
            List<String> lignes = Files.readAllLines(fichierErreur);
            String texte = String.join(" ", lignes);
            log.error("Sortie d'erreur complète de pg_dump : {}", texte);
            return texte.length() > 300 ? texte.substring(0, 300) + "…" : texte;
        } catch (IOException e) {
            return "(sortie d'erreur illisible)";
        }
    }

    private long tailleSure(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }

    private LocalDateTime dateModification(Path p) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return LocalDateTime.now();
        }
    }

    private static String formatTaille(long octets) {
        if (octets < 1024) return octets + " o";
        if (octets < 1024 * 1024) return String.format("%.1f Ko", octets / 1024.0);
        return String.format("%.1f Mo", octets / (1024.0 * 1024.0));
    }
}
