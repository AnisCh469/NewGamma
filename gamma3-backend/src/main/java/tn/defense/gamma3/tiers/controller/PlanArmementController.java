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

import java.util.List;

/**
 * RÔLE : consultation et gestion des lignes du Plan d'Armement (dotation de chaque unité en
 * matériel, par article et quantités type/réelle/virtuelle).
 *
 * CORRECTIF SÉCURITÉ CRITIQUE (2026-09-11, découvert lors de l'audit de conformité au CCF —
 * voir docs/AUDIT_CONFORMITE_CCF_2026-09-11.md §2.1) : ce contrôleur n'avait auparavant AUCUN
 * contrôle de rôle sur {@code POST}/{@code PUT}/{@code DELETE} — tout compte authentifié, y
 * compris un simple {@code UNIT_USER}, pouvait créer, modifier ou SUPPRIMER n'importe quelle
 * ligne de Plan d'Armement de n'importe quelle unité. Le CCF (Procédure P07 Bis, RG91-RG93)
 * prévoit un vrai circuit de validation (demande visée par le chef de corps, commission de
 * traitement) — cette correction n'implémente pas ce circuit complet (jugé hors périmètre
 * immédiat, voir la proposition à venir pour les Procédures P01/P07), mais restaure au minimum
 * le contrôle d'accès attendu par le principe Zero-Trust du projet : seuls les rôles qui
 * gèrent déjà la logistique d'une unité (SUPER_ADMIN, ADMIN, DA_MANAGER — cohérent avec
 * {@code ReceptionController.exigerRoleDA} et les autres contrôleurs de ce périmètre) peuvent
 * écrire. La lecture reste ouverte à tout authentifié : un {@code UNIT_USER} doit pouvoir
 * consulter le plan de sa propre unité (écran "Mon Plan &amp; Suivi", demande de dotation).
 *
 * Chaque écriture est désormais journalisée dans le Journal d'Audit (traçabilité — demande
 * explicite de l'utilisateur lors de la gouvernance des rôles, 2026-09-11).
 */
@RestController
@RequestMapping("/api/v1/plans-armement")
@RequiredArgsConstructor
public class PlanArmementController {

    private final PlanArmementRepository planArmementRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final ItemRepository itemRepository;
    private final JournalAuditService journalAuditService;

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
        User u = exigerRoleLogistique(authentication);
        if (u == null) return ResponseEntity.status(403).body("La gestion du Plan d'Armement est réservée à la Division de l'Approvisionnement.");

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
                            journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.PLAN_ARMEMENT_CREE,
                                    "Article " + item.getNomenclature() + " ajouté au plan de l'unité " + unite.getCode()
                                            + " (dotation " + saved.getQuantiteType() + ").",
                                    httpRequest.getRemoteAddr());
                            return saved;
                        })
                )
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePlan(@PathVariable Long id, @RequestBody PlanArmement planDetails, Authentication authentication, HttpServletRequest httpRequest) {
        User u = exigerRoleLogistique(authentication);
        if (u == null) return ResponseEntity.status(403).body("La gestion du Plan d'Armement est réservée à la Division de l'Approvisionnement.");

        return planArmementRepository.findById(id)
                .map(plan -> {
                    plan.setQuantiteType(planDetails.getQuantiteType());
                    plan.setQuantiteReelle(planDetails.getQuantiteReelle());
                    plan.setQuantiteVirtuelle(planDetails.getQuantiteVirtuelle());
                    PlanArmement saved = planArmementRepository.save(plan);
                    journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.PLAN_ARMEMENT_MODIFIE,
                            "Article " + plan.getItem().getNomenclature() + " (unité " + plan.getUnite().getCode()
                                    + ") : dotation type=" + saved.getQuantiteType() + ", réelle=" + saved.getQuantiteReelle()
                                    + ", virtuelle=" + saved.getQuantiteVirtuelle() + ".",
                            httpRequest.getRemoteAddr());
                    return (ResponseEntity<?>) ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlan(@PathVariable Long id, Authentication authentication, HttpServletRequest httpRequest) {
        User u = exigerRoleLogistique(authentication);
        if (u == null) return ResponseEntity.status(403).body("La gestion du Plan d'Armement est réservée à la Division de l'Approvisionnement.");

        return planArmementRepository.findById(id)
                .map(plan -> {
                    String details = "Article " + plan.getItem().getNomenclature() + " retiré du plan de l'unité " + plan.getUnite().getCode() + ".";
                    planArmementRepository.delete(plan);
                    journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.PLAN_ARMEMENT_SUPPRIME,
                            details, httpRequest.getRemoteAddr());
                    return (ResponseEntity<?>) ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** @return l'utilisateur authentifié s'il a un rôle de gestion logistique (SUPER_ADMIN,
     *  ADMIN ou DA_MANAGER), sinon {@code null}. */
    private static User exigerRoleLogistique(Authentication authentication) {
        User u = (authentication != null && authentication.getPrincipal() instanceof User user) ? user : null;
        if (u == null) return null;
        if (u.getRole() != Role.SUPER_ADMIN && u.getRole() != Role.ADMIN && u.getRole() != Role.DA_MANAGER) return null;
        return u;
    }
}
