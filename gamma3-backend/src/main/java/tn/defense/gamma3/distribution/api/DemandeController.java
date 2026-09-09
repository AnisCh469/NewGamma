package tn.defense.gamma3.distribution.api;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.domain.FonctionUnite;
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
public class DemandeController {

    private final DistributionService distributionService;
    private final DemandeMaterielRepository demandeRepository;
    private final LigneDemandeRepository ligneRepository;
    private final StockRepository stockRepository;
    private final MouvementStockRepository mouvementRepository;
    private final LigneReceptionRepository ligneReceptionRepository;

    // ─── Helpers d'autorisation ──────────────────────────────────────────────────
    // Contexte : contrairement à creerDemande()/validerCommandant() (sécurisés au
    // 2026-09-04), les endpoints d'arbitrage ci-dessous ne vérifiaient RIEN — un
    // Gestionnaire d'unité pouvait auto-approuver sa propre demande, et un DA_MANAGER
    // pouvait arbitrer/escalader les demandes d'un autre magasin (audit 2026-09-09).
    // Ces helpers centralisent les contrôles, dans le même style « instanceof User »
    // que le reste du contrôleur (pas de @PreAuthorize dans ce projet).

    /** Renvoie l'utilisateur authentifié, ou null si la requête n'est pas authentifiée. */
    private static User currentUser(Authentication authentication) {
        return (authentication != null && authentication.getPrincipal() instanceof User user) ? user : null;
    }

    /**
     * Vérifie qu'un utilisateur a le droit de TRAITER (arbitrer / escalader) une demande :
     *   - ADMIN       → toute demande, tout magasin (il supervise SGS et l'ensemble du réseau) ;
     *   - DA_MANAGER  → uniquement les demandes routées vers SON magasin assigné ;
     *   - autre rôle  → interdit.
     * @return null si l'accès est autorisé, sinon la ResponseEntity d'erreur (401/403) à renvoyer.
     */
    private ResponseEntity<?> verifierAccesTraitement(Authentication authentication, DemandeMateriel demande) {
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body("Authentification requise.");
        }
        if (user.getRole() == Role.ADMIN) {
            return null;
        }
        if (user.getRole() != Role.DA_MANAGER) {
            return ResponseEntity.status(403).body("Seul un gestionnaire DA (DA_MANAGER) ou l'administrateur peut traiter une demande de matériel.");
        }
        if (user.getMagasin() == null) {
            return ResponseEntity.status(403).body("Votre compte DA_MANAGER n'est rattaché à aucun magasin.");
        }
        if (demande.getMagasinDesigne() == null
                || !user.getMagasin().getId().equals(demande.getMagasinDesigne().getId())) {
            return ResponseEntity.status(403).body("Cette demande est routée vers un autre magasin que le vôtre — vous ne pouvez pas la traiter.");
        }
        return null;
    }

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

    /** DTO pour la validation (ou refus) d'une demande par le Commandant de l'unité */
    public record ValidationCommandantDto(boolean accepter, String motifRefus) {}

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
    public ResponseEntity<?> getAllDemandes(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body("Authentification requise.");
        }
        // SÉCURITÉ (audit 2026-09-09) : cette vue est la vue GAMMA3 (DA/ADMIN). Un compte
        // UNIT_USER (Gestionnaire ou Commandant) ne doit JAMAIS y accéder — auparavant il
        // recevait ici la liste des demandes de TOUTES les unités de la Marine (fuite de
        // confidentialité). Il dispose de /demandes/unite/{id} pour sa propre unité.
        if (user.getRole() == Role.UNIT_USER) {
            return ResponseEntity.status(403).body("Accès refusé : utilisez /demandes/unite/{id} pour consulter les demandes de votre unité.");
        }

        List<DemandeMateriel> result;
        if (user.getRole() == Role.DA_MANAGER && user.getMagasin() != null) {
            // DA_MANAGER filtré par son magasin assigné
            result = demandeRepository.findByMagasinDesigne_Id(user.getMagasin().getId());
        } else {
            // ADMIN : toutes les demandes
            result = demandeRepository.findAllWithUnite();
        }
        // DOUBLE VÉRIFICATION (2026-09-04) : GAMMA3 (DA_MANAGER/ADMIN) ne doit jamais voir une
        // demande tant qu'elle n'a pas été validée par le Commandant de l'unité, ni celles que
        // le Commandant a refusées avant envoi. En pratique EN_ATTENTE_VALIDATION_COMMANDANT
        // n'a pas de magasinDesigne (donc déjà exclue du filtre DA_MANAGER ci-dessus), mais la
        // vue ADMIN ("toutes les demandes") les inclurait sans ce filtre explicite.
        return ResponseEntity.ok(result.stream()
                .filter(d -> d.getStatut() != StatutDemande.EN_ATTENTE_VALIDATION_COMMANDANT
                        && d.getStatut() != StatutDemande.REFUSE_COMMANDANT)
                .collect(Collectors.toList()));
    }

    // ─── GET /demandes/escalades ─────────────────────────────────────────────────
    // RÔLE : Retourner les demandes en attente de validation de transfert inter-magasin (ESCALADE_SGS).
    //        Réservé à l'ADMIN pour traitement prioritaire.
    @GetMapping("/escalades")
    public ResponseEntity<?> getEscalades(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body("Authentification requise.");
        }
        // SÉCURITÉ (audit 2026-09-09) : la validation d'un transfert inter-magasin est un
        // pouvoir exclusivement ADMIN (SGS). La file d'attente correspondante ne doit donc
        // être lisible que par un ADMIN — pas par un DA_MANAGER, encore moins un UNIT_USER.
        if (user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body("Accès refusé : seul l'administrateur (SGS) traite les escalades inter-magasin.");
        }
        return ResponseEntity.ok(demandeRepository.findByStatut(StatutDemande.ESCALADE_SGS));
    }

    @GetMapping("/unite/{uniteId}")
    public ResponseEntity<?> getDemandesByUnite(@PathVariable Long uniteId, Authentication authentication) {
        // Un compte UNIT_USER (Gestionnaire ou Commandant) ne peut consulter QUE les demandes
        // de sa PROPRE unité — sans ce contrôle, n'importe quel compte pouvait lire l'historique
        // de n'importe quelle autre unité en devinant son identifiant.
        if (authentication != null && authentication.getPrincipal() instanceof User user
                && user.getRole() == Role.UNIT_USER) {
            if (user.getUnite() == null || !user.getUnite().getId().equals(uniteId)) {
                return ResponseEntity.status(403).body("Accès refusé : vous ne pouvez consulter que les demandes de votre propre unité.");
            }
        }
        return ResponseEntity.ok(demandeRepository.findByUniteId(uniteId));
    }

    @GetMapping("/{id}/lignes")
    public ResponseEntity<?> getLignesByDemandeId(@PathVariable Long id, Authentication authentication) {
        // SÉCURITÉ (audit 2026-09-09) : un compte UNIT_USER ne peut lire le détail des lignes
        // que pour les demandes de SA propre unité (l'écran "détail unité" ne demande jamais
        // rien d'autre). DA_MANAGER et ADMIN : accès complet (vue GAMMA3).
        User user = currentUser(authentication);
        if (user != null && user.getRole() == Role.UNIT_USER) {
            DemandeMateriel demande = demandeRepository.findById(id).orElse(null);
            if (demande == null) {
                return ResponseEntity.notFound().build();
            }
            if (user.getUnite() == null || demande.getUnite() == null
                    || !user.getUnite().getId().equals(demande.getUnite().getId())) {
                return ResponseEntity.status(403).body("Accès refusé : cette demande n'appartient pas à votre unité.");
            }
        }
        return ResponseEntity.ok(ligneRepository.findByDemande_Id(id));
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
    public ResponseEntity<?> getLignesDetaillees(@PathVariable Long id, Authentication authentication) {
        // SÉCURITÉ (audit 2026-09-09) : cette vue expose l'état des stocks par magasin, la
        // consommation moyenne et les prévisions — données d'arbitrage GAMMA3. Réservée
        // donc à DA_MANAGER / ADMIN ; un UNIT_USER n'y a pas accès.
        User user = currentUser(authentication);
        if (user == null || user.getRole() == Role.UNIT_USER) {
            return ResponseEntity.status(403).body("Accès refusé : vue réservée à l'arbitrage DA.");
        }
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
    public ResponseEntity<?> creerDemande(@RequestBody DemandeRequestDto requestDto, Authentication authentication) {
        // SÉCURITÉ + RÈGLE MÉTIER (2026-09-04) : seul le Gestionnaire d'une unité peut soumettre
        // une demande, jamais le Commandant (qui ne fait que valider), et jamais pour une unité
        // qui n'est pas la sienne — l'unité est donc dérivée du compte authentifié, PAS du corps
        // de la requête (qui restait auparavant non authentifiée et acceptait n'importe quel
        // identifiant d'unité fourni par le client).
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).body("Authentification requise.");
        }
        if (user.getRole() != Role.UNIT_USER || user.getFonctionUnite() != FonctionUnite.GESTIONNAIRE) {
            return ResponseEntity.status(403).body("Seul le Gestionnaire d'une unité peut soumettre une demande de matériel.");
        }
        if (user.getUnite() == null) {
            return ResponseEntity.badRequest().body("Votre compte n'est rattaché à aucune unité. Contactez un administrateur.");
        }
        requestDto.demande().setUnite(user.getUnite());
        try {
            DemandeMateriel saved = distributionService.creerDemande(requestDto.demande(), requestDto.lignes());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── POST /demandes/{id}/valider-commandant ──────────────────────────────────
    // RÔLE : Le Commandant (chef d'unité) valide ou refuse une demande soumise par le
    //        Gestionnaire de SA MÊME unité — la deuxième des deux vérifications requises
    //        avant qu'une demande n'atteigne GAMMA3 (voir StatutDemande, DistributionService).
    @PostMapping("/{id}/valider-commandant")
    public ResponseEntity<?> validerCommandant(
            @PathVariable Long id,
            @RequestBody ValidationCommandantDto requestDto,
            Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).body("Authentification requise.");
        }
        if (user.getRole() != Role.UNIT_USER || user.getFonctionUnite() != FonctionUnite.COMMANDANT) {
            return ResponseEntity.status(403).body("Seul le Commandant d'une unité peut valider ou refuser ses demandes.");
        }
        if (user.getUnite() == null) {
            return ResponseEntity.badRequest().body("Votre compte n'est rattaché à aucune unité. Contactez un administrateur.");
        }
        DemandeMateriel demande = demandeRepository.findById(id).orElse(null);
        if (demande == null) {
            return ResponseEntity.notFound().build();
        }
        if (!user.getUnite().getId().equals(demande.getUnite().getId())) {
            return ResponseEntity.status(403).body("Vous ne pouvez valider que les demandes de votre propre unité.");
        }
        try {
            DemandeMateriel resultat = distributionService.validerParCommandant(
                    id, requestDto.accepter(), requestDto.motifRefus(), user.getMatricule());
            return ResponseEntity.ok(resultat);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── POST /demandes/{id}/arbitrer ────────────────────────────────────────────
    // RÔLE : Le DA_MANAGER du magasin désigné (ou l'ADMIN) approuve/refuse la demande.
    // SÉCURITÉ (audit 2026-09-09) : auparavant AUCUN contrôle — un Gestionnaire d'unité
    //   pouvait auto-approuver sa demande, un DA_MANAGER pouvait arbitrer le magasin
    //   d'un collègue, et le `magasinId` du corps JSON réécrivait librement le routage.
    @PostMapping("/{id}/arbitrer")
    public ResponseEntity<?> arbitrerDemande(
            @PathVariable Long id,
            @RequestBody ArbitrageRequestDto requestDto,
            Authentication authentication) {
        DemandeMateriel demande = demandeRepository.findById(id).orElse(null);
        if (demande == null) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<?> refus = verifierAccesTraitement(authentication, demande);
        if (refus != null) return refus;

        User user = currentUser(authentication);
        // Le magasin de prélèvement n'est PAS pris depuis le corps de la requête (un
        // DA_MANAGER ne peut prélever que dans SON magasin ; l'ADMIN prélève dans le
        // magasin déjà désigné, faute d'indication contraire explicite et légitime).
        Long magasinPrelevement = (user.getRole() == Role.DA_MANAGER)
                ? user.getMagasin().getId()
                : (demande.getMagasinDesigne() != null ? demande.getMagasinDesigne().getId() : requestDto.magasinId());
        try {
            DemandeMateriel arbitree = distributionService.arbitrerDemande(
                    id,
                    requestDto.decision(),
                    requestDto.motifRefus(),
                    requestDto.lignes(),
                    magasinPrelevement,
                    user.getMatricule()
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
    // SÉCURITÉ (audit 2026-09-09) : mêmes contrôles que l'arbitrage — seul le DA_MANAGER
    //        du magasin routé (ou l'ADMIN) peut escalader cette demande.
    @PostMapping("/{id}/escalader")
    public ResponseEntity<?> escalerDemande(
            @PathVariable Long id,
            @RequestBody EscaladeRequestDto requestDto,
            Authentication authentication) {
        DemandeMateriel demande = demandeRepository.findById(id).orElse(null);
        if (demande == null) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<?> refus = verifierAccesTraitement(authentication, demande);
        if (refus != null) return refus;
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
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body("Authentification requise.");
        }
        if (user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body("Accès refusé : seul l'ADMIN (SGS) peut valider un transfert inter-magasin.");
        }
        try {
            DemandeMateriel result = distributionService.validerTransfertInterMagasin(
                    id, requestDto.accepter(), requestDto.motifRefus(), user.getMatricule());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/bons-sortie/{id}/confirmer-livraison")
    public ResponseEntity<?> confirmerLivraisonPhysique(
            @PathVariable Long id,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body("Authentification requise.");
        }
        try {
            tn.defense.gamma3.distribution.domain.BonSortie bs = distributionService.confirmerSortiePhysique(id, user.getMatricule());
            return ResponseEntity.ok(bs);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
