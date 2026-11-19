package tn.defense.gamma3.tiers.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.service.JournalAuditService;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.notification.service.BackofficeNotificationService;

import java.util.List;

/**
 * RÔLE : consultation et gestion des lignes du Plan d'Armement (dotation de chaque unité en
 * matériel, par article et quantités type/réelle/virtuelle).
 *
 * RÈGLE DE SÉCURITÉ STRICTE (2026-11-19) :
 * Seul le SUPER_ADMIN peut créer, modifier les valeurs de dotation ou supprimer une dotation.
 * L'administrateur logistique (SGS / ADMIN / DA_MANAGER) ne dispose que d'un droit de consultation (lecture seule).
 * Toute modification est consignée dans le Journal d'Audit et fait l'objet d'une alerte/email aux responsables backoffice.
 */
@RestController
@RequestMapping("/api/v1/plans-armement")
@RequiredArgsConstructor
public class PlanArmementController {

    private final PlanArmementRepository planArmementRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final ItemRepository itemRepository;
    private final JournalAuditService journalAuditService;
    private final BackofficeNotificationService backofficeNotificationService;

    @GetMapping
    public List<PlanArmement> getAllPlans() {
        return planArmementRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanArmement> getPlanById(@PathVariable Long id) {
        return planArmementRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/unite/{uniteId}")
    public List<PlanArmement> getPlansByUnite(@PathVariable Long uniteId) {
        return planArmementRepository.findByUniteId(uniteId);
    }

    @GetMapping("/unite-code/{uniteCode}")
    public List<PlanArmement> getPlansByUniteCode(@PathVariable String uniteCode) {
        return planArmementRepository.findByUniteCode(uniteCode);
    }

    @PostMapping
    public ResponseEntity<?> createPlan(@RequestBody PlanArmement plan, Authentication authentication, HttpServletRequest httpRequest) {
        User u = exigerSuperAdmin(authentication);
        if (u == null) return ResponseEntity.status(403).body("Seul le Super Administrateur est habilité à ajouter une dotation au Plan d'Armement.");

        if (plan.getUnite() == null || plan.getUnite().getId() == null ||
            plan.getItem() == null || plan.getItem().getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return uniteRepository.findById(plan.getUnite().getId())
                .flatMap(unite -> itemRepository.findById(plan.getItem().getId())
                        .map(item -> {
                            plan.setUnite(unite);
                            plan.setItem(item);
                            PlanArmement saved = planArmementRepository.save(plan);
                            String details = "Article " + item.getNomenclature() + " ajouté au plan de l'unité " + unite.getCode()
                                    + " (dotation type: " + saved.getQuantiteType() + ").";
                            journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.PLAN_ARMEMENT_CREE,
                                    details, httpRequest.getRemoteAddr());
                            backofficeNotificationService.notifierModificationBackoffice(
                                    "Nouvelle Dotation Réglementaire",
                                    details + " Effectué par " + u.getFullName(),
                                    saved.getId().toString()
                            );
                            return saved;
                        })
                )
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePlan(@PathVariable Long id, @RequestBody PlanArmement planDetails, Authentication authentication, HttpServletRequest httpRequest) {
        User u = exigerSuperAdmin(authentication);
        if (u == null) return ResponseEntity.status(403).body("Seul le Super Administrateur est habilité à modifier une dotation du Plan d'Armement.");

        return planArmementRepository.findById(id)
                .map(plan -> {
                    java.math.BigDecimal oldType = plan.getQuantiteType();
                    java.math.BigDecimal oldReel = plan.getQuantiteReelle();
                    plan.setQuantiteType(planDetails.getQuantiteType());
                    plan.setQuantiteReelle(planDetails.getQuantiteReelle());
                    plan.setQuantiteVirtuelle(planDetails.getQuantiteVirtuelle());
                    PlanArmement saved = planArmementRepository.save(plan);
                    String details = "Article " + plan.getItem().getNomenclature() + " (unité " + plan.getUnite().getCode()
                            + ") : dotation type " + oldType + "->" + saved.getQuantiteType() + ", réelle " + oldReel + "->" + saved.getQuantiteReelle()
                            + ", virtuelle=" + saved.getQuantiteVirtuelle() + ".";
                    journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.PLAN_ARMEMENT_MODIFIE,
                            details, httpRequest.getRemoteAddr());
                    backofficeNotificationService.notifierModificationBackoffice(
                            "Modification Valeurs Dotation",
                            details + " Effectué par " + u.getFullName(),
                            saved.getId().toString()
                    );
                    return (ResponseEntity<?>) ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlan(@PathVariable Long id, Authentication authentication, HttpServletRequest httpRequest) {
        User u = exigerSuperAdmin(authentication);
        if (u == null) return ResponseEntity.status(403).body("Seul le Super Administrateur est habilité à supprimer une dotation du Plan d'Armement.");

        return planArmementRepository.findById(id)
                .map(plan -> {
                    String details = "Article " + plan.getItem().getNomenclature() + " retiré du plan de l'unité " + plan.getUnite().getCode() + ".";
                    planArmementRepository.delete(plan);
                    journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.PLAN_ARMEMENT_SUPPRIME,
                            details, httpRequest.getRemoteAddr());
                    backofficeNotificationService.notifierModificationBackoffice(
                            "Suppression Dotation Réglementaire",
                            details + " Effectué par " + u.getFullName(),
                            id.toString()
                    );
                    return (ResponseEntity<?>) ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** @return l'utilisateur authentifié s'il est SUPER_ADMIN, sinon {@code null}. */
    private static User exigerSuperAdmin(Authentication authentication) {
        User u = (authentication != null && authentication.getPrincipal() instanceof User user) ? user : null;
        if (u == null) return null;
        if (u.getRole() != Role.SUPER_ADMIN) return null;
        return u;
    }
}
