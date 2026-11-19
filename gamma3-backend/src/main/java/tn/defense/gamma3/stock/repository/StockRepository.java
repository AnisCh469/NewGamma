package tn.defense.gamma3.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.defense.gamma3.stock.domain.Stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByItem_IdAndMagasin_Id(java.util.UUID itemId, Long magasinId);

    List<Stock> findByItem_Id(java.util.UUID itemId);

    long countByMagasin_Id(Long magasinId);

    @Query("SELECT s.item.id, SUM(s.quantite) FROM Stock s WHERE s.item.id IN :itemIds GROUP BY s.item.id")
    List<Object[]> getTotalQuantitiesByItemIds(@Param("itemIds") List<UUID> itemIds);

    @Query("SELECT s.item.id, SUM(s.quantite) FROM Stock s WHERE s.item.id IN :itemIds AND s.magasin.id = :magasinId GROUP BY s.item.id")
    List<Object[]> getQuantitiesByItemIdsAndMagasinId(@Param("itemIds") List<UUID> itemIds, @Param("magasinId") Long magasinId);

    /** Stock total (tous magasins confondus), pour TOUS les articles ayant au moins une ligne
     *  de stock — utilisé par la Procédure P01 (Prévisions) pour borner le calcul aux articles
     *  réellement en circulation, plutôt que de parcourir tout le catalogue (63 000+ articles,
     *  la plupart jamais mouvementés depuis la migration Gamma2). */
    @Query("SELECT s.item.id, SUM(s.quantite) FROM Stock s GROUP BY s.item.id")
    List<Object[]> getAllTotalQuantitiesByItem();

    /** Valeur totale valorisée du stock pour le Dashboard (calcul direct côté base) */
    @Query("SELECT COALESCE(SUM(s.quantite * s.item.prixUnitaire), 0) FROM Stock s")
    BigDecimal calculateTotalStockValue();

    /** Nombre d'articles sous le seuil d'alerte pour le Dashboard */
    @Query("SELECT COUNT(s) FROM Stock s WHERE s.quantite < s.item.stockSecurite")
    long countAlertStockItems();

    /** Agrégation par magasin pour le Dashboard Analytics */
    @Query("SELECT s.magasin.id, s.magasin.code, s.magasin.nom, COALESCE(SUM(s.quantite), 0), COALESCE(SUM(s.quantite * s.item.prixUnitaire), 0) " +
           "FROM Stock s GROUP BY s.magasin.id, s.magasin.code, s.magasin.nom")
    List<Object[]> getStockStatsByMagasin();

    /** Chargement complet avec Item et Magasin pour l'export d'inventaire sans requêtes N+1 */
    @Query("SELECT s FROM Stock s JOIN FETCH s.item JOIN FETCH s.magasin")
    List<Stock> findAllWithItemAndMagasin();
}
