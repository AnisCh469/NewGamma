package tn.defense.gamma3.prevision.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.prevision.domain.ExpressionBesoin;
import tn.defense.gamma3.prevision.domain.StatutBesoin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpressionBesoinRepository extends JpaRepository<ExpressionBesoin, UUID> {

    List<ExpressionBesoin> findByUniteIdOrderByDateCreationDesc(Long uniteId);

    List<ExpressionBesoin> findByAnneeBudgetaireOrderByDateCreationDesc(Integer annee);

    @Query("SELECT eb FROM ExpressionBesoin eb WHERE (:annee IS NULL OR eb.anneeBudgetaire = :annee) AND (:statut IS NULL OR eb.statut = :statut) ORDER BY eb.dateCreation DESC")
    List<ExpressionBesoin> findByFiltres(@Param("annee") Integer annee, @Param("statut") StatutBesoin statut);

    Optional<ExpressionBesoin> findByNumeroDossier(String numeroDossier);

    long countByAnneeBudgetaire(Integer annee);
}
