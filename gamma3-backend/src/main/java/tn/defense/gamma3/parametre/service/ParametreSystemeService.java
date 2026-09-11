package tn.defense.gamma3.parametre.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.parametre.domain.ParametreSysteme;
import tn.defense.gamma3.parametre.repository.ParametreSystemeRepository;

import java.time.LocalDateTime;

/**
 * RÔLE : gérer le délai d'inactivité avant déconnexion automatique — configurable par
 * l'administrateur (demande utilisateur du 2026-09-11, motif sécurité), avec une valeur par
 * défaut de 5 minutes tant qu'aucun administrateur ne l'a modifiée.
 *
 * IMPORTANT — portée réelle de ce réglage : il pilote la déconnexion cliente (minuteur
 * d'inactivité côté navigateur, voir InactivityService côté front) ET l'expiration effective
 * du jeton JWT n'est PAS raccourcie automatiquement en conséquence (le jeton reste valable
 * selon `application.security.jwt.expiration`, actuellement 24 h) — voir la limite documentée
 * dans docs/DEV_JOURNAL.md : sans jeton à courte durée de vie ou liste de révocation côté
 * serveur, un jeton déjà émis reste techniquement utilisable jusqu'à sa propre expiration même
 * après une "déconnexion" côté navigateur.
 */
@Service
@RequiredArgsConstructor
public class ParametreSystemeService {

    private final ParametreSystemeRepository repository;

    public static final String CLE_INACTIVITE_MINUTES = "session.inactivite.minutes";
    private static final int DEFAUT_MINUTES = 5;
    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 480; // 8 h

    /** Nombre de sauvegardes conservées avant que les plus anciennes soient purgées
     *  automatiquement (voir module {@code sauvegarde}, Phase 2 de la gouvernance des rôles). */
    public static final String CLE_RETENTION_SAUVEGARDES = "sauvegarde.retention.nombre";
    private static final int DEFAUT_RETENTION = 30;
    private static final int MIN_RETENTION = 1;
    private static final int MAX_RETENTION = 365;

    @Transactional(readOnly = true)
    public int getInactiviteMinutes() {
        return repository.findByCle(CLE_INACTIVITE_MINUTES)
                .map(p -> {
                    try {
                        return Integer.parseInt(p.getValeur());
                    } catch (NumberFormatException e) {
                        return DEFAUT_MINUTES;
                    }
                })
                .orElse(DEFAUT_MINUTES);
    }

    @Transactional
    public int setInactiviteMinutes(int minutes, User admin) {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException(
                    "Le délai d'inactivité doit être compris entre " + MIN_MINUTES + " et " + MAX_MINUTES + " minutes.");
        }
        ParametreSysteme p = repository.findByCle(CLE_INACTIVITE_MINUTES)
                .orElseGet(() -> ParametreSysteme.builder()
                        .cle(CLE_INACTIVITE_MINUTES)
                        .description("Délai d'inactivité (minutes) avant déconnexion automatique de l'utilisateur.")
                        .build());
        p.setValeur(String.valueOf(minutes));
        p.setModifiePar(admin);
        p.setModifieLe(LocalDateTime.now());
        repository.save(p);
        return minutes;
    }

    @Transactional(readOnly = true)
    public int getRetentionSauvegardes() {
        return repository.findByCle(CLE_RETENTION_SAUVEGARDES)
                .map(p -> {
                    try {
                        return Integer.parseInt(p.getValeur());
                    } catch (NumberFormatException e) {
                        return DEFAUT_RETENTION;
                    }
                })
                .orElse(DEFAUT_RETENTION);
    }

    @Transactional
    public int setRetentionSauvegardes(int nombre, User admin) {
        if (nombre < MIN_RETENTION || nombre > MAX_RETENTION) {
            throw new IllegalArgumentException(
                    "Le nombre de sauvegardes conservées doit être compris entre " + MIN_RETENTION + " et " + MAX_RETENTION + ".");
        }
        ParametreSysteme p = repository.findByCle(CLE_RETENTION_SAUVEGARDES)
                .orElseGet(() -> ParametreSysteme.builder()
                        .cle(CLE_RETENTION_SAUVEGARDES)
                        .description("Nombre de sauvegardes de la base à conserver avant purge automatique des plus anciennes.")
                        .build());
        p.setValeur(String.valueOf(nombre));
        p.setModifiePar(admin);
        p.setModifieLe(LocalDateTime.now());
        repository.save(p);
        return nombre;
    }
}
