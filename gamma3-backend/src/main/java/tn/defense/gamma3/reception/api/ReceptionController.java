package tn.defense.gamma3.reception.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.FonctionDA;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.util.AccesDAUtil;
import tn.defense.gamma3.reception.domain.BonEntree;
import tn.defense.gamma3.reception.domain.BonProvisoireReception;
import tn.defense.gamma3.reception.domain.LigneReception;
import tn.defense.gamma3.reception.domain.PvCommission;
import tn.defense.gamma3.reception.repository.BonEntreeRepository;
import tn.defense.gamma3.reception.repository.BonProvisoireReceptionRepository;
import tn.defense.gamma3.reception.repository.LigneReceptionRepository;
import tn.defense.gamma3.reception.repository.PvCommissionRepository;
import tn.defense.gamma3.reception.service.ReceptionService;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.repository.MagasinRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/receptions")
@RequiredArgsConstructor
public class ReceptionController {

    private final ReceptionService receptionService;
    private final BonProvisoireReceptionRepository bprRepository;
    private final PvCommissionRepository pvRepository;
    private final BonEntreeRepository beRepository;
    private final LigneReceptionRepository ligneRepository;
    private final MagasinRepository magasinRepository;

    public record BprRequestDto(BonProvisoireReception bpr, List<LigneReception> lignes) {}

    /**
     * La chaîne de réception (BPR → PV de commission → BE → entrée en stock) est une
     * opération INTERNE à la Division de l'Approvisionnement : réservée à ADMIN / DA_MANAGER.
     * Sans ce contrôle (audit 2026-09-09), n'importe quel compte authentifié pouvait créer
     * un bon d'entrée et donc incrémenter le stock d'un magasin.
     *
     * CLOISONNEMENT PAR SECTION (Phase B, 2026-09-14, voir docs/PROPOSITION_ROLES_SECTIONS_DA.md
     * §4) : ceci correspond à la Section Réception du CCF (acronyme officiel SRE). Un compte
     * ADMIN sans `fonctionDA` renseignée garde l'accès (comportement historique inchangé) ; un
     * compte ADMIN explicitement tagué avec une AUTRE section (ex : SRR, SC…) est désormais
     * refusé — voir {@link AccesDAUtil}.
     * @throws AccesReceptionRefuseException (HTTP 403) si l'appelant n'a pas le bon rôle/section
     */
    private void exigerRoleDA(Authentication authentication) {
        User u = (authentication != null && authentication.getPrincipal() instanceof User user) ? user : null;
        if (u == null) throw new AccesReceptionRefuseException("Authentification requise.");
        if (!AccesDAUtil.estAutorise(u, true, FonctionDA.SRE)) {
            throw new AccesReceptionRefuseException("La gestion des réceptions est réservée à la Division de l'Approvisionnement (Section Réception).");
        }
    }

    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    static class AccesReceptionRefuseException extends RuntimeException {
        AccesReceptionRefuseException(String m) { super(m); }
    }

    @GetMapping("/magasins")
    public List<Magasin> getAllMagasins() {
        return magasinRepository.findAll();
    }

    @GetMapping("/bpr")
    public List<BonProvisoireReception> getAllBPR() {
        return bprRepository.findAll();
    }

    @GetMapping("/bpr/{id}")
    public ResponseEntity<BonProvisoireReception> getBprById(@PathVariable Long id) {
        return bprRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bpr/{id}/lignes")
    public List<LigneReception> getLignesByBprId(@PathVariable Long id) {
        return ligneRepository.findByBpr_Id(id);
    }

    @PostMapping("/bpr")
    public ResponseEntity<BonProvisoireReception> createBPR(@RequestBody BprRequestDto requestDto, Authentication authentication) {
        exigerRoleDA(authentication);
        BonProvisoireReception saved = receptionService.creerBPR(requestDto.bpr(), requestDto.lignes());
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/pv")
    public List<PvCommission> getAllPV() {
        return pvRepository.findAll();
    }

    @PostMapping("/pv")
    public ResponseEntity<PvCommission> validerPV(@RequestBody PvCommission pv, Authentication authentication) {
        exigerRoleDA(authentication);
        return ResponseEntity.ok(receptionService.validerPV(pv));
    }

    @GetMapping("/be")
    public List<BonEntree> getAllBE() {
        return beRepository.findAll();
    }

    @PostMapping("/be")
    public ResponseEntity<BonEntree> genererBE(@RequestBody BonEntree be, Authentication authentication) {
        exigerRoleDA(authentication);
        String matricule = "admin";
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            matricule = user.getMatricule();
        }
        return ResponseEntity.ok(receptionService.genererBonEntree(be, matricule));
    }

    @GetMapping("/bpr/{bprId}/pv")
    public ResponseEntity<PvCommission> getPvByBprId(@PathVariable Long bprId) {
        return pvRepository.findByBpr_Id(bprId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/pv/{pvId}/be")
    public ResponseEntity<BonEntree> getBeByPvId(@PathVariable Long pvId) {
        return beRepository.findByPvCommission_Id(pvId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
