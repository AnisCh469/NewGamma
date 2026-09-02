package tn.defense.gamma3.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.stock.domain.Magasin;

import java.util.Optional;

public interface MagasinRepository extends JpaRepository<Magasin, Long> {
    Optional<Magasin> findByCode(String code);
}
