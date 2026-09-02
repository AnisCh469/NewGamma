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
}

