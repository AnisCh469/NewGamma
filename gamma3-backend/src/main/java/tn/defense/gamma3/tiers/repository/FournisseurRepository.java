package tn.defense.gamma3.tiers.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.tiers.domain.Fournisseur;
import java.util.Optional;

public interface FournisseurRepository extends JpaRepository<Fournisseur, Long> {
    Optional<Fournisseur> findByCode(String code);
}
