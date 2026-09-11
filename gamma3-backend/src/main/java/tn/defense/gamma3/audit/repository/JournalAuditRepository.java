package tn.defense.gamma3.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.audit.domain.JournalAudit;
import tn.defense.gamma3.audit.domain.TypeActionAudit;

public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long> {
    Page<JournalAudit> findAllByOrderByDateEvenementDesc(Pageable pageable);
    Page<JournalAudit> findByActeurMatriculeIgnoreCaseOrderByDateEvenementDesc(String matricule, Pageable pageable);
    Page<JournalAudit> findByActionOrderByDateEvenementDesc(TypeActionAudit action, Pageable pageable);
    Page<JournalAudit> findByActeurMatriculeIgnoreCaseAndActionOrderByDateEvenementDesc(
            String matricule, TypeActionAudit action, Pageable pageable);
}
