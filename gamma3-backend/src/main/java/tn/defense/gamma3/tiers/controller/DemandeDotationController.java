package tn.defense.gamma3.tiers.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.tiers.domain.DemandeDotation;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.repository.DemandeDotationRepository;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.notification.domain.Notification;
import tn.defense.gamma3.notification.repository.NotificationRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/demandes-dotation")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class DemandeDotationController {

    private final DemandeDotationRepository demandeDotationRepository;
    private final PlanArmementRepository planArmementRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final ItemRepository itemRepository;
    private final NotificationRepository notificationRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<DemandeDotation> getAllDemandes() {
        return demandeDotationRepository.findAllWithRelations();
    }

    @GetMapping("/unite/{uniteId}")
    @Transactional(readOnly = true)
    public List<DemandeDotation> getDemandesByUnite(@PathVariable Long uniteId) {
        return demandeDotationRepository.findByUniteId(uniteId);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<DemandeDotation> createDemande(@RequestBody DemandeDotation demande) {
        if (demande.getUnite() == null || demande.getUnite().getId() == null ||
            demande.getItem() == null || demande.getItem().getId() == null ||
            demande.getQuantiteType() == null) {
            return ResponseEntity.badRequest().build();
        }

        return uniteRepository.findById(demande.getUnite().getId())
                .flatMap(unite -> itemRepository.findById(demande.getItem().getId())
                        .map(item -> {
                            demande.setUnite(unite);
                            demande.setItem(item);
                            demande.setStatut("SOUMISE");
                            DemandeDotation saved = demandeDotationRepository.save(demande);

                            // Trigger Notification for Admin/Operator
                            notificationRepository.save(Notification.builder()
                                    .title("Nouvelle Demande de Dotation")
                                    .message("L'unité " + saved.getUnite().getNom() + " a soumis une demande d'ajout pour l'article : " + saved.getItem().getDesignation() + " (Qté: " + saved.getQuantiteType() + ").")
                                    .type("DOTATION")
                                    .uniteCode(null)
                                    .referenceId(saved.getUnite().getId().toString())
                                    .build());

                            return saved;
                        })
                )
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/{id}/approuver")
    @Transactional
    public ResponseEntity<DemandeDotation> approuverDemande(@PathVariable Long id) {
        return demandeDotationRepository.findById(id)
                .map(demande -> {
                    if (!"SOUMISE".equals(demande.getStatut())) {
                        return ResponseEntity.badRequest().<DemandeDotation>build();
                    }
                    demande.setStatut("APPROUVEE");
                    DemandeDotation saved = demandeDotationRepository.save(demande);

                    // Créer ou mettre à jour le PlanArmement correspondant
                    PlanArmement plan = planArmementRepository.findByUniteIdAndItemId(demande.getUnite().getId(), demande.getItem().getId())
                            .orElseGet(() -> PlanArmement.builder()
                                    .unite(demande.getUnite())
                                    .item(demande.getItem())
                                    .quantiteType(java.math.BigDecimal.ZERO)
                                    .quantiteReelle(java.math.BigDecimal.ZERO)
                                    .quantiteVirtuelle(java.math.BigDecimal.ZERO)
                                    .build());
                    
                    plan.setQuantiteType(demande.getQuantiteType());
                    planArmementRepository.save(plan);

                    // Trigger Notification for Client Unit
                    notificationRepository.save(Notification.builder()
                            .title("Demande de Dotation Approuvée")
                            .message("Votre demande d'ajout pour l'article : " + saved.getItem().getDesignation() + " a été APPROUVÉE (Quantité Type : " + saved.getQuantiteType() + ").")
                            .type("DOTATION")
                            .uniteCode(saved.getUnite().getCode())
                            .referenceId(saved.getUnite().getId().toString())
                            .build());

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/refuser")
    @Transactional
    public ResponseEntity<DemandeDotation> refuserDemande(@PathVariable Long id, @RequestParam String motif) {
        return demandeDotationRepository.findById(id)
                .map(demande -> {
                    if (!"SOUMISE".equals(demande.getStatut())) {
                        return ResponseEntity.badRequest().<DemandeDotation>build();
                    }
                    demande.setStatut("REJETEE");
                    demande.setMotifRefus(motif);
                    DemandeDotation saved = demandeDotationRepository.save(demande);

                    // Trigger Notification for Client Unit
                    notificationRepository.save(Notification.builder()
                            .title("Demande de Dotation Rejetée")
                            .message("Votre demande d'ajout pour l'article : " + saved.getItem().getDesignation() + " a été REJETÉE. Motif : " + motif)
                            .type("DOTATION")
                            .uniteCode(saved.getUnite().getCode())
                            .referenceId(saved.getUnite().getId().toString())
                            .build());

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
