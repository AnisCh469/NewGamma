package tn.defense.gamma3.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.stock.domain.MouvementStock;

import java.util.List;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long> {
    List<MouvementStock> findByItem_IdOrderByDateMouvementDesc(java.util.UUID itemId);
    List<MouvementStock> findByDateMouvementBetweenOrderByDateMouvementDesc(java.time.LocalDateTime start, java.time.LocalDateTime end);

    /** Somme des sorties par article depuis une date donnée — alimente le calcul de la
     *  consommation moyenne annuelle (Cmoy3, CCF RG5 : Procédure P01, Prévisions). */
    @org.springframework.data.jpa.repository.Query(
        "SELECT m.item.id, SUM(m.quantite) FROM MouvementStock m " +
        "WHERE m.typeMouvement = tn.defense.gamma3.stock.domain.TypeMouvement.SORTIE " +
        "AND m.dateMouvement >= :depuis GROUP BY m.item.id")
    List<Object[]> sommeSortiesParItemDepuis(@org.springframework.data.repository.query.Param("depuis") java.time.LocalDateTime depuis);
}
