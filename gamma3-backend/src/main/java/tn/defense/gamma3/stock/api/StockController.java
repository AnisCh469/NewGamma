package tn.defense.gamma3.stock.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.stock.api.dto.MouvementDto;
import tn.defense.gamma3.stock.api.dto.StockDto;
import tn.defense.gamma3.stock.service.StockService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/item/{itemId}")
    public ResponseEntity<List<StockDto>> getStockByItemId(@PathVariable java.util.UUID itemId) {
        return ResponseEntity.ok(stockService.getStockByItemId(itemId));
    }

    /** Historique des mouvements de stock d'un article (réceptions, sorties, transferts, réformes). */
    @GetMapping("/item/{itemId}/historique")
    public ResponseEntity<List<tn.defense.gamma3.stock.api.dto.MouvementHistoriqueDto>> getHistoriqueByItemId(
            @PathVariable java.util.UUID itemId) {
        return ResponseEntity.ok(stockService.getHistoriqueByItemId(itemId));
    }

    @PostMapping("/mouvement")
    public ResponseEntity<Void> effectuerMouvement(
            @RequestBody MouvementDto dto,
            Authentication authentication) {
        // authentication.getName() retourne le matricule
        stockService.effectuerMouvement(dto, authentication.getName());
        return ResponseEntity.ok().build();
    }
}
