package tn.defense.gamma3.distribution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.distribution.domain.*;
import tn.defense.gamma3.distribution.repository.BonSortieRepository;
import tn.defense.gamma3.distribution.repository.DemandeMaterielRepository;
import tn.defense.gamma3.distribution.repository.LigneDemandeRepository;
import tn.defense.gamma3.stock.api.dto.MouvementDto;
import tn.defense.gamma3.stock.domain.Stock;
import tn.defense.gamma3.stock.domain.TypeMouvement;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.stock.service.StockService;
import tn.defense.gamma3.stock.repository.MagasinRepository;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;
import tn.defense.gamma3.notification.domain.Notification;
import tn.defense.gamma3.notification.repository.NotificationRepository;
import tn.defense.gamma3.auth.repository.UserRepository;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.service.JournalAuditService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DistributionService {

    private final DemandeMaterielRepository demandeRepository;
    private final LigneDemandeRepository ligneRepository;
    private final BonSortieRepository bonSortieRepository;
    private final PlanArmementRepository planRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final ItemRepository itemRepository;
    private final StockService stockService;
    private final StockRepository stockRepository;
    private final MagasinRepository magasinRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final JournalAuditService journalAuditService;

    // ─── Constante : ID du Magasin Principal SGS (Nord/Bizerte) ───────────────────────
    // RÔLE : Magasin de référence pour les transferts inter-magasins et le routage par défaut.
    private static final Long SGS_ID = 1L;

    // ──────────────────────────────────────────────────────────────────────────
    // MÉTHODE 1 : creerDemande
    // RÔLE : Créer une demande de matériel et l'assigner au magasin optimal.
    //
    // ALGORITHME DE ROUTAGE GÉOGRAPHIQUE :
    //   - Si l'unité est dans la région Nord (base navale Bizerte) → SGS (ID 1)
    //   - Si l'unité est en région Centre → SM1 (ID 2)
    //   - Si l'unité est en région Sud → SM2 (ID 3)
    //   - Par défaut (inconnu) → SGS (magasin principal, toujours stocké)
    //
    // PARAMÈTRES :
    //   demande   – L'entité DemandeMateriel initée par le client
    //   lignes    – Liste des LigneDemande demandées
    // RETOUR : DemandeMateriel avec magasinDesigne assigné
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional
    public DemandeMateriel creerDemande(DemandeMateriel demande, List<LigneDemande> lignes) {
        UniteUtilisatrice unite = uniteRepository.findById(demande.getUnite().getId())
                .orElseThrow(() -> new IllegalArgumentException("Unité non trouvée"));
        demande.setUnite(unite);

        // DOUBLE VÉRIFICATION (règle métier du 2026-09-04) : une demande créée par le
        // Gestionnaire n'est PAS transmise à GAMMA3 immédiatement — elle attend d'abord la
        // validation du Commandant de la même unité. Ni routage de magasin, ni réservation
        // sur le Plan d'Armement, ni notification au DA_MANAGER à ce stade : voir
        // validerParCommandant(), qui exécute cette logique UNIQUEMENT si le Commandant
        // accepte. magasinDesigne reste donc volontairement null jusque-là.
        demande.setStatut(StatutDemande.EN_ATTENTE_VALIDATION_COMMANDANT);

        DemandeMateriel savedDemande = demandeRepository.save(demande);

        // Persister les lignes telles que soumises, SANS toucher au Plan d'Armement (la
        // vérification/réservation des quantités a lieu à la validation, pas ici) : le
        // Commandant doit voir exactement ce qui a été demandé pour pouvoir juger.
        for (LigneDemande ligne : lignes) {
            Item item = itemRepository.findById(ligne.getItem().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Article non trouvé dans le catalogue"));
            ligne.setItem(item);
            ligne.setDemande(savedDemande);
            ligneRepository.save(ligne);
        }
        savedDemande.setLignes(lignes);

        // Notifier le Commandant (et le Gestionnaire, la notification cible l'unité entière
        // faute de ciblage par compte individuel dans ce système) qu'une validation est
        // attendue.
        notificationRepository.save(Notification.builder()
                .title("Demande de Matériel en Attente de Validation")
                .message("Une nouvelle demande (" + savedDemande.getNumeroDemande() + ", "
                        + lignes.size() + " article(s)) a été soumise par le Gestionnaire de votre "
                        + "unité et attend la validation du Commandant avant envoi à la DA.")
                .type("MATERIEL")
                .uniteCode(unite.getCode())
                .referenceId(unite.getId().toString())
                .build());

        return savedDemande;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MÉTHODE 1bis : validerParCommandant
    // RÔLE : Le Commandant (chef d'unité) valide ou refuse une demande soumise par le
    //        Gestionnaire de SA MÊME unité — la deuxième des deux vérifications requises
    //        avant qu'une demande n'atteigne GAMMA3 (voir StatutDemande).
    //
    //        SI ACCEPTÉE : exécute maintenant ce que faisait l'ancien creerDemande() en une
    //        seule étape — vérification et réservation sur le Plan d'Armement, routage
    //        géographique vers le magasin optimal, notification du DA_MANAGER/ADMIN.
    //        SI REFUSÉE : statut REFUSE_COMMANDANT + motif, notifie le Gestionnaire. La
    //        demande n'atteint jamais GAMMA3.
    //
    // PARAMÈTRES :
    //   demandeId          – Identifiant de la demande à valider
    //   accepter           – true = validation, false = refus
    //   motifRefus         – Obligatoire si accepter=false
    //   commandantMatricule – Matricule du Commandant (audit/traçabilité)
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional
    public DemandeMateriel validerParCommandant(Long demandeId, boolean accepter, String motifRefus, String commandantMatricule) {
        DemandeMateriel demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande non trouvée"));

        if (demande.getStatut() != StatutDemande.EN_ATTENTE_VALIDATION_COMMANDANT) {
            throw new IllegalStateException("Cette demande n'est plus en attente de validation par le Commandant (statut actuel : " + demande.getStatut() + ").");
        }

        UniteUtilisatrice unite = demande.getUnite();
        List<LigneDemande> lignes = ligneRepository.findByDemande_Id(demandeId);

        if (!accepter) {
            demande.setStatut(StatutDemande.REFUSE_COMMANDANT);
            demande.setMotifRefus(motifRefus);
            DemandeMateriel refusee = demandeRepository.save(demande);

            notificationRepository.save(Notification.builder()
                    .title("Demande Refusée par le Commandant")
                    .message("Votre demande " + refusee.getNumeroDemande() + " a été refusée par le "
                            + "Commandant de votre unité. Motif : " + (motifRefus != null ? motifRefus : "non précisé") + ".")
                    .type("MATERIEL")
                    .uniteCode(unite.getCode())
                    .referenceId(unite.getId().toString())
                    .build());

            return refusee;
        }

        // ÉTAPE 1 : Valider les lignes de demande par rapport au Plan d'Armement et réserver
        // les quantités (déplacé ici depuis l'ancien creerDemande — voir commentaire ci-dessus)
        for (LigneDemande ligne : lignes) {
            Item item = ligne.getItem();

            PlanArmement plan = planRepository.findByUniteIdAndItemId(unite.getId(), item.getId())
                    .orElseThrow(() -> new IllegalArgumentException("L'article " + item.getDesignation() + " n'est pas autorisé dans le Plan d'Armement de cette unité."));

            BigDecimal totalVirtuel = plan.getQuantiteVirtuelle().add(ligne.getQuantiteDemandee());
            if (totalVirtuel.compareTo(plan.getQuantiteType()) > 0) {
                throw new IllegalArgumentException("La quantité demandée pour " + item.getDesignation() + " (" + ligne.getQuantiteDemandee() + ") dépasse la dotation maximale restante autorisée (" + plan.getQuantiteType().subtract(plan.getQuantiteVirtuelle()) + "). La demande reste en attente : contactez le Gestionnaire pour la corriger, ou refusez-la.");
            }

            plan.setQuantiteVirtuelle(totalVirtuel);
            planRepository.save(plan);
        }

        // ÉTAPE 2 : Routage géographique — déterminer le magasin le plus proche de l'unité cliente
        Magasin magasinOptimal = routerVersMagasinOptimal(unite);
        demande.setMagasinDesigne(magasinOptimal);
        demande.setStatut(StatutDemande.SOUMIS);
        DemandeMateriel validee = demandeRepository.save(demande);

        // ÉTAPE 3 : Notifier le DA_MANAGER du magasin désigné (et l'ADMIN pour information)
        notificationRepository.save(Notification.builder()
                .title("Nouvelle Demande de Matériel")
                .message("L'unité " + unite.getNom() + " a soumis la demande "
                        + validee.getNumeroDemande() + " ("
                        + lignes.size() + " article(s), validée par son Commandant). Soute désignée : "
                        + magasinOptimal.getNom() + ".")
                .type("MATERIEL")
                .uniteCode(null) // visible par l'ADMIN et les DA_MANAGERs
                .referenceId(unite.getId().toString())
                .build());

        // NE PAS faire `validee.setLignes(lignes)` ici : `validee` est une entité CHARGÉE
        // depuis la base (via demandeRepository.findById en début de méthode), donc sa
        // collection `lignes` est une PersistentCollection gérée par Hibernate. Remplacer
        // cette référence par une autre liste sur une entité chargée déclenche, au flush,
        // « A collection with cascade=all-delete-orphan was no longer referenced by the
        // owning entity instance » (car @OneToMany(orphanRemoval = true) sur DemandeMateriel.lignes),
        // ce qui faisait échouer TOUTE validation « accepter » avec une 500 et un rollback
        // complet. La branche « refuser » ci-dessus ne touchait pas `lignes` et fonctionnait :
        // c'est ce contraste qui a permis d'isoler la cause. Les lignes sont déjà rattachées
        // en base ; la réponse HTTP les sérialise via le chargement paresseux (Open Session
        // In View), exactement comme le fait déjà `return refusee;`.
        return validee;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MÉTHODE 2 : routerVersMagasinOptimal
    // RÔLE : Déterminer automatiquement la soute la plus proche de l'unité cliente.
    //        La logique se base sur la `baseNavale` de l'unité.
    // PARAMÈTRES : unite – L'unité utilisatrice cliente
    // RETOUR : Le Magasin désigné comme source optimale.
    // ──────────────────────────────────────────────────────────────────────────
    private Magasin routerVersMagasinOptimal(UniteUtilisatrice unite) {
        String baseNavale = unite.getBaseNavale() != null ? unite.getBaseNavale().toLowerCase() : "";

        // Mapping géographique : base navale → soute assignée
        // Règle de routage géographique Marine Nationale : base navale assignée à sa soute régionale de rattachement.
        Long magasinId;
        if (baseNavale.contains("bizerte") || baseNavale.contains("nord") || baseNavale.contains("sgs")) {
            magasinId = SGS_ID;       // Nord → SGS
        } else if (baseNavale.contains("centre") || baseNavale.contains("sfax") || baseNavale.contains("sousse")) {
            magasinId = 2L;           // Centre → SM1
        } else if (baseNavale.contains("sud") || baseNavale.contains("gabes") || baseNavale.contains("djerba") || baseNavale.contains("zarzis")) {
            magasinId = 3L;           // Sud → SM2
        } else {
            magasinId = SGS_ID;       // Inconnu → SGS par défaut (magasin principal)
        }

        return magasinRepository.findById(magasinId)
                .orElseGet(() -> magasinRepository.findById(SGS_ID)
                        .orElseThrow(() -> new IllegalStateException("Magasin SGS introuvable en base.")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MÉTHODE 3 : arbitrerDemande
    // RÔLE : Traiter l'arbitrage d'une demande par le DA_MANAGER du magasin désigné.
    //        Le magasinId passé en paramètre doit être celui du magasin désigné de la demande.
    //        L'ADMIN peut arbitrer toute demande, y compris les escalades.
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional
    public DemandeMateriel arbitrerDemande(Long demandeId, StatutDemande decision, String motifRefus, List<LigneDemande> arbitrageLignes, Long magasinId, String userMatricule) {
        DemandeMateriel demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable"));

        if (demande.getStatut() != StatutDemande.SOUMIS
                && demande.getStatut() != StatutDemande.EN_COURS_ARBITRAGE
                && demande.getStatut() != StatutDemande.ESCALADE_SGS) {
            throw new IllegalStateException("Cette demande a déjà été traitée.");
        }

        if (decision == StatutDemande.REFUSE) {
            demande.setStatut(StatutDemande.REFUSE);
            demande.setMotifRefus(motifRefus);

            // Annuler les réservations virtuelles
            List<LigneDemande> lignes = ligneRepository.findByDemande_Id(demandeId);
            for (LigneDemande ligne : lignes) {
                planRepository.findByUniteIdAndItemId(demande.getUnite().getId(), ligne.getItem().getId())
                        .ifPresent(plan -> {
                            BigDecimal newVirtuel = plan.getQuantiteVirtuelle().subtract(ligne.getQuantiteDemandee());
                            plan.setQuantiteVirtuelle(newVirtuel.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newVirtuel);
                            planRepository.save(plan);
                        });
            }
            DemandeMateriel saved = demandeRepository.save(demande);

            // Trigger Notification for Client Unit
            notificationRepository.save(Notification.builder()
                    .title("Demande de Matériel Refusée")
                    .message("Votre demande de matériel N° " + saved.getNumeroDemande() + " a été REFUSÉE. Motif : " + motifRefus)
                    .type("MATERIEL")
                    .uniteCode(saved.getUnite().getCode())
                    .referenceId(saved.getUnite().getId().toString())
                    .build());

            return saved;
        }

        // Cas Approbation (Totale ou Partielle)
        demande.setStatut(decision);

        if (magasinId != null) {
            Magasin newMagasin = magasinRepository.findById(magasinId)
                    .orElseThrow(() -> new IllegalArgumentException("Magasin non trouvé"));
            demande.setMagasinDesigne(newMagasin);
        }

        // Générer le Bon de Sortie (Statut: PREPARE)
        BonSortie bs = BonSortie.builder()
                .numeroBs("BS-" + demande.getNumeroDemande())
                .dateSortie(LocalDate.now())
                .transporteur("Service Transit DA")
                .vehiculeMatricule("MDN-10452")
                .statut("PREPARE")
                .magasinId(magasinId)
                .build();
        BonSortie savedBs = bonSortieRepository.save(bs);
        demande.setBonSortie(savedBs);

        // Traiter les lignes d'arbitrage
        for (LigneDemande arbLigne : arbitrageLignes) {
            LigneDemande originalLigne = ligneRepository.findById(arbLigne.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Ligne de demande introuvable"));

            BigDecimal accordee = arbLigne.getQuantiteAccordee() != null ? arbLigne.getQuantiteAccordee() : originalLigne.getQuantiteDemandee();
            originalLigne.setQuantiteAccordee(accordee);
            ligneRepository.save(originalLigne);

            // Ajuster les dotations virtuelles de l'unité (réservation approuvée)
            planRepository.findByUniteIdAndItemId(demande.getUnite().getId(), originalLigne.getItem().getId())
                    .ifPresent(plan -> {
                        // Relâcher l'excédent de réservation virtuelle
                        BigDecimal newVirtuel = plan.getQuantiteVirtuelle().subtract(originalLigne.getQuantiteDemandee()).add(accordee);
                        plan.setQuantiteVirtuelle(newVirtuel.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newVirtuel);
                        planRepository.save(plan);
                    });
        }

        DemandeMateriel saved = demandeRepository.save(demande);

        // Trigger Notification for Client Unit
        notificationRepository.save(Notification.builder()
                .title("Demande de Matériel Validée")
                .message("Votre demande de matériel N° " + saved.getNumeroDemande() + " a été VALIDÉE (" + decision.name() + "). Le matériel est réservé, préparez le bon de sortie (" + saved.getBonSortie().getNumeroBs() + ") pour le retrait physique.")
                .type("MATERIEL")
                .uniteCode(saved.getUnite().getCode())
                .referenceId(saved.getUnite().getId().toString())
                .build());

        return saved;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MÉTHODE 4 : escalerDemande
    // RÔLE : Escalader une demande vers le magasin SGS (ADMIN) quand le stock local est insuffisant.
    //        C'est le DA_MANAGER LOCAL qui décide d'escalader, pas le système automatiquement.
    //        Après escalade, seul l'ADMIN peut valider ou refuser le transfert inter-magasin.
    // PARAMÈTRES :
    //   demandeId     – ID de la demande à escalader
    //   motifEscalade – Explication du DA_MANAGER (ex: "Stock local insuffisant, besoin de 3 filtres")
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional
    public DemandeMateriel escalerDemande(Long demandeId, String motifEscalade) {
        DemandeMateriel demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable"));

        // Seule une demande SOUMIS ou EN_COURS_ARBITRAGE peut être escaladée
        if (demande.getStatut() != StatutDemande.SOUMIS
                && demande.getStatut() != StatutDemande.EN_COURS_ARBITRAGE) {
            throw new IllegalStateException("Seule une demande en cours d'arbitrage peut être escaladée.");
        }

        // ÉTAPE 1 : Changer le statut de la demande vers ESCALADE_SGS
        demande.setStatut(StatutDemande.ESCALADE_SGS);
        demande.setMotifEscalade(motifEscalade);
        DemandeMateriel saved = demandeRepository.save(demande);

        // ÉTAPE 2 : Notifier l'ADMIN (SGS) pour action urgente
        notificationRepository.save(Notification.builder()
                .title("⚠️ Escalade — Transfert Inter-Magasin Requis")
                .message("La demande " + saved.getNumeroDemande() + " de l'unité "
                        + saved.getUnite().getNom()
                        + " nécessite un transfert depuis SGS. Motif : " + motifEscalade
                        + ". Soute locale : " + (saved.getMagasinDesigne() != null ? saved.getMagasinDesigne().getNom() : "Inconnue") + ".")
                .type("ESCALADE")
                .uniteCode(null) // Visible par l'ADMIN uniquement
                .referenceId(saved.getId().toString())
                .build());

        return saved;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MÉTHODE 5 : validerTransfertInterMagasin (ADMIN uniquement)
    // RÔLE : L'ADMIN valide ou refuse le transfert de stock depuis SGS vers la soute locale.
    //        Si validé : les deux mouvements de stock (sortie SGS + entrée soute locale) sont enregistrés.
    //        La demande revient ensuite en statut APPROUVE_TOTAL.
    // PARAMÈTRES :
    //   demandeId   – ID de la demande ESCALADE_SGS
    //   accepter    – true pour valider le transfert, false pour refuser et signaler l'unité
    //   motifRefus  – Raison du refus si accepter=false
    //   userMatricule – Matricule de l'ADMIN qui valide
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional
    public DemandeMateriel validerTransfertInterMagasin(Long demandeId, boolean accepter, String motifRefus, String userMatricule) {
        DemandeMateriel demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable"));

        if (demande.getStatut() != StatutDemande.ESCALADE_SGS) {
            throw new IllegalStateException("Seule une demande en statut ESCALADE_SGS peut être validée ici.");
        }

        if (!accepter) {
            // ÉTAPE A : Refus de l'ADMIN — la demande est refusée avec motif
            demande.setStatut(StatutDemande.REFUSE);
            demande.setMotifRefus("[Transfert inter-magasin refusé par SGS] " + motifRefus);
            DemandeMateriel saved = demandeRepository.save(demande);

            notificationRepository.save(Notification.builder()
                    .title("Demande Refusée — Transfert Non Accordé")
                    .message("L'ADMIN SGS a refusé le transfert pour la demande "
                            + saved.getNumeroDemande() + ". Motif : " + motifRefus)
                    .type("MATERIEL")
                    .uniteCode(saved.getUnite().getCode())
                    .referenceId(saved.getUnite().getId().toString())
                    .build());

            return saved;
        }

        // ÉTAPE B : Validation ADMIN — effectuer le transfert de stock SGS → soute locale
        Magasin magasinLocal = demande.getMagasinDesigne();
        if (magasinLocal == null || SGS_ID.equals(magasinLocal.getId())) {
            throw new IllegalStateException("La demande n'a pas de soute locale valide pour le transfert.");
        }

        // Récupérer les lignes et effectuer les mouvements de transfert article par article
        List<LigneDemande> lignes = ligneRepository.findByDemande_Id(demandeId);
        String transferRef = "TRANSFERT-SGS-" + demande.getNumeroDemande();

        for (LigneDemande ligne : lignes) {
            BigDecimal accordee = ligne.getQuantiteAccordee() != null
                    ? ligne.getQuantiteAccordee()
                    : ligne.getQuantiteDemandee();

            // Le chemin d'escalade saute l'étape d'arbitrage : on matérialise ici la
            // quantité accordée (= demandée) pour que la suite du cycle (retrait physique,
            // bon de sortie imprimé, écran de détail) dispose d'une valeur cohérente.
            if (ligne.getQuantiteAccordee() == null) {
                ligne.setQuantiteAccordee(accordee);
                ligneRepository.save(ligne);
            }

            if (accordee.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Vérifier le stock disponible dans la soute locale
            BigDecimal stockLocal = stockRepository.findByItem_IdAndMagasin_Id(
                    ligne.getItem().getId(), magasinLocal.getId())
                    .map(Stock::getQuantite).orElse(BigDecimal.ZERO);

            BigDecimal deficit = accordee.subtract(stockLocal);
            if (deficit.compareTo(BigDecimal.ZERO) <= 0) continue; // Suffisant localement, pas de transfert nécessaire

            // Vérifier le stock SGS
            BigDecimal stockSGS = stockRepository.findByItem_IdAndMagasin_Id(
                    ligne.getItem().getId(), SGS_ID)
                    .map(Stock::getQuantite).orElse(BigDecimal.ZERO);

            if (stockSGS.compareTo(deficit) < 0) {
                throw new IllegalStateException(
                        "Stock SGS insuffisant pour l'article '" + ligne.getItem().getDesignation()
                        + "'. Requis: " + deficit + ", Disponible SGS: " + stockSGS);
            }

            // ÉTAPE B1 : Sortie depuis SGS
            stockService.effectuerMouvement(MouvementDto.builder()
                    .itemId(ligne.getItem().getId())
                    .magasinId(SGS_ID)
                    .quantite(deficit)
                    .typeMouvement(TypeMouvement.SORTIE)
                    .referenceBon(transferRef)
                    .motif("Transfert inter-magasin validé par ADMIN vers "
                            + magasinLocal.getNom() + " (" + demande.getNumeroDemande() + ")")
                    .build(), userMatricule);

            // ÉTAPE B2 : Entrée dans la soute locale
            stockService.effectuerMouvement(MouvementDto.builder()
                    .itemId(ligne.getItem().getId())
                    .magasinId(magasinLocal.getId())
                    .quantite(deficit)
                    .typeMouvement(TypeMouvement.ENTREE)
                    .referenceBon(transferRef)
                    .motif("Transfert reçu de SGS, validé par ADMIN (" + demande.getNumeroDemande() + ")")
                    .build(), userMatricule);
        }

        // ÉTAPE B3 : La demande est maintenant approuvée totalement
        demande.setStatut(StatutDemande.APPROUVE_TOTAL);
        // Générer le Bon de Sortie si pas encore créé
        if (demande.getBonSortie() == null) {
            BonSortie bs = BonSortie.builder()
                    .numeroBs("BS-" + demande.getNumeroDemande())
                    .dateSortie(LocalDate.now())
                    .transporteur("Service Transit DA")
                    .vehiculeMatricule("MDN-10452")
                    .statut("PREPARE")
                    .magasinId(magasinLocal.getId())
                    .build();
            demande.setBonSortie(bonSortieRepository.save(bs));
        }

        DemandeMateriel saved = demandeRepository.save(demande);

        // ÉTAPE B4 : Notifier le DA_MANAGER local et l'unité cliente
        notificationRepository.save(Notification.builder()
                .title("Transfert Inter-Magasin Validé")
                .message("L'ADMIN SGS a validé le transfert pour la demande "
                        + saved.getNumeroDemande() + ". Le matériel est maintenant disponible à la soute "
                        + magasinLocal.getNom() + ". Bon de sortie généré.")
                .type("MATERIEL")
                .uniteCode(saved.getUnite().getCode())
                .referenceId(saved.getUnite().getId().toString())
                .build());

        return saved;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MÉTHODE 6 : confirmerSortiePhysique
    // RÔLE : Confirmer que le matériel a été physiquement retiré depuis la soute désignée.
    //        → Décrémente le stock de la soute source (pas de synchronisation automatique à ce stade).
    //        → Le stock SGS a déjà été transféré localement via validerTransfertInterMagasin si nécessaire.
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional
    public BonSortie confirmerSortiePhysique(Long bonSortieId, String userMatricule, boolean derogationConfirmee, String adresseIp) {
        BonSortie bs = bonSortieRepository.findById(bonSortieId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de sortie introuvable"));

        if ("LIVRE".equals(bs.getStatut())) {
            throw new IllegalStateException("Ce bon de sortie a déjà été retiré physiquement.");
        }

        tn.defense.gamma3.auth.domain.User user = userRepository.findByMatricule(userMatricule)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

        boolean estAdmin = user.getRole() == tn.defense.gamma3.auth.domain.Role.ADMIN
                || user.getRole() == tn.defense.gamma3.auth.domain.Role.SUPER_ADMIN;
        boolean estDaManager = user.getRole() == tn.defense.gamma3.auth.domain.Role.DA_MANAGER;

        if (!estAdmin && !estDaManager) {
            throw new IllegalStateException("Seul un gestionnaire de soute (magasinier) ou l'administrateur est autorisé à confirmer la livraison physique des équipements.");
        }

        // Contrôle d'appartenance à la soute :
        //   - DA_MANAGER  → uniquement la soute à laquelle il est assigné ;
        //   - ADMIN       → uniquement la soute SGS (aucun DA_MANAGER n'y étant rattaché,
        //                   c'est l'ADMIN qui joue le rôle de magasinier SGS ; sans cette
        //                   règle, toute demande d'une unité routée vers SGS restait
        //                   bloquée en APPROUVE_TOTAL, incapable de passer LIVRE — audit 2026-09-09).
        if (estDaManager && (user.getMagasin() == null || !user.getMagasin().getId().equals(bs.getMagasinId()))) {
            throw new IllegalStateException("Vous n'êtes pas assigné à cette soute pour confirmer le retrait physique de ces équipements.");
        }
        if (estAdmin && !SGS_ID.equals(bs.getMagasinId())) {
            throw new IllegalStateException("La confirmation de retrait dans une soute DA locale revient à son magasinier (DA_MANAGER), pas à l'administrateur.");
        }

        DemandeMateriel demande = demandeRepository.findByBonSortieId(bonSortieId)
                .orElseThrow(() -> new IllegalArgumentException("Demande associée introuvable"));

        if (bs.getMagasinId() == null) {
            throw new IllegalArgumentException("La soute de prélèvement n'est pas spécifiée sur ce bon de sortie.");
        }

        List<LigneDemande> lignes = ligneRepository.findByDemande_Id(demande.getId());

        // ─── Seuil de sécurité (CCF RG16/RG17/RG68/RG102, audit de conformité 2026-09-11) ───
        // "Au seuil de sécurité, la distribution de l'article est bloquée. Elle n'est
        // autorisée que sur ordre de l'EMAM/BL." — équivalent numérique retenu : une
        // dérogation explicite, réservée à un ADMIN/SUPER_ADMIN (jamais un simple magasinier
        // DA_MANAGER, qui ne peut donc jamais s'auto-autoriser), tracée dans le Journal
        // d'Audit. Le seuil est comparé au stock TOTAL de l'article tous magasins confondus
        // (c'est la notion de "le stock" du CCF, écrit avant l'existence de plusieurs soutes
        // régionales dans Gamma3) — pas seulement celui de la soute de prélèvement.
        List<String> articlesSousSeuil = new ArrayList<>();
        for (LigneDemande ligne : lignes) {
            BigDecimal accordeeVerif = ligne.getQuantiteAccordee() != null ? ligne.getQuantiteAccordee() : ligne.getQuantiteDemandee();
            if (accordeeVerif == null || accordeeVerif.compareTo(BigDecimal.ZERO) <= 0) continue;

            Item article = ligne.getItem();
            BigDecimal seuil = article.getStockSecurite();
            if (seuil == null || seuil.compareTo(BigDecimal.ZERO) <= 0) continue; // Pas de seuil configuré pour cet article.

            BigDecimal stockTotalActuel = stockRepository.findByItem_Id(article.getId()).stream()
                    .map(Stock::getQuantite)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal stockApresSortie = stockTotalActuel.subtract(accordeeVerif);

            if (stockApresSortie.compareTo(seuil) < 0) {
                articlesSousSeuil.add(article.getNomenclature() + " (" + article.getDesignation() + ") : stock actuel "
                        + stockTotalActuel + ", seuil de sécurité " + seuil + ", après sortie " + stockApresSortie);
            }
        }

        if (!articlesSousSeuil.isEmpty()) {
            boolean estAdminPourDerogation = estAdmin; // ADMIN ou SUPER_ADMIN uniquement, jamais DA_MANAGER (voir estAdmin plus haut).
            if (!estAdminPourDerogation) {
                throw new SeuilSecuriteAtteintException(
                        "Sortie bloquée : le stock passerait sous le seuil de sécurité pour "
                                + articlesSousSeuil.size() + " article(s). Seul un administrateur (SGS ou système) peut autoriser une dérogation. Détail : "
                                + String.join(" | ", articlesSousSeuil));
            }
            if (!derogationConfirmee) {
                throw new SeuilSecuriteAtteintException(
                        "SEUIL_SECURITE:Le stock passerait sous le seuil de sécurité pour "
                                + articlesSousSeuil.size() + " article(s). Confirmez la dérogation pour poursuivre. Détail : "
                                + String.join(" | ", articlesSousSeuil));
            }
            // Dérogation confirmée par un ADMIN/SUPER_ADMIN : on poursuit, mais on trace
            // impérativement cette décision — c'est exactement le type d'action que le CCF
            // réserve à "l'ordre de l'EMAM/BL".
            journalAuditService.enregistrer(user.getMatricule(), user.getFullName(), TypeActionAudit.SEUIL_SECURITE_DEROGATION,
                    "Dérogation au seuil de sécurité pour le bon de sortie " + bs.getNumeroBs() + " : " + String.join(" | ", articlesSousSeuil),
                    adresseIp);
        }

        for (LigneDemande ligne : lignes) {
            // Quantité à sortir physiquement :
            //   - arbitrage classique  → quantiteAccordee a été fixée (éventuellement 0 = ligne refusée) ;
            //   - chemin d'escalade    → quantiteAccordee reste null (l'étape arbitrer est sautée),
            //                            on retombe alors sur la quantité demandée, comme le fait déjà
            //                            validerTransfertInterMagasin(). Sans ce repli, le retrait ne
            //                            décrémentait NI le stock NI la dotation réelle tout en passant
            //                            quand même la demande à LIVRE (bug data d'intégrité, audit 2026-09-09).
            BigDecimal accordee = ligne.getQuantiteAccordee() != null
                    ? ligne.getQuantiteAccordee()
                    : ligne.getQuantiteDemandee();
            if (accordee == null || accordee.compareTo(BigDecimal.ZERO) <= 0) continue;

            // ÉTAPE 1 : Convertir la dotation virtuelle en dotation réelle à bord
            planRepository.findByUniteIdAndItemId(demande.getUnite().getId(), ligne.getItem().getId())
                    .ifPresent(plan -> {
                        BigDecimal newVirtuel = plan.getQuantiteVirtuelle().subtract(accordee);
                        plan.setQuantiteVirtuelle(newVirtuel.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newVirtuel);
                        plan.setQuantiteReelle(plan.getQuantiteReelle().add(accordee));
                        planRepository.save(plan);
                    });

            // ÉTAPE 2 : Décrémenter le stock de la soute source (déjà approvisionnée si transfert validé)
            Long sortieMagasinId = bs.getMagasinId() != null
                    ? bs.getMagasinId()
                    : (demande.getMagasinDesigne() != null ? demande.getMagasinDesigne().getId() : SGS_ID);

            stockService.effectuerMouvement(MouvementDto.builder()
                    .itemId(ligne.getItem().getId())
                    .magasinId(sortieMagasinId)
                    .quantite(accordee)
                    .typeMouvement(TypeMouvement.SORTIE)
                    .referenceBon(bs.getNumeroBs())
                    .motif("Sortie physique et remise matériel (Demande: " + demande.getNumeroDemande() + ")")
                    .build(), userMatricule);
        }

        bs.setStatut("LIVRE");
        bs.setDateSortie(LocalDate.now());
        demande.setStatut(StatutDemande.LIVRE);
        demandeRepository.save(demande);
        BonSortie savedBs = bonSortieRepository.save(bs);

        notificationRepository.save(Notification.builder()
                .title("Matériel Retiré Physiquement")
                .message("Le matériel associé au bon de sortie " + bs.getNumeroBs()
                        + " a été retiré physiquement depuis la soute "
                        + bs.getMagasinId() + " et est désormais en service à bord.")
                .type("MATERIEL")
                .uniteCode(demande.getUnite().getCode())
                .referenceId(demande.getUnite().getId().toString())
                .build());

        return savedBs;
    }

    /**
     * Levée quand une sortie physique ferait passer le stock d'un article sous son seuil de
     * sécurité (CCF RG16/RG17/RG68/RG102). Le message commence par le préfixe machine-lisible
     * {@code "SEUIL_SECURITE:"} uniquement quand une dérogation ADMIN/SUPER_ADMIN reste
     * possible (pour que le frontend propose un bouton de confirmation) — son absence signale
     * un blocage définitif (ex : appelant DA_MANAGER, qui ne peut jamais s'auto-autoriser).
     */
    public static class SeuilSecuriteAtteintException extends RuntimeException {
        public SeuilSecuriteAtteintException(String message) { super(message); }
    }
}
