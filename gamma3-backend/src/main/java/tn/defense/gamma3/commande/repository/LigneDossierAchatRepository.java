package tn.defense.gamma3.commande.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.commande.domain.LigneDossierAchat;

import java.util.List;
import java.util.UUID;

@Repository
public interface LigneDossierAchatRepository extends JpaRepository<LigneDossierAchat, UUID> {
    List<LigneDossierAchat> findByDossierAchatId(UUID dossierAchatId);
}
