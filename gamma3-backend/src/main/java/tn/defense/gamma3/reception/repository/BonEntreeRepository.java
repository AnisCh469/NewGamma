package tn.defense.gamma3.reception.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.reception.domain.BonEntree;

import java.util.Optional;

@Repository
public interface BonEntreeRepository extends JpaRepository<BonEntree, Long> {
    Optional<BonEntree> findByPvCommission_Id(Long pvId);
}
