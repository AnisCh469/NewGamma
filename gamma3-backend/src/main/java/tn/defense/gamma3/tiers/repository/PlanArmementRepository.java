package tn.defense.gamma3.tiers.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanArmementRepository extends JpaRepository<PlanArmement, Long> {
    @Query("SELECT p FROM PlanArmement p JOIN FETCH p.item JOIN FETCH p.unite WHERE p.unite.id = :uniteId")
    List<PlanArmement> findByUniteId(@Param("uniteId") Long uniteId);

    @Query("SELECT p FROM PlanArmement p JOIN FETCH p.item JOIN FETCH p.unite WHERE p.unite.code = :uniteCode")
    List<PlanArmement> findByUniteCode(@Param("uniteCode") String uniteCode);

    Optional<PlanArmement> findByUniteIdAndItemId(Long uniteId, UUID itemId);
    Optional<PlanArmement> findByUniteCodeAndItemId(String uniteCode, UUID itemId);

    @Query("SELECT p.unite.id, COUNT(p) FROM PlanArmement p GROUP BY p.unite.id")
    List<Object[]> countPlansGroupByUniteId();

    /** Somme de la dotation réellement en service (tous unités confondues) par article — c'est
     *  le "parc" (matériel en service) de la CCF RG6, utilisé pour le seuil d'alerte (Sa) d'un
     *  article non consommable (armement/dotation). */
    @Query("SELECT p.item.id, SUM(p.quantiteReelle) FROM PlanArmement p GROUP BY p.item.id")
    List<Object[]> sommeQuantiteReelleParItem();

    @Query("SELECT p FROM PlanArmement p JOIN FETCH p.item JOIN FETCH p.unite")
    List<PlanArmement> findAllWithItemAndUnite();
}

