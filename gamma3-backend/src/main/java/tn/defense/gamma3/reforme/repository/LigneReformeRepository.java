package tn.defense.gamma3.reforme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.reforme.domain.LigneReforme;

import java.util.List;

@Repository
public interface LigneReformeRepository extends JpaRepository<LigneReforme, Long> {

    @Query("SELECT l FROM LigneReforme l JOIN FETCH l.item WHERE l.dossierReforme.id = :dossierId")
    List<LigneReforme> findByDossierReformeId(@Param("dossierId") Long dossierId);
}
