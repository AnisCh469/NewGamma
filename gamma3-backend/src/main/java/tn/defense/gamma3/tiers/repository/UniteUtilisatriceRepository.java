package tn.defense.gamma3.tiers.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import java.util.Optional;

public interface UniteUtilisatriceRepository extends JpaRepository<UniteUtilisatrice, Long> {
    Optional<UniteUtilisatrice> findByCode(String code);
}
