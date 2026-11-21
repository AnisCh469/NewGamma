package tn.defense.gamma3.commande.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.commande.domain.DossierAchat;
import tn.defense.gamma3.commande.domain.StatutAchat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DossierAchatRepository extends JpaRepository<DossierAchat, UUID> {

    @Query("SELECT da FROM DossierAchat da WHERE (:annee IS NULL OR da.annee = :annee) AND (:statut IS NULL OR da.statut = :statut) ORDER BY da.dateCreation DESC")
    List<DossierAchat> findByFiltres(@Param("annee") Integer annee, @Param("statut") StatutAchat statut);

    Optional<DossierAchat> findByNumeroDossier(String numeroDossier);

    long countByAnnee(Integer annee);
}
