package tn.defense.gamma3.distribution.api;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.distribution.domain.DemandeMateriel;
import tn.defense.gamma3.distribution.domain.LigneDemande;
import tn.defense.gamma3.distribution.domain.StatutDemande;
import tn.defense.gamma3.distribution.repository.DemandeMaterielRepository;
import tn.defense.gamma3.distribution.repository.LigneDemandeRepository;
import tn.defense.gamma3.distribution.service.DistributionService;
import tn.defense.gamma3.stock.domain.Stock;
import tn.defense.gamma3.stock.domain.MouvementStock;
import tn.defense.gamma3.stock.domain.TypeMouvement;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.stock.repository.MouvementStockRepository;
import tn.defense.gamma3.reception.domain.LigneReception;
import tn.defense.gamma3.reception.domain.StatutBPR;
import tn.defense.gamma3.reception.repository.LigneReceptionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/demandes")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class DemandeController {

    private final DistributionService distributionService;
    private final DemandeMaterielRepository demandeRepository;
    private final LigneDemandeRepository ligneRepository;
    private final StockRepository stockRepository;
    private final MouvementStockRepository mouvementRepository;
    private final LigneReceptionRepository ligneReceptionRepository;

    public record LigneDemandeDetailDto(
        Long id,
        tn.defense.gamma3.catalogue.domain.Item item,
        BigDecimal quantiteDemandee,
        BigDecimal quantiteAccordee,
        BigDecimal stockSecurite,
        List<MagasinStockDto> stocksParMagasin,
        BigDecimal consommationMoyenne,
        List<PrevisionDto> previsions
    ) {}

    public record MagasinStockDto(
        Long magasinId,
        String code,
        String nom,
        BigDecimal quantite
    ) {}

    public record PrevisionDto(
        String reference,
        BigDecimal quantite,
        String datePrevue
    ) {}

    public record DemandeRequestDto(DemandeMateriel demande, List<LigneDemande> lignes) {}

    public record ArbitrageRequestDto(StatutDemande decision, String motifRefus, List<LigneDemande> lignes, Long magasinId) {}

    /** DTO pour l'escalade d'une demande vers SGS */
    public record EscaladeRequestDto(String motifEscalade) {}

    /** DTO pour la validation (ou refus) d'un transfert inter-magasin par l'ADMIN */
    public record TransfertValidationDto(boolean accepter, String motifRefus) {}

    public record ReservationDto(
        String numeroBs,
        String numeroDemande,
        String codeUnite,
        String nomUnite,
        BigDecimal quantite,
        String dateReservation
    ) {}

    // ─── GET /demandes ───────────────────────────────────────────────────────────
    // RÔLE : Retourner les demandes en fonction du rôle de l'utilisateur connecté.
    //   - ADMIN       → Voit TOUTES les demandes (tous magasins confondus)
    //   - DA_MANAGER  → Voit UNIQUEMENT les demandes assignées à son magasin
    //   - UNIT_USER   → Accès refusé (utiliser /demandes/unite/{id})
    @GetMapping
    public List<DemandeMateriel> getAllDemandes(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return demandeRepository.findAllWithUnite();
        }
        // DA_MANAGER filtré par son magasin assigné
        if (user.getRole() == Role.DA_MANAGER && user.getMagasin() != null) {
            return demandeRepository.findByMagasinDesigne_Id(user.getMagasin().getId());
        }
        // ADMIN : toutes les demandes
        return demandeRepository.findAllWithUnite();
    }

    // ─── GET /demandes/escalades ─────────────────────────────────────────────────
    // RÔLE : Retourner les demandes en attente de validation de transfert inter-magasin (ESCALADE_SGS).
    //        Réservé à l'ADMIN pour traitement prioritaire.
    @GetMapping("/escalades")
    public List<DemandeMateriel> getEscalades() {
        return demandeRepository.findByStatut(StatutDemande.ESCALADE_SGS);
    }

    @GetMapping("/unite/{uniteId}")
    public List<DemandeMateriel> getDemandesByUnite(@PathVariable Long uniteId) {
        return demandeRepository.findByUniteId(uniteId);
    }

    @GetMapping("/{id}/lignes")
    public List<LigneDemande> getLignesByDemandeId(@PathVariable Long id) {
        return ligneRepository.findByDemande_Id(id);
    }

    @GetMapping("/item/{itemId}/reservations")
    @Transactional(readOnly = true)
    public List<ReservationDto> getItemReservations(@PathVariable java.util.UUID itemId) {
        List<LigneDemande> lignes = ligneRepository.findActiveReservationsByItemId(itemId);
        return lignes.stream()
            .map(l -> new ReservationDto(
                l.getDemande().getBonSortie() != null ? l.getDemande().getBonSortie().getNumeroBs() : "En attente",
                l.getDemande().getNumeroDemande(),
                l.getDemande().getUnite().getCode(),
                l.getDemande().getUnite().getNom(),
                l.getQuantiteAccordee() != null ? l.getQuantiteAccordee() : java.math.BigDecimal.ZERO,
                l.getDemande().getBonSortie() != null && l.getDemande().getBonSortie().getDateSortie() != null ? l.getDemande().getBonSortie().getDateSortie().toString() : l.getDemande().getDateDemande().toString()
            ))
            .collect(Collectors.toList());
    }

    @GetMapping("/{id}/lignes-detaillees")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getLignesDetaillees(@PathVariable Long id) {
        List<LigneDemande> originalLignes = ligneRepository.findByDemande_Id(id);
        
        List<LigneDemandeDetailDto> detailedList = originalLignes.stream()
            .map(l -> {
                java.util.UUID itemId = l.getItem().getId();
                
                // 1. Charger les stocks actuels par magasin
                List<Stock> stocks = stockRepository.findByItem_Id(itemId);
                List<MagasinStockDto> stocksParMagasin = stocks.stream()
                    .map(s -> new MagasinStockDto(s.getMagasin().getId(), s.getMagasin().getCode(), s.getMagasin().getNom(), s.getQuantite()))
                    .collect(Collectors.toList());
                
                // 2. Calculer la consommation mensuelle moyenne (Sorties des 12 derniers mois / 12)
                List<MouvementStock> mvts = mouvementRepository.findByItem_IdOrderByDateMouvementDesc(itemId);
                LocalDateTime limitDate = LocalDateTime.now().minusMonths(12);
                BigDecimal totalSorties = mvts.stream()
                    .filter(m -> m.getTypeMouvement() == TypeMouvement.SORTIE && m.getDateMouvement().isAfter(limitDate))
                    .map(MouvementStock::getQuantite)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                BigDecimal monthlyAvg = totalSorties.divide(new BigDecimal("12"), 2, java.math.RoundingMode.HALF_UP);
                if (monthlyAvg.compareTo(BigDecimal.ZERO) == 0 && l.getItem().getStockSecurite() != null) {
                    // Fallback réaliste basé sur le stock de sécurité
                    monthlyAvg = l.getItem().getStockSecurite().multiply(new BigDecimal("0.15")).setScale(0, java.math.RoundingMode.HALF_UP);
                }
                if (monthlyAvg.compareTo(BigDecimal.ZERO) == 0) {
                    monthlyAvg = new BigDecimal("5"); // Fallback absolu
                }
                
                // 3. Charger les prévisions de stock à venir (BPR en attente PV ou VALIDE)
                List<LigneReception> arrivals = ligneReceptionRepository.findByItem_Id(itemId);
                List<PrevisionDto> previsions = arrivals.stream()
                    .filter(a -> a.getBpr().getStatut() == StatutBPR.ATTENTE_PV || a.getBpr().getStatut() == StatutBPR.VALIDE)
                    .map(a -> new PrevisionDto(a.getBpr().getNumeroBpr(), a.getQuantiteLivree(), a.getBpr().getDateReception().toString()))
                    .collect(Collectors.toList());
                
                return new LigneDemandeDetailDto(
                    l.getId(),
                    l.getItem(),
                    l.getQuantiteDemandee(),
                    l.getQuantiteAccordee(),
                    l.getItem().getStockSecurite(),
                    stocksParMagasin,
                    monthlyAvg,
                    previsions
                );
            })
            .collect(Collectors.toList());
            
        return ResponseEntity.ok(detailedList);
    }

    @PostMapping
    public ResponseEntity<?> creerDemande(@RequestBody DemandeRequestDto requestDto) {
        try {
            DemandeMateriel saved = distributionService.creerDemande(requestDto.demande(), requestDto.lignes());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/arbitrer")
    public ResponseEntity<?> arbitrerDemande(
            @PathVariable Long id,
            @RequestBody ArbitrageRequestDto requestDto,
            Authentication authentication) {
        try {
            String matricule = "admin";
            if (authentication != null && authentication.getPrincipal() instanceof User user) {
                matricule = user.getMatricule();
            }
            DemandeMateriel arbitree = distributionService.arbitrerDemande(
                    id,
                    requestDto.decision(),
                    requestDto.motifRefus(),
                    requestDto.lignes(),
                    requestDto.magasinId(),
                    matricule
            );
            return ResponseEntity.ok(arbitree);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── POST /demandes/{id}/escalader ───────────────────────────────────────────
    // RÔLE : Le DA_MANAGER LOCAL escalade la demande vers l'ADMIN (SGS)
    //        quand son stock local est insuffisant.
    //        Après escalade, seul l'ADMIN peut valider le transfert.
    @PostMapping("/{id}/escalader")
    public ResponseEntity<?> escalerDemande(
            @PathVariable Long id,
            @RequestBody EscaladeRequestDto requestDto,
            Authentication authentication) {
        try {
            DemandeMateriel escalee = distributionService.escalerDemande(id, requestDto.motifEscalade());
            return ResponseEntity.ok(escalee);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── POST /demandes/{id}/valider-transfert ───────────────────────────────────
    // RÔLE : L'ADMIN (SGS) valide ou refuse le transfert inter-magasin demandé.
    //        Si validé : stock SGS débité, stock local crédité, bon de sortie généré.
    //        Si refusé : demande passe à REFUSE avec motif.
    @PostMapping("/{id}/valider-transfert")
    public ResponseEntity<?> validerTransfert(
            @PathVariable Long id,
            @RequestBody TransfertValidationDto requestDto,
            Authentication authentication) {
        try {
            String matricule = "admin";
            if (authentication != null && authentication.getPrincipal() instanceof User user) {
                if (user.getRole() != Role.ADMIN) {
                    return ResponseEntity.status(403).body("Accès refusé : seul l'ADMIN peut valider un transfert inter-magasin.");
                }
                matricule = user.getMatricule();
            }
            DemandeMateriel result = distributionService.validerTransfertInterMagasin(
                    id, requestDto.accepter(), requestDto.motifRefus(), matricule);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/bons-sortie/{id}/confirmer-livraison")
    public ResponseEntity<?> confirmerLivraisonPhysique(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            String matricule = "admin";
            if (authentication != null && authentication.getPrincipal() instanceof User user) {
                matricule = user.getMatricule();
            }
            tn.defense.gamma3.distribution.domain.BonSortie bs = distributionService.confirmerSortiePhysique(id, matricule);
            return ResponseEntity.ok(bs);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
