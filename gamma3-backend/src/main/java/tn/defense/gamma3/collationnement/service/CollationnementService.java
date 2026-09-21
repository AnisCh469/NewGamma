package tn.defense.gamma3.collationnement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.collationnement.domain.CampagneCollationnement;
import tn.defense.gamma3.collationnement.domain.LigneCollationnement;
import tn.defense.gamma3.collationnement.dto.LigneComptageDto;
import tn.defense.gamma3.collationnement.repository.CampagneCollationnementRepository;
import tn.defense.gamma3.collationnement.repository.LigneCollationnementRepository;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * RÔLE : Procédure P07 du CCF (Comptabilité / Collationnement annuel) — voir la javadoc de
 * {@link CampagneCollationnement} pour la conception d'ensemble.
 */
@Service
@RequiredArgsConstructor
public class CollationnementService {

    private final CampagneCollationnementRepository campagneRepository;
    private final LigneCollationnementRepository ligneRepository;
    private final PlanArmementRepository planArmementRepository;
    private final UniteUtilisatriceRepository uniteRepository;

    @Transactional(readOnly = true)
    public List<CampagneCollationnement> getAllCampagnes() {
        return campagneRepository.findAllWithRelations();
    }

    @Transactional(readOnly = true)
    public CampagneCollationnement getCampagne(Long id) {
        return campagneRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new IllegalArgumentException("Campagne introuvable (id=" + id + ")."));
    }

    @Transactional(readOnly = true)
    public List<LigneCollationnement> getLignes(Long campagneId) {
        return ligneRepository.findByCampagneId(campagneId);
    }

    /**
     * Lance une campagne pour une unité : chaque ligne du Plan d'Armement de l'unité devient
     * une {@link LigneCollationnement} avec sa dotation théorique FIGÉE à cet instant (le Plan
     * d'Armement continue d'évoluer ensuite — la campagne doit rester une photographie fidèle
     * de ce qui a été effectivement soumis à vérification physique, voir CCF RG83-RG86).
     */
    @Transactional
    public CampagneCollationnement creerCampagne(Long uniteId, LocalDate datePlanifiee, String membresCommission) {
        var unite = uniteRepository.findById(uniteId)
                .orElseThrow(() -> new IllegalArgumentException("Unité introuvable (id=" + uniteId + ")."));

        List<PlanArmement> lignesPlan = planArmementRepository.findByUniteId(uniteId);
        if (lignesPlan.isEmpty()) {
            throw new IllegalStateException("Cette unité n'a aucune ligne de Plan d'Armement à collationner.");
        }

        CampagneCollationnement campagne = campagneRepository.save(CampagneCollationnement.builder()
                .unite(unite)
                .datePlanifiee(datePlanifiee != null ? datePlanifiee : LocalDate.now())
                .statut("PLANIFIEE")
                .membresCommission(membresCommission)
                .build());

        for (PlanArmement ligne : lignesPlan) {
            ligneRepository.save(LigneCollationnement.builder()
                    .campagne(campagne)
                    .item(ligne.getItem())
                    .quantiteTheorique(ligne.getQuantiteReelle())
                    .build());
        }

        return campagne;
    }

    /** Saisie du constat physique par la commission — plusieurs passages possibles avant
     *  clôture (une campagne peut s'étaler sur plusieurs jours de vérification). */
    @Transactional
    public CampagneCollationnement saisirComptage(Long campagneId, List<LigneComptageDto> comptages) {
        CampagneCollationnement campagne = getCampagne(campagneId);
        if ("TERMINEE".equals(campagne.getStatut())) {
            throw new IllegalStateException("Cette campagne est déjà clôturée — le comptage ne peut plus être modifié.");
        }

        Map<Long, LigneCollationnement> lignesParId = ligneRepository.findByCampagneId(campagneId).stream()
                .collect(java.util.stream.Collectors.toMap(LigneCollationnement::getId, l -> l));

        for (LigneComptageDto c : comptages) {
            LigneCollationnement ligne = lignesParId.get(c.ligneId());
            if (ligne == null) continue; // ligne d'une autre campagne, ignorée silencieusement
            ligne.setQuantitePhysiqueConstatee(c.quantitePhysiqueConstatee());
            ligne.setObservations(c.observations());
            ligneRepository.save(ligne);
        }

        if (!"TERMINEE".equals(campagne.getStatut())) {
            campagne.setStatut("EN_COURS");
            campagneRepository.save(campagne);
        }
        return campagne;
    }

    /**
     * Clôture la campagne : exige que TOUTES les lignes aient été comptées (CCF : la commission
     * "procède à l'opération de collationnement" avant de signer le registre — une clôture
     * partielle n'aurait pas de sens). La conclusion (CONFORME / ECART_CONSTATE, CCF RG88-RG90)
     * est déterminée automatiquement à partir des écarts relevés, jamais saisie à la main.
     */
    @Transactional
    public CampagneCollationnement cloturerCampagne(Long campagneId, String observationsGenerales) {
        CampagneCollationnement campagne = getCampagne(campagneId);
        if ("TERMINEE".equals(campagne.getStatut())) {
            throw new IllegalStateException("Cette campagne est déjà clôturée.");
        }

        List<LigneCollationnement> lignes = ligneRepository.findByCampagneId(campagneId);
        long nonComptees = lignes.stream().filter(l -> l.getQuantitePhysiqueConstatee() == null).count();
        if (nonComptees > 0) {
            throw new IllegalStateException(nonComptees + " ligne(s) n'ont pas encore de constat physique — la clôture exige un comptage complet.");
        }

        boolean auMoinsUnEcart = lignes.stream().anyMatch(l -> l.getEcart().compareTo(java.math.BigDecimal.ZERO) != 0);

        campagne.setStatut("TERMINEE");
        campagne.setDateCloture(LocalDate.now());
        campagne.setConclusionGenerale(auMoinsUnEcart ? "ECART_CONSTATE" : "CONFORME");
        campagne.setObservationsGenerales(observationsGenerales);

        return campagneRepository.save(campagne);
    }
}
