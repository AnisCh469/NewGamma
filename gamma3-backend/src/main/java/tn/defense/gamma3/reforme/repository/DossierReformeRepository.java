package tn.defense.gamma3.reforme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.reforme.domain.DossierReforme;

import java.util.List;
import java.util.Optional;

@Repository
public interface DossierReformeRepository extends JpaRepository<DossierReforme, Long> {

    @Query("SELECT DISTINCT d FROM DossierReforme d JOIN FETCH d.unite ORDER BY d.createdAt DESC")
    List<DossierReforme> findAllWithRelations();

    @Query("SELECT d FROM DossierReforme d JOIN FETCH d.unite WHERE d.unite.id = :uniteId ORDER BY d.createdAt DESC")
    List<DossierReforme> findByUniteId(@Param("uniteId") Long uniteId);

    @Query("SELECT d FROM DossierReforme d JOIN FETCH d.unite WHERE d.id = :id")
    Optional<DossierReforme> findByIdWithRelations(@Param("id") Long id);
}
