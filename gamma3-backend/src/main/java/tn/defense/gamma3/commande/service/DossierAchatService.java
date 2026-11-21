package tn.defense.gamma3.commande.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.commande.domain.DossierAchat;
import tn.defense.gamma3.commande.domain.LigneDossierAchat;
import tn.defense.gamma3.commande.domain.StatutAchat;
import tn.defense.gamma3.commande.dto.DossierAchatDto;
import tn.defense.gamma3.commande.dto.LigneDossierAchatDto;
import tn.defense.gamma3.commande.repository.DossierAchatRepository;
import tn.defense.gamma3.prevision.domain.ModeAcquisition;
import tn.defense.gamma3.tiers.domain.Fournisseur;
import tn.defense.gamma3.tiers.repository.FournisseurRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DossierAchatService {

    private final DossierAchatRepository dossierAchatRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ItemRepository itemRepository;

    @Transactional(readOnly = true)
    public List<DossierAchatDto> lister(Integer annee, StatutAchat statut) {
        return dossierAchatRepository.findByFiltres(annee, statut)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public DossierAchatDto getById(UUID id) {
        DossierAchat da = dossierAchatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier d'achat introuvable: " + id));
        return toDto(da);
    }

    @Transactional
    public DossierAchatDto creerOuModifier(DossierAchatDto dto, User user) {
        int annee = dto.annee() != null ? dto.annee() : Year.now().getValue();

        Fournisseur fournisseur = null;
        if (dto.fournisseurId() != null) {
            fournisseur = fournisseurRepository.findById(dto.fournisseurId()).orElse(null);
        }

        DossierAchat da;
        if (dto.id() != null) {
            da = dossierAchatRepository.findById(dto.id())
                    .orElseThrow(() -> new IllegalArgumentException("Dossier d'achat introuvable: " + dto.id()));
            da.setObjet(dto.objet());
            da.setFournisseur(fournisseur);
            da.setReferenceMarcheOuBon(dto.referenceMarcheOuBon());
            da.setModeAcquisition(dto.modeAcquisition() != null ? dto.modeAcquisition() : ModeAcquisition.MARCHE_CADRE);
            da.setDateNotification(dto.dateNotification());
            da.setDateLivraisonPrevue(dto.dateLivraisonPrevue());
            da.setObservations(dto.observations());
            if (dto.montantReelTotal() != null) {
                da.setMontantReelTotal(dto.montantReelTotal());
            }
            da.getLignes().clear();
        } else {
            long seq = dossierAchatRepository.countByAnnee(annee) + 1;
            String num = String.format("ACH-%d-%04d", annee, seq);
            da = DossierAchat.builder()
                    .numeroDossier(num)
                    .annee(annee)
                    .objet(dto.objet() != null ? dto.objet() : "Commande d'achat " + annee)
                    .modeAcquisition(dto.modeAcquisition() != null ? dto.modeAcquisition() : ModeAcquisition.MARCHE_CADRE)
                    .fournisseur(fournisseur)
                    .statut(StatutAchat.DOSSIER_CREE)
                    .referenceMarcheOuBon(dto.referenceMarcheOuBon())
                    .dateNotification(dto.dateNotification())
                    .dateLivraisonPrevue(dto.dateLivraisonPrevue())
                    .observations(dto.observations())
                    .creePar(user)
                    .dateCreation(LocalDateTime.now())
                    .lignes(new ArrayList<>())
                    .build();
        }

        BigDecimal montantEstime = BigDecimal.ZERO;
        if (dto.lignes() != null) {
            for (LigneDossierAchatDto lDto : dto.lignes()) {
                Item item = null;
                if (lDto.itemId() != null) {
                    item = itemRepository.findById(lDto.itemId()).orElse(null);
                }

                BigDecimal qte = lDto.quantiteCommandee() != null ? lDto.quantiteCommandee() : BigDecimal.ONE;
                BigDecimal pu = lDto.prixUnitaire() != null ? lDto.prixUnitaire() : BigDecimal.ZERO;
                BigDecimal total = qte.multiply(pu);
                montantEstime = montantEstime.add(total);

                LigneDossierAchat la = LigneDossierAchat.builder()
                        .dossierAchat(da)
                        .item(item)
                        .designation(lDto.designation())
                        .reference(lDto.reference())
                        .quantiteCommandee(qte)
                        .prixUnitaire(pu)
                        .montantTotal(total)
                        .sourceLigneBesoinId(lDto.sourceLigneBesoinId())
                        .build();

                da.getLignes().add(la);
            }
        }

        da.setMontantEstimeTotal(montantEstime);
        return toDto(dossierAchatRepository.save(da));
    }

    @Transactional
    public DossierAchatDto changerStatut(UUID id, StatutAchat nouveauStatut, String referenceMarche, LocalDate dateNotification, String observations) {
        DossierAchat da = dossierAchatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier d'achat introuvable: " + id));

        da.setStatut(nouveauStatut);
        if (referenceMarche != null && !referenceMarche.isBlank()) {
            da.setReferenceMarcheOuBon(referenceMarche);
        }
        if (dateNotification != null) {
            da.setDateNotification(dateNotification);
        }
        if (observations != null) {
            da.setObservations(observations);
        }

        return toDto(dossierAchatRepository.save(da));
    }

    private DossierAchatDto toDto(DossierAchat da) {
        List<LigneDossierAchatDto> lignesDto = da.getLignes().stream()
                .map(this::toLigneDto)
                .toList();

        return DossierAchatDto.builder()
                .id(da.getId())
                .numeroDossier(da.getNumeroDossier())
                .annee(da.getAnnee())
                .objet(da.getObjet())
                .modeAcquisition(da.getModeAcquisition())
                .modeAcquisitionLibelle(da.getModeAcquisition() != null ? da.getModeAcquisition().getLibelle() : null)
                .fournisseurId(da.getFournisseur() != null ? da.getFournisseur().getId() : null)
                .fournisseurNom(da.getFournisseur() != null ? da.getFournisseur().getNom() : null)
                .statut(da.getStatut())
                .statutLibelle(da.getStatut() != null ? da.getStatut().getLibelle() : null)
                .referenceMarcheOuBon(da.getReferenceMarcheOuBon())
                .montantEstimeTotal(da.getMontantEstimeTotal())
                .montantReelTotal(da.getMontantReelTotal())
                .dateCreation(da.getDateCreation())
                .dateNotification(da.getDateNotification())
                .dateLivraisonPrevue(da.getDateLivraisonPrevue())
                .observations(da.getObservations())
                .creeParNom(da.getCreePar() != null ? da.getCreePar().getFullName() : null)
                .lignes(lignesDto)
                .build();
    }

    private LigneDossierAchatDto toLigneDto(LigneDossierAchat l) {
        return LigneDossierAchatDto.builder()
                .id(l.getId())
                .dossierAchatId(l.getDossierAchat().getId())
                .itemId(l.getItem() != null ? l.getItem().getId() : null)
                .itemNomenclature(l.getItem() != null ? l.getItem().getNomenclature() : null)
                .designation(l.getDesignation())
                .reference(l.getReference())
                .quantiteCommandee(l.getQuantiteCommandee())
                .prixUnitaire(l.getPrixUnitaire())
                .montantTotal(l.getMontantTotal())
                .sourceLigneBesoinId(l.getSourceLigneBesoinId())
                .build();
    }
}
