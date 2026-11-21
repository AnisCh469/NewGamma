package tn.defense.gamma3.prevision.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.commande.domain.DossierAchat;
import tn.defense.gamma3.commande.domain.LigneDossierAchat;
import tn.defense.gamma3.commande.domain.StatutAchat;
import tn.defense.gamma3.commande.repository.DossierAchatRepository;
import tn.defense.gamma3.prevision.domain.*;
import tn.defense.gamma3.prevision.dto.ExpressionBesoinDto;
import tn.defense.gamma3.prevision.dto.LigneExpressionBesoinDto;
import tn.defense.gamma3.prevision.dto.PrevisionDto;
import tn.defense.gamma3.prevision.dto.SyntheseConsolideeDto;
import tn.defense.gamma3.prevision.repository.ExpressionBesoinRepository;
import tn.defense.gamma3.prevision.repository.LigneExpressionBesoinRepository;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpressionBesoinService {

    private final ExpressionBesoinRepository expressionBesoinRepository;
    private final LigneExpressionBesoinRepository ligneExpressionBesoinRepository;
    private final UniteUtilisatriceRepository uniteUtilisatriceRepository;
    private final ItemRepository itemRepository;
    private final PrevisionService previsionService;
    private final DossierAchatRepository dossierAchatRepository;

    @Transactional(readOnly = true)
    public List<ExpressionBesoinDto> lister(Integer annee, StatutBesoin statut, User user) {
        if (user.getRole() == Role.UNIT_USER) {
            if (user.getUnite() == null) return List.of();
            return expressionBesoinRepository.findByUniteIdOrderByDateCreationDesc(user.getUnite().getId())
                    .stream()
                    .filter(eb -> (annee == null || eb.getAnneeBudgetaire().equals(annee))
                            && (statut == null || eb.getStatut() == statut))
                    .map(this::toDto)
                    .toList();
        }
        return expressionBesoinRepository.findByFiltres(annee, statut)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpressionBesoinDto getById(UUID id, User user) {
        ExpressionBesoin eb = expressionBesoinRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier de besoin introuvable: " + id));

        if (user.getRole() == Role.UNIT_USER && (user.getUnite() == null || !user.getUnite().getId().equals(eb.getUnite().getId()))) {
            throw new SecurityException("Accès non autorisé à ce dossier de besoin.");
        }

        return toDto(eb);
    }

    @Transactional
    public ExpressionBesoinDto enregistrer(ExpressionBesoinDto dto, User user) {
        UniteUtilisatrice unite;
        if (user.getRole() == Role.UNIT_USER) {
            unite = user.getUnite();
            if (unite == null) throw new IllegalStateException("L'utilisateur n'est rattaché à aucune unité.");
        } else {
            if (dto.uniteId() == null) throw new IllegalArgumentException("L'unité est obligatoire.");
            unite = uniteUtilisatriceRepository.findById(dto.uniteId())
                    .orElseThrow(() -> new IllegalArgumentException("Unité introuvable: " + dto.uniteId()));
        }

        Integer annee = dto.anneeBudgetaire() != null ? dto.anneeBudgetaire() : Year.now().getValue() + 1;

        ExpressionBesoin eb;
        if (dto.id() != null) {
            eb = expressionBesoinRepository.findById(dto.id())
                    .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable: " + dto.id()));
            if (eb.getStatut() != StatutBesoin.BROUILLON && user.getRole() == Role.UNIT_USER) {
                throw new IllegalStateException("Impossible de modifier un dossier déjà soumis.");
            }
            eb.setObjet(dto.objet());
            eb.setCommentaires(dto.commentaires());
            eb.getLignes().clear();
        } else {
            long seq = expressionBesoinRepository.countByAnneeBudgetaire(annee) + 1;
            String num = String.format("BES-%d-%04d", annee, seq);
            eb = ExpressionBesoin.builder()
                    .numeroDossier(num)
                    .anneeBudgetaire(annee)
                    .unite(unite)
                    .objet(dto.objet() != null ? dto.objet() : "Expression des besoins annuels " + annee)
                    .commentaires(dto.commentaires())
                    .statut(StatutBesoin.BROUILLON)
                    .creePar(user)
                    .dateCreation(LocalDateTime.now())
                    .lignes(new ArrayList<>())
                    .build();
        }

        if (dto.lignes() != null) {
            for (LigneExpressionBesoinDto lDto : dto.lignes()) {
                Item item = null;
                boolean isNouveau = Boolean.TRUE.equals(lDto.nouveauBesoin());
                if (!isNouveau && lDto.itemId() != null) {
                    item = itemRepository.findById(lDto.itemId()).orElse(null);
                }

                LigneExpressionBesoin ligne = LigneExpressionBesoin.builder()
                        .expressionBesoin(eb)
                        .item(item)
                        .nouveauBesoin(isNouveau)
                        .designationHorsCatalogue(lDto.designationHorsCatalogue())
                        .referenceConstructeur(lDto.referenceConstructeur())
                        .equipementParent(lDto.equipementParent())
                        .justificationTechnique(lDto.justificationTechnique())
                        .quantiteDemandee(lDto.quantiteDemandee() != null ? lDto.quantiteDemandee() : BigDecimal.ONE)
                        .priorite(lDto.priorite() != null ? lDto.priorite() : PrioriteBesoin.PRIORITE_2)
                        .quantiteProposeeSEP(lDto.quantiteProposeeSEP())
                        .avisSEP(lDto.avisSEP())
                        .quantiteRetenue(lDto.quantiteRetenue())
                        .prixUnitaireEstime(lDto.prixUnitaireEstime())
                        .modeAcquisitionRecommande(lDto.modeAcquisitionRecommande())
                        .decisionCommission(lDto.decisionCommission() != null ? lDto.decisionCommission() : DecisionCommission.EN_ATTENTE)
                        .motifArbitrage(lDto.motifArbitrage())
                        .transfereAchat(Boolean.TRUE.equals(lDto.transfereAchat()))
                        .build();

                eb.getLignes().add(ligne);
            }
        }

        ExpressionBesoin saved = expressionBesoinRepository.save(eb);
        return toDto(saved);
    }

    @Transactional
    public ExpressionBesoinDto soumettre(UUID id, User user) {
        ExpressionBesoin eb = expressionBesoinRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable: " + id));

        if (user.getRole() == Role.UNIT_USER && (user.getUnite() == null || !user.getUnite().getId().equals(eb.getUnite().getId()))) {
            throw new SecurityException("Action non autorisée sur ce dossier.");
        }
        if (eb.getLignes().isEmpty()) {
            throw new IllegalStateException("Le dossier ne contient aucun article.");
        }

        eb.setStatut(StatutBesoin.SOUMIS);
        eb.setDateSoumission(LocalDateTime.now());
        return toDto(expressionBesoinRepository.save(eb));
    }

    @Transactional
    public ExpressionBesoinDto etudierSEP(UUID id, List<LigneExpressionBesoinDto> etudes, User user) {
        ExpressionBesoin eb = expressionBesoinRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable: " + id));

        Map<UUID, LigneExpressionBesoinDto> mapEtudes = new HashMap<>();
        if (etudes != null) {
            for (LigneExpressionBesoinDto l : etudes) {
                if (l.id() != null) mapEtudes.put(l.id(), l);
            }
        }

        for (LigneExpressionBesoin ligne : eb.getLignes()) {
            LigneExpressionBesoinDto dtoLigne = mapEtudes.get(ligne.getId());
            if (dtoLigne != null) {
                if (dtoLigne.quantiteProposeeSEP() != null) {
                    ligne.setQuantiteProposeeSEP(dtoLigne.quantiteProposeeSEP());
                } else if (ligne.getQuantiteProposeeSEP() == null) {
                    ligne.setQuantiteProposeeSEP(ligne.getQuantiteDemandee());
                }
                ligne.setAvisSEP(dtoLigne.avisSEP());
            } else if (ligne.getQuantiteProposeeSEP() == null) {
                ligne.setQuantiteProposeeSEP(ligne.getQuantiteDemandee());
            }
        }

        eb.setStatut(StatutBesoin.ETUDIE_SEP);
        return toDto(expressionBesoinRepository.save(eb));
    }

    @Transactional
    public ExpressionBesoinDto arbitrerCommission(UUID id, List<LigneExpressionBesoinDto> arbitrages, User user) {
        ExpressionBesoin eb = expressionBesoinRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable: " + id));

        Map<UUID, LigneExpressionBesoinDto> mapArbitrage = new HashMap<>();
        if (arbitrages != null) {
            for (LigneExpressionBesoinDto a : arbitrages) {
                if (a.id() != null) mapArbitrage.put(a.id(), a);
            }
        }

        for (LigneExpressionBesoin ligne : eb.getLignes()) {
            LigneExpressionBesoinDto dtoLigne = mapArbitrage.get(ligne.getId());
            if (dtoLigne != null) {
                ligne.setQuantiteRetenue(dtoLigne.quantiteRetenue() != null ? dtoLigne.quantiteRetenue() : BigDecimal.ZERO);
                ligne.setPrixUnitaireEstime(dtoLigne.prixUnitaireEstime());
                ligne.setModeAcquisitionRecommande(dtoLigne.modeAcquisitionRecommande() != null ? dtoLigne.modeAcquisitionRecommande() : ModeAcquisition.MARCHE_CADRE);
                ligne.setDecisionCommission(dtoLigne.decisionCommission() != null ? dtoLigne.decisionCommission() : DecisionCommission.ACCORDE);
                ligne.setMotifArbitrage(dtoLigne.motifArbitrage());
            } else {
                ligne.setQuantiteRetenue(ligne.getQuantiteDemandee());
                ligne.setDecisionCommission(DecisionCommission.ACCORDE);
                ligne.setModeAcquisitionRecommande(ModeAcquisition.MARCHE_CADRE);
            }
        }

        eb.setStatut(StatutBesoin.ARBITRE);
        eb.setArbitrePar(user);
        eb.setDateArbitrage(LocalDateTime.now());
        return toDto(expressionBesoinRepository.save(eb));
    }

    @Transactional
    public ExpressionBesoinDto validerPADI(UUID id, User user) {
        ExpressionBesoin eb = expressionBesoinRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable: " + id));

        eb.setStatut(StatutBesoin.VALIDE_PADI);
        return toDto(expressionBesoinRepository.save(eb));
    }

    @Transactional
    public DossierAchat transfererVersAchats(Integer annee, List<UUID> ligneIds, ModeAcquisition mode, User user) {
        if (ligneIds == null || ligneIds.isEmpty()) {
            throw new IllegalArgumentException("Aucune ligne sélectionnée pour le transfert d'achat.");
        }

        List<LigneExpressionBesoin> lignes = ligneExpressionBesoinRepository.findAllById(ligneIds);
        if (lignes.isEmpty()) throw new IllegalArgumentException("Lignes de besoin introuvables.");

        int targetAnnee = annee != null ? annee : lignes.get(0).getExpressionBesoin().getAnneeBudgetaire();
        long seq = dossierAchatRepository.countByAnnee(targetAnnee) + 1;
        String numAchat = String.format("ACH-%d-%04d", targetAnnee, seq);

        ModeAcquisition modeFinal = mode != null ? mode :
                (lignes.get(0).getModeAcquisitionRecommande() != null ? lignes.get(0).getModeAcquisitionRecommande() : ModeAcquisition.MARCHE_CADRE);

        BigDecimal montantTotalEstime = BigDecimal.ZERO;
        List<LigneDossierAchat> lignesAchat = new ArrayList<>();

        DossierAchat dossier = DossierAchat.builder()
                .numeroDossier(numAchat)
                .annee(targetAnnee)
                .objet("Plan d'achat issu des prévisions validées " + targetAnnee)
                .modeAcquisition(modeFinal)
                .statut(StatutAchat.DOSSIER_CREE)
                .creePar(user)
                .dateCreation(LocalDateTime.now())
                .lignes(lignesAchat)
                .build();

        for (LigneExpressionBesoin l : lignes) {
            BigDecimal qte = l.getQuantiteRetenue() != null ? l.getQuantiteRetenue() : l.getQuantiteDemandee();
            BigDecimal pu = l.getPrixUnitaireEstime() != null ? l.getPrixUnitaireEstime() :
                    (l.getItem() != null && l.getItem().getPrixUnitaire() != null ? l.getItem().getPrixUnitaire() : BigDecimal.ZERO);
            BigDecimal totalLigne = qte.multiply(pu);
            montantTotalEstime = montantTotalEstime.add(totalLigne);

            String designation = l.isNouveauBesoin() ? l.getDesignationHorsCatalogue() :
                    (l.getItem() != null ? l.getItem().getDesignation() : "Article indéfini");
            String ref = l.isNouveauBesoin() ? l.getReferenceConstructeur() :
                    (l.getItem() != null ? l.getItem().getNomenclature() : "");

            LigneDossierAchat la = LigneDossierAchat.builder()
                    .dossierAchat(dossier)
                    .item(l.getItem())
                    .designation(designation)
                    .reference(ref)
                    .quantiteCommandee(qte)
                    .prixUnitaire(pu)
                    .montantTotal(totalLigne)
                    .sourceLigneBesoinId(l.getId())
                    .build();

            lignesAchat.add(la);
            l.setTransfereAchat(true);
        }

        dossier.setMontantEstimeTotal(montantTotalEstime);
        ligneExpressionBesoinRepository.saveAll(lignes);
        return dossierAchatRepository.save(dossier);
    }

    @Transactional(readOnly = true)
    public List<SyntheseConsolideeDto> getSyntheseConsolidee(Integer annee) {
        int anneeCible = annee != null ? annee : Year.now().getValue() + 1;

        // 1. Alertes mathématiques issues de l'algorithme CCF OP3
        List<PrevisionDto> alertesCalcul = previsionService.calculerAlertes();
        Map<UUID, PrevisionDto> mapAlertes = new HashMap<>();
        for (PrevisionDto a : alertesCalcul) {
            mapAlertes.put(a.itemId(), a);
        }

        // 2. Expressions de besoins soumises par les unités
        List<ExpressionBesoin> dossiers = expressionBesoinRepository.findByAnneeBudgetaireOrderByDateCreationDesc(anneeCible);

        Map<UUID, List<LigneExpressionBesoin>> besoinsParItem = new HashMap<>();
        List<LigneExpressionBesoin> nouveauxBesoins = new ArrayList<>();

        for (ExpressionBesoin eb : dossiers) {
            if (eb.getStatut() == StatutBesoin.BROUILLON) continue; // ignorer les brouillons non soumis
            for (LigneExpressionBesoin l : eb.getLignes()) {
                if (l.isNouveauBesoin()) {
                    nouveauxBesoins.add(l);
                } else if (l.getItem() != null) {
                    besoinsParItem.computeIfAbsent(l.getItem().getId(), k -> new ArrayList<>()).add(l);
                }
            }
        }

        List<SyntheseConsolideeDto> synthese = new ArrayList<>();

        // Traiter les articles du catalogue
        Set<UUID> tousItems = new HashSet<>(mapAlertes.keySet());
        tousItems.addAll(besoinsParItem.keySet());

        for (UUID itemId : tousItems) {
            PrevisionDto alerte = mapAlertes.get(itemId);
            List<LigneExpressionBesoin> lignesDemandes = besoinsParItem.getOrDefault(itemId, List.of());

            BigDecimal totalQteDemandee = BigDecimal.ZERO;
            PrioriteBesoin prioriteMax = PrioriteBesoin.PRIORITE_3;
            for (LigneExpressionBesoin l : lignesDemandes) {
                totalQteDemandee = totalQteDemandee.add(l.getQuantiteDemandee());
                if (l.getPriorite() == PrioriteBesoin.PRIORITE_1) {
                    prioriteMax = PrioriteBesoin.PRIORITE_1;
                } else if (l.getPriorite() == PrioriteBesoin.PRIORITE_2 && prioriteMax != PrioriteBesoin.PRIORITE_1) {
                    prioriteMax = PrioriteBesoin.PRIORITE_2;
                }
            }

            String nomenclature = alerte != null ? alerte.nomenclature() :
                    (lignesDemandes.isEmpty() ? "" : lignesDemandes.get(0).getItem().getNomenclature());
            String designation = alerte != null ? alerte.designation() :
                    (lignesDemandes.isEmpty() ? "" : lignesDemandes.get(0).getItem().getDesignation());

            BigDecimal stockActuel = alerte != null ? alerte.stockActuel() : BigDecimal.ZERO;
            BigDecimal stockPotentiel = alerte != null ? alerte.stockPotentiel() : BigDecimal.ZERO;
            BigDecimal reservee = alerte != null ? alerte.quantiteReservee() : BigDecimal.ZERO;
            BigDecimal cmoy3 = alerte != null ? alerte.consommationMoyenneAnnuelle() : BigDecimal.ZERO;
            BigDecimal sa = alerte != null ? alerte.seuilAlerte() : BigDecimal.ZERO;
            BigDecimal deficit = sa.subtract(stockPotentiel);
            if (deficit.compareTo(BigDecimal.ZERO) < 0) deficit = BigDecimal.ZERO;

            BigDecimal recommandationAchat = totalQteDemandee.add(deficit);

            synthese.add(SyntheseConsolideeDto.builder()
                    .itemId(itemId)
                    .nomenclature(nomenclature)
                    .designation(designation)
                    .nouveauBesoin(false)
                    .totalQuantiteDemandee(totalQteDemandee)
                    .nombreUnitesDemandeuses(lignesDemandes.size())
                    .stockActuel(stockActuel)
                    .quantiteReservee(reservee)
                    .stockPotentiel(stockPotentiel)
                    .consommationMoyenneAnnuelle(cmoy3)
                    .seuilAlerte(sa)
                    .deficitCalcule(deficit)
                    .quantiteRecommandeeAchat(recommandationAchat)
                    .prioriteLaPlusHaute(prioriteMax)
                    .build());
        }

        // Traiter les nouveaux besoins hors-catalogue
        for (LigneExpressionBesoin nb : nouveauxBesoins) {
            synthese.add(SyntheseConsolideeDto.builder()
                    .nomenclature("F01-NOUV")
                    .designation(nb.getDesignationHorsCatalogue())
                    .nouveauBesoin(true)
                    .referenceConstructeur(nb.getReferenceConstructeur())
                    .totalQuantiteDemandee(nb.getQuantiteDemandee())
                    .nombreUnitesDemandeuses(1)
                    .stockActuel(BigDecimal.ZERO)
                    .quantiteReservee(BigDecimal.ZERO)
                    .stockPotentiel(BigDecimal.ZERO)
                    .consommationMoyenneAnnuelle(BigDecimal.ZERO)
                    .seuilAlerte(BigDecimal.ZERO)
                    .deficitCalcule(nb.getQuantiteDemandee())
                    .quantiteRecommandeeAchat(nb.getQuantiteDemandee())
                    .prioriteLaPlusHaute(nb.getPriorite())
                    .build());
        }

        // Tri par urgence / quantité recommandée
        synthese.sort((a, b) -> {
            if (a.prioriteLaPlusHaute() != b.prioriteLaPlusHaute()) {
                return a.prioriteLaPlusHaute().compareTo(b.prioriteLaPlusHaute());
            }
            return b.quantiteRecommandeeAchat().compareTo(a.quantiteRecommandeeAchat());
        });

        return synthese;
    }

    private ExpressionBesoinDto toDto(ExpressionBesoin eb) {
        List<LigneExpressionBesoinDto> lignesDto = eb.getLignes().stream()
                .map(this::toLigneDto)
                .toList();

        return ExpressionBesoinDto.builder()
                .id(eb.getId())
                .numeroDossier(eb.getNumeroDossier())
                .anneeBudgetaire(eb.getAnneeBudgetaire())
                .uniteId(eb.getUnite() != null ? eb.getUnite().getId() : null)
                .uniteCode(eb.getUnite() != null ? eb.getUnite().getCode() : null)
                .uniteNom(eb.getUnite() != null ? eb.getUnite().getNom() : null)
                .objet(eb.getObjet())
                .commentaires(eb.getCommentaires())
                .statut(eb.getStatut())
                .statutLibelle(eb.getStatut().getLibelle())
                .dateCreation(eb.getDateCreation())
                .dateSoumission(eb.getDateSoumission())
                .dateArbitrage(eb.getDateArbitrage())
                .creeParNom(eb.getCreePar() != null ? eb.getCreePar().getFullName() : null)
                .arbitreParNom(eb.getArbitrePar() != null ? eb.getArbitrePar().getFullName() : null)
                .lignes(lignesDto)
                .build();
    }

    private LigneExpressionBesoinDto toLigneDto(LigneExpressionBesoin l) {
        return LigneExpressionBesoinDto.builder()
                .id(l.getId())
                .expressionBesoinId(l.getExpressionBesoin().getId())
                .itemId(l.getItem() != null ? l.getItem().getId() : null)
                .itemNomenclature(l.getItem() != null ? l.getItem().getNomenclature() : null)
                .itemDesignation(l.getItem() != null ? l.getItem().getDesignation() : null)
                .nouveauBesoin(l.isNouveauBesoin())
                .designationHorsCatalogue(l.getDesignationHorsCatalogue())
                .referenceConstructeur(l.getReferenceConstructeur())
                .equipementParent(l.getEquipementParent())
                .justificationTechnique(l.getJustificationTechnique())
                .quantiteDemandee(l.getQuantiteDemandee())
                .priorite(l.getPriorite())
                .prioriteLibelle(l.getPriorite().getLibelle())
                .quantiteProposeeSEP(l.getQuantiteProposeeSEP())
                .avisSEP(l.getAvisSEP())
                .quantiteRetenue(l.getQuantiteRetenue())
                .prixUnitaireEstime(l.getPrixUnitaireEstime())
                .modeAcquisitionRecommande(l.getModeAcquisitionRecommande())
                .decisionCommission(l.getDecisionCommission())
                .decisionLibelle(l.getDecisionCommission() != null ? l.getDecisionCommission().getLibelle() : null)
                .motifArbitrage(l.getMotifArbitrage())
                .transfereAchat(l.isTransfereAchat())
                .build();
    }
}
