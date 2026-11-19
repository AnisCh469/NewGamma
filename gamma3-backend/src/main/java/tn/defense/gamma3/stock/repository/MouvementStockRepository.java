package tn.defense.gamma3.stock.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.defense.gamma3.stock.domain.MouvementStock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long> {
    List<MouvementStock> findByItem_IdOrderByDateMouvementDesc(UUID itemId);

    @Query("SELECT m FROM MouvementStock m JOIN FETCH m.item JOIN FETCH m.magasin " +
           "WHERE m.dateMouvement BETWEEN :start AND :end ORDER BY m.dateMouvement DESC")
    List<MouvementStock> findByDateMouvementBetweenOrderByDateMouvementDesc(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Somme des sorties par article depuis une date donnée — alimente le calcul de la
     *  consommation moyenne annuelle (Cmoy3, CCF RG5 : Procédure P01, Prévisions). */
    @Query("SELECT m.item.id, SUM(m.quantite) FROM MouvementStock m " +
           "WHERE m.typeMouvement = tn.defense.gamma3.stock.domain.TypeMouvement.SORTIE " +
           "AND m.dateMouvement >= :depuis GROUP BY m.item.id")
    List<Object[]> sommeSortiesParItemDepuis(@Param("depuis") LocalDateTime depuis);

    /** Volumes mensuels de consommation (sorties) pour une année cible — Dashboard Analytics */
    @Query("SELECT MONTH(m.dateMouvement), COALESCE(SUM(m.quantite), 0) " +
           "FROM MouvementStock m " +
           "WHERE m.typeMouvement = tn.defense.gamma3.stock.domain.TypeMouvement.SORTIE " +
           "AND YEAR(m.dateMouvement) = :year " +
           "GROUP BY MONTH(m.dateMouvement)")
    List<Object[]> getMonthlyOutputVolumes(@Param("year") int year);

    /** Top articles consommés (sorties) ordonnés par volume décroissant — Dashboard Analytics */
    @Query("SELECT m.item.nomenclature, m.item.designation, COALESCE(SUM(m.quantite), 0) as totalVol " +
           "FROM MouvementStock m " +
           "WHERE m.typeMouvement = tn.defense.gamma3.stock.domain.TypeMouvement.SORTIE " +
           "GROUP BY m.item.nomenclature, m.item.designation " +
           "ORDER BY totalVol DESC")
    List<Object[]> getTopConsumedItems(Pageable pageable);
}
