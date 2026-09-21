package tn.defense.gamma3.collationnement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.collationnement.domain.LigneCollationnement;

import java.util.List;

@Repository
public interface LigneCollationnementRepository extends JpaRepository<LigneCollationnement, Long> {

    @Query("SELECT l FROM LigneCollationnement l JOIN FETCH l.item WHERE l.campagne.id = :campagneId ORDER BY l.item.nomenclature")
    List<LigneCollationnement> findByCampagneId(@Param("campagneId") Long campagneId);
}
