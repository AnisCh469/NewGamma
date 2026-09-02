package tn.defense.gamma3.distribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.distribution.domain.LigneDemande;
import java.util.List;

@Repository
public interface LigneDemandeRepository extends JpaRepository<LigneDemande, Long> {
    List<LigneDemande> findByDemande_Id(Long demandeId);

    @Query("SELECT ld FROM LigneDemande ld " +
           "JOIN FETCH ld.demande d " +
           "JOIN FETCH d.unite u " +
           "JOIN FETCH d.bonSortie bs " +
           "WHERE ld.item.id = :itemId " +
           "AND d.statut IN (tn.defense.gamma3.distribution.domain.StatutDemande.APPROUVE_TOTAL, tn.defense.gamma3.distribution.domain.StatutDemande.APPROUVE_PARTIEL) " +
           "AND bs.statut = 'PREPARE'")
    List<LigneDemande> findActiveReservationsByItemId(@Param("itemId") java.util.UUID itemId);

    @Query("SELECT ld.item.id, SUM(COALESCE(ld.quantiteAccordee, 0)) FROM LigneDemande ld " +
           "JOIN ld.demande d " +
           "JOIN d.bonSortie bs " +
           "WHERE ld.item.id IN :itemIds " +
           "AND d.statut IN (tn.defense.gamma3.distribution.domain.StatutDemande.APPROUVE_TOTAL, tn.defense.gamma3.distribution.domain.StatutDemande.APPROUVE_PARTIEL) " +
           "AND bs.statut = 'PREPARE' " +
           "GROUP BY ld.item.id")
    List<Object[]> getReservedQuantitiesByItemIds(@Param("itemIds") List<java.util.UUID> itemIds);
}

