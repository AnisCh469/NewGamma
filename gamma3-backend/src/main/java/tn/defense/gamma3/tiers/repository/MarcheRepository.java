package tn.defense.gamma3.tiers.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.tiers.domain.Marche;
import java.util.List;
import java.util.Optional;

public interface MarcheRepository extends JpaRepository<Marche, Long> {
    Optional<Marche> findByNumeroMarche(String numeroMarche);
    List<Marche> findByFournisseurId(Long fournisseurId);
}
