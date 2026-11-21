package tn.defense.gamma3.prevision.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.prevision.domain.DecisionCommission;
import tn.defense.gamma3.prevision.domain.LigneExpressionBesoin;

import java.util.List;
import java.util.UUID;

@Repository
public interface LigneExpressionBesoinRepository extends JpaRepository<LigneExpressionBesoin, UUID> {

    List<LigneExpressionBesoin> findByExpressionBesoinId(UUID expressionBesoinId);

    @Query("SELECT l FROM LigneExpressionBesoin l WHERE l.expressionBesoin.anneeBudgetaire = :annee AND l.decisionCommission = :decision AND l.transfereAchat = false")
    List<LigneExpressionBesoin> findPretesPourAchat(@Param("annee") Integer annee, @Param("decision") DecisionCommission decision);
}
