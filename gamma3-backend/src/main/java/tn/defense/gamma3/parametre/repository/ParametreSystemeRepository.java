package tn.defense.gamma3.parametre.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.parametre.domain.ParametreSysteme;

import java.util.Optional;

public interface ParametreSystemeRepository extends JpaRepository<ParametreSysteme, Long> {
    Optional<ParametreSysteme> findByCle(String cle);
}
