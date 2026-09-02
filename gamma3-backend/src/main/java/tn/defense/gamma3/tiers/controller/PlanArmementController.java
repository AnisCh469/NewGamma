package tn.defense.gamma3.tiers.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;
import tn.defense.gamma3.catalogue.repository.ItemRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans-armement")
@RequiredArgsConstructor
public class PlanArmementController {

    private final PlanArmementRepository planArmementRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final ItemRepository itemRepository;

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
    public ResponseEntity<PlanArmement> createPlan(@RequestBody PlanArmement plan) {
        if (plan.getUnite() == null || plan.getUnite().getId() == null || 
            plan.getItem() == null || plan.getItem().getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return uniteRepository.findById(plan.getUnite().getId())
                .flatMap(unite -> itemRepository.findById(plan.getItem().getId())
                        .map(item -> {
                            plan.setUnite(unite);
                            plan.setItem(item);
                            return planArmementRepository.save(plan);
                        })
                )
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanArmement> updatePlan(@PathVariable Long id, @RequestBody PlanArmement planDetails) {
        return planArmementRepository.findById(id)
                .map(plan -> {
                    plan.setQuantiteType(planDetails.getQuantiteType());
                    plan.setQuantiteReelle(planDetails.getQuantiteReelle());
                    plan.setQuantiteVirtuelle(planDetails.getQuantiteVirtuelle());
                    return ResponseEntity.ok(planArmementRepository.save(plan));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        return planArmementRepository.findById(id)
                .map(plan -> {
                    planArmementRepository.delete(plan);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
