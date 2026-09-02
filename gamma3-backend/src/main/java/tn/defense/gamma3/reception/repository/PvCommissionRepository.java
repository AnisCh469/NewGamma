package tn.defense.gamma3.reception.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.reception.domain.PvCommission;

import java.util.Optional;

@Repository
public interface PvCommissionRepository extends JpaRepository<PvCommission, Long> {
    Optional<PvCommission> findByBpr_Id(Long bprId);
}
