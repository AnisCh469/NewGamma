package tn.defense.gamma3.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.stock.domain.MouvementStock;

import java.util.List;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long> {
    List<MouvementStock> findByItem_IdOrderByDateMouvementDesc(java.util.UUID itemId);
    List<MouvementStock> findByDateMouvementBetweenOrderByDateMouvementDesc(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
