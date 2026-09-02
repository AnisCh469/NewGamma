package tn.defense.gamma3.tiers.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.tiers.domain.CommandeFournisseur;
import java.util.List;
import java.util.Optional;

public interface CommandeFournisseurRepository extends JpaRepository<CommandeFournisseur, Long> {
    Optional<CommandeFournisseur> findByNumeroCommande(String numeroCommande);
    List<CommandeFournisseur> findByFournisseurId(Long fournisseurId);
    List<CommandeFournisseur> findByMarcheId(Long marcheId);
}
