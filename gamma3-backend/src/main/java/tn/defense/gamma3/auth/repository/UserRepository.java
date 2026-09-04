package tn.defense.gamma3.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.auth.domain.FonctionUnite;
import tn.defense.gamma3.auth.domain.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMatricule(String matricule);

    /** Tous les comptes rattachés à une unité (Gestionnaire + Commandant, actifs ou bloqués). */
    List<User> findByUnite_Id(Long uniteId);

    /** Le compte occupant une fonction donnée (Gestionnaire OU Commandant) pour une unité,
     *  s'il existe — utilisé pour router les notifications et vérifier l'unicité à la création. */
    Optional<User> findByUnite_IdAndFonctionUnite(Long uniteId, FonctionUnite fonctionUnite);
}
