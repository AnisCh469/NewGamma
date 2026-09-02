package tn.defense.gamma3.reception.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.reception.domain.BonProvisoireReception;

@Repository
public interface BonProvisoireReceptionRepository extends JpaRepository<BonProvisoireReception, Long> {
}
