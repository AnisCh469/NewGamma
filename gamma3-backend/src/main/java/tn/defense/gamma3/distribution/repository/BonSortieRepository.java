package tn.defense.gamma3.distribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.distribution.domain.BonSortie;
import java.util.Optional;

@Repository
public interface BonSortieRepository extends JpaRepository<BonSortie, Long> {
    Optional<BonSortie> findByNumeroBs(String numeroBs);
}
