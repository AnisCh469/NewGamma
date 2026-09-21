package tn.defense.gamma3.collationnement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.collationnement.domain.CampagneCollationnement;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampagneCollationnementRepository extends JpaRepository<CampagneCollationnement, Long> {

    @Query("SELECT DISTINCT c FROM CampagneCollationnement c JOIN FETCH c.unite ORDER BY c.createdAt DESC")
    List<CampagneCollationnement> findAllWithRelations();

    @Query("SELECT c FROM CampagneCollationnement c JOIN FETCH c.unite WHERE c.id = :id")
    Optional<CampagneCollationnement> findByIdWithRelations(@Param("id") Long id);
}
