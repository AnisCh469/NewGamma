package tn.defense.gamma3.tiers.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.tiers.domain.DemandeDotation;
import java.util.List;

@Repository
public interface DemandeDotationRepository extends JpaRepository<DemandeDotation, Long> {
    @Query("SELECT d FROM DemandeDotation d JOIN FETCH d.item JOIN FETCH d.unite WHERE d.unite.id = :uniteId ORDER BY d.createdAt DESC")
    List<DemandeDotation> findByUniteId(@Param("uniteId") Long uniteId);
    
    @Query("SELECT d FROM DemandeDotation d JOIN FETCH d.item JOIN FETCH d.unite ORDER BY d.createdAt DESC")
    List<DemandeDotation> findAllWithRelations();
}
