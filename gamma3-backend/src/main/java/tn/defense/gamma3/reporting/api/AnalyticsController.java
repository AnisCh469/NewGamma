package tn.defense.gamma3.reporting.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.distribution.domain.StatutDemande;
import tn.defense.gamma3.distribution.repository.DemandeMaterielRepository;
import tn.defense.gamma3.stock.domain.Stock;
import tn.defense.gamma3.stock.domain.MouvementStock;
import tn.defense.gamma3.stock.domain.TypeMouvement;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.stock.repository.MouvementStockRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
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

        List<Stock> stocks = stockRepository.findAll();
        BigDecimal totalValue = stocks.stream()
                .map(s -> s.getQuantite().multiply(s.getItem().getPrixUnitaire()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long alertItems = stocks.stream()
                .filter(s -> s.getQuantite().compareTo(s.getItem().getStockSecurite()) < 0)
                .count();

        long pendingDemandes = demandeRepository.findAll().stream()
                .filter(d -> d.getStatut() == StatutDemande.SOUMIS)
                .count();

        return ResponseEntity.ok(new KpisDto(totalItems, totalValue, alertItems, pendingDemandes));
    }

    @GetMapping("/stock-by-magasin")
    public ResponseEntity<List<MagasinStatDto>> getStockByMagasin() {
        List<Stock> stocks = stockRepository.findAll();

        Map<Long, List<Stock>> grouped = stocks.stream()
                .collect(Collectors.groupingBy(s -> s.getMagasin().getId()));

        List<MagasinStatDto> stats = new ArrayList<>();
        for (Map.Entry<Long, List<Stock>> entry : grouped.entrySet()) {
            List<Stock> list = entry.getValue();
            if (list.isEmpty()) continue;
            
            String code = list.get(0).getMagasin().getCode();
            String nom = list.get(0).getMagasin().getNom();
            
            BigDecimal volume = list.stream()
                    .map(Stock::getQuantite)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
            BigDecimal value = list.stream()
                    .map(s -> s.getQuantite().multiply(s.getItem().getPrixUnitaire()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            stats.add(new MagasinStatDto(entry.getKey(), code, nom, volume, value));
        }

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/consommation-mensuelle")
    public ResponseEntity<List<MensuelleStatDto>> getConsommationMensuelle(@RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : LocalDateTime.now().getYear();
        List<MouvementStock> mvts = mouvementRepository.findAll();

        List<MouvementStock> outputs = mvts.stream()
                .filter(m -> m.getTypeMouvement() == TypeMouvement.SORTIE && m.getDateMouvement().getYear() == targetYear)
                .collect(Collectors.toList());

        Map<Integer, BigDecimal> monthlySum = outputs.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getDateMouvement().getMonthValue(),
                        Collectors.reducing(BigDecimal.ZERO, MouvementStock::getQuantite, BigDecimal::add)
                ));

        List<MensuelleStatDto> stats = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            stats.add(new MensuelleStatDto(i, monthlySum.getOrDefault(i, BigDecimal.ZERO)));
        }

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/top-items")
    public ResponseEntity<List<ItemStatDto>> getTopItems() {
        List<MouvementStock> mvts = mouvementRepository.findAll();

        // Sorties physiques
        List<MouvementStock> outputs = mvts.stream()
                .filter(m -> m.getTypeMouvement() == TypeMouvement.SORTIE)
                .collect(Collectors.toList());

        Map<String, List<MouvementStock>> grouped = outputs.stream()
                .collect(Collectors.groupingBy(m -> m.getItem().getNomenclature()));

        List<ItemStatDto> stats = new ArrayList<>();
        for (Map.Entry<String, List<MouvementStock>> entry : grouped.entrySet()) {
            List<MouvementStock> list = entry.getValue();
            String designation = list.get(0).getItem().getDesignation();
            BigDecimal totalVol = list.stream()
                    .map(MouvementStock::getQuantite)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            stats.add(new ItemStatDto(entry.getKey(), designation, totalVol));
        }

        stats.sort((a, b) -> b.volumeTotal().compareTo(a.volumeTotal()));

        return ResponseEntity.ok(stats.stream().limit(5).collect(Collectors.toList()));
    }
}
