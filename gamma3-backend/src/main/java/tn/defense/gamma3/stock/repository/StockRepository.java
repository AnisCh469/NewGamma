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
}
