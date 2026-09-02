package tn.defense.gamma3.reception.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.reception.domain.*;
import tn.defense.gamma3.reception.repository.BonEntreeRepository;
import tn.defense.gamma3.reception.repository.BonProvisoireReceptionRepository;
import tn.defense.gamma3.reception.repository.LigneReceptionRepository;
import tn.defense.gamma3.reception.repository.PvCommissionRepository;
import tn.defense.gamma3.stock.api.dto.MouvementDto;
import tn.defense.gamma3.stock.domain.TypeMouvement;
import tn.defense.gamma3.stock.service.StockService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceptionService {

    private final BonProvisoireReceptionRepository bprRepository;
    private final LigneReceptionRepository ligneReceptionRepository;
    private final PvCommissionRepository pvRepository;
    private final BonEntreeRepository beRepository;
    private final StockService stockService;

    @Transactional
    public BonProvisoireReception creerBPR(BonProvisoireReception bpr, List<LigneReception> lignes) {
        bpr.setStatut(StatutBPR.ATTENTE_PV);
        BonProvisoireReception savedBpr = bprRepository.save(bpr);

        if (lignes != null) {
            for (LigneReception ligne : lignes) {
                ligne.setBpr(savedBpr);
                ligneReceptionRepository.save(ligne);
            }
        }
        return savedBpr;
    }

    @Transactional
    public PvCommission validerPV(PvCommission pv) {
        BonProvisoireReception bpr = bprRepository.findById(pv.getBpr().getId())
                .orElseThrow(() -> new IllegalArgumentException("BPR non trouvé"));
        
        pv.setBpr(bpr);
        PvCommission savedPv = pvRepository.save(pv);

        if (pv.getDecision() == DecisionPv.ACCEPTE || pv.getDecision() == DecisionPv.ACCEPTE_AVEC_RESERVE) {
            bpr.setStatut(StatutBPR.VALIDE);
        } else {
            bpr.setStatut(StatutBPR.REJETE);
        }
        bprRepository.save(bpr);

        return savedPv;
    }

    @Transactional
    public BonEntree genererBonEntree(BonEntree be, String userMatricule) {
        PvCommission pv = pvRepository.findById(be.getPvCommission().getId())
                .orElseThrow(() -> new IllegalArgumentException("PV non trouvé"));
        
        if (pv.getDecision() == DecisionPv.REFUSE) {
            throw new IllegalStateException("Impossible de générer un BE pour un PV refusé");
        }

        be.setPvCommission(pv);
        BonEntree savedBe = beRepository.save(be);

        // Mouvement de stock automatique pour chaque ligne du BPR
        List<LigneReception> lignes = ligneReceptionRepository.findByBpr_Id(pv.getBpr().getId());
        for (LigneReception ligne : lignes) {
            if (ligne.getBpr().getId().equals(pv.getBpr().getId())) {
                MouvementDto mouvementDto = MouvementDto.builder()
                        .itemId(ligne.getItem().getId())
                        .magasinId(pv.getBpr().getMagasin().getId())
                        .quantite(ligne.getQuantiteLivree())
                        .typeMouvement(TypeMouvement.ENTREE)
                        .referenceBon(savedBe.getNumeroBe())
                        .motif("Réception fournisseur (BPR: " + pv.getBpr().getNumeroBpr() + ")")
                        .build();
                
                stockService.effectuerMouvement(mouvementDto, userMatricule);
            }
        }

        return savedBe;
    }
}
