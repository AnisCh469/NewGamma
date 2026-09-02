package tn.defense.gamma3.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.auth.domain.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMatricule(String matricule);
}
