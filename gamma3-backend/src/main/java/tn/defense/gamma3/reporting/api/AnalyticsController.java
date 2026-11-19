package tn.defense.gamma3.reporting.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.distribution.domain.StatutDemande;
import tn.defense.gamma3.distribution.repository.DemandeMaterielRepository;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.stock.repository.MouvementStockRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CONTRÔLEUR : Tableaux de Bord & Analytics
 *
 * OPTIMISATIONS MAJEURES (2026-11-19) :
 * Remplacement intégral des stockRepository.findAll() et mouvementRepository.findAll() (qui causaient
 * le chargement en mémoire de dizaines de milliers d'entités avec N+1 requêtes Hibernate bloquant le pool HikariCP)
 * par des requêtes d'agrégation SQL / JPQL natives ultrarapides (exécutées en quelques millisecondes).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ItemRepository itemRepository;
    private final StockRepository stockRepository;
    private final MouvementStockRepository mouvementRepository;
    private final DemandeMaterielRepository demandeRepository;

    public record KpisDto(long totalItems, BigDecimal totalValue, long alertItems, long pendingDemandes) {}

    public record MagasinStatDto(Long magasinId, String code, String nom, BigDecimal totalVolume, BigDecimal totalValue) {}

    public record MensuelleStatDto(int month, BigDecimal volume) {}

    public record ItemStatDto(String nomenclature, String designation, BigDecimal volumeTotal) {}

    @GetMapping("/kpis")
    public ResponseEntity<KpisDto> getKpis() {
        long totalItems = itemRepository.count();
        BigDecimal totalValue = stockRepository.calculateTotalStockValue();
        long alertItems = stockRepository.countAlertStockItems();
        long pendingDemandes = demandeRepository.countByStatut(StatutDemande.SOUMIS);

        return ResponseEntity.ok(new KpisDto(totalItems, totalValue, alertItems, pendingDemandes));
    }

    @GetMapping("/stock-by-magasin")
    public ResponseEntity<List<MagasinStatDto>> getStockByMagasin() {
        List<Object[]> rows = stockRepository.getStockStatsByMagasin();
        List<MagasinStatDto> stats = new ArrayList<>();
        for (Object[] r : rows) {
            Long id = ((Number) r[0]).longValue();
            String code = (String) r[1];
            String nom = (String) r[2];
            BigDecimal volume = (r[3] instanceof BigDecimal b) ? b : BigDecimal.valueOf(((Number) r[3]).doubleValue());
            BigDecimal value = (r[4] instanceof BigDecimal b) ? b : BigDecimal.valueOf(((Number) r[4]).doubleValue());
            stats.add(new MagasinStatDto(id, code, nom, volume, value));
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/consommation-mensuelle")
    public ResponseEntity<List<MensuelleStatDto>> getConsommationMensuelle(@RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : LocalDateTime.now().getYear();
        List<Object[]> rows = mouvementRepository.getMonthlyOutputVolumes(targetYear);

        Map<Integer, BigDecimal> monthlySum = new HashMap<>();
        for (Object[] r : rows) {
            int month = ((Number) r[0]).intValue();
            BigDecimal vol = (r[1] instanceof BigDecimal b) ? b : BigDecimal.valueOf(((Number) r[1]).doubleValue());
            monthlySum.put(month, vol);
        }

        List<MensuelleStatDto> stats = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            stats.add(new MensuelleStatDto(i, monthlySum.getOrDefault(i, BigDecimal.ZERO)));
        }

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/top-items")
    public ResponseEntity<List<ItemStatDto>> getTopItems() {
        List<Object[]> rows = mouvementRepository.getTopConsumedItems(PageRequest.of(0, 5));
        List<ItemStatDto> stats = rows.stream().map(r -> new ItemStatDto(
                (String) r[0],
                (String) r[1],
                (r[2] instanceof BigDecimal b) ? b : BigDecimal.valueOf(((Number) r[2]).doubleValue())
        )).collect(Collectors.toList());

        return ResponseEntity.ok(stats);
    }
}
