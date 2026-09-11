package tn.defense.gamma3.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.audit.domain.JournalAudit;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.repository.JournalAuditRepository;

/**
 * Point d'entrée unique pour écrire dans le journal d'audit. Toute intervention sensible
 * (connexion, déconnexion, modification d'un compte, changement d'un paramètre système…)
 * doit passer par {@link #enregistrer}.
 *
 * RÈGLE IMPORTANTE : une écriture d'audit ne doit JAMAIS faire échouer l'action métier qui
 * l'a déclenchée. Si le journal est indisponible, on se contente d'un avertissement dans les
 * logs applicatifs — la connexion, la création de compte, etc. doivent continuer à fonctionner.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JournalAuditService {

    private final JournalAuditRepository repository;

    public void enregistrer(String matricule, String nom, TypeActionAudit action, String details, String adresseIp) {
        try {
            repository.save(JournalAudit.builder()
                    .acteurMatricule(matricule != null && !matricule.isBlank() ? matricule : "?")
                    .acteurNom(nom)
                    .action(action)
                    .details(details)
                    .adresseIp(adresseIp)
                    .build());
        } catch (Exception e) {
            log.warn("Échec d'écriture du journal d'audit ({} / {}) : {}", matricule, action, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<JournalAudit> lister(String matricule, TypeActionAudit action, Pageable pageable) {
        boolean hasMatricule = matricule != null && !matricule.isBlank();
        if (hasMatricule && action != null) {
            return repository.findByActeurMatriculeIgnoreCaseAndActionOrderByDateEvenementDesc(matricule, action, pageable);
        }
        if (hasMatricule) {
            return repository.findByActeurMatriculeIgnoreCaseOrderByDateEvenementDesc(matricule, pageable);
        }
        if (action != null) {
            return repository.findByActionOrderByDateEvenementDesc(action, pageable);
        }
        return repository.findAllByOrderByDateEvenementDesc(pageable);
    }
}
