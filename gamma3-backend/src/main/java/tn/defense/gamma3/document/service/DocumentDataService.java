package tn.defense.gamma3.document.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.collationnement.domain.CampagneCollationnement;
import tn.defense.gamma3.collationnement.domain.LigneCollationnement;
import tn.defense.gamma3.collationnement.repository.CampagneCollationnementRepository;
import tn.defense.gamma3.collationnement.repository.LigneCollationnementRepository;
import tn.defense.gamma3.distribution.domain.BonSortie;
import tn.defense.gamma3.distribution.domain.DemandeMateriel;
import tn.defense.gamma3.distribution.domain.LigneDemande;
import tn.defense.gamma3.distribution.repository.DemandeMaterielRepository;
import tn.defense.gamma3.distribution.repository.LigneDemandeRepository;
import tn.defense.gamma3.prevision.dto.PrevisionDto;
import tn.defense.gamma3.prevision.service.PrevisionService;
import tn.defense.gamma3.reception.domain.*;
import tn.defense.gamma3.reception.repository.*;
import tn.defense.gamma3.reforme.domain.DossierReforme;
import tn.defense.gamma3.reforme.domain.LigneReforme;
import tn.defense.gamma3.reforme.repository.DossierReformeRepository;
import tn.defense.gamma3.reforme.repository.LigneReformeRepository;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.repository.MagasinRepository;
import tn.defense.gamma3.tiers.domain.CommandeFournisseur;
import tn.defense.gamma3.tiers.domain.Fournisseur;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import tn.defense.gamma3.tiers.repository.CommandeFournisseurRepository;
import tn.defense.gamma3.tiers.repository.FournisseurRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * RÔLE : construire, pour chaque document officiel, le « modèle » (une {@code Map}) que le
 * moteur de rendu ({@link DocumentPdfService}) injecte dans le modèle HTML Thymeleaf.
 *
 * Toute la navigation JPA (relations paresseuses) se fait ici, dans une transaction en
 * lecture seule. Les modèles ne contiennent que des types simples (String, nombres, listes
 * de Map) : le template n'accède jamais directement à une entité, ce qui évite les
 * {@code LazyInitializationException} au rendu et garde les modèles faciles à tester.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentDataService {

    private final DemandeMaterielRepository demandeRepository;
    private final LigneDemandeRepository ligneDemandeRepository;
    private final MagasinRepository magasinRepository;
    private final BonProvisoireReceptionRepository bprRepository;
    private final LigneReceptionRepository ligneReceptionRepository;
    private final PvCommissionRepository pvCommissionRepository;
    private final BonEntreeRepository bonEntreeRepository;
    private final DossierReformeRepository dossierReformeRepository;
    private final LigneReformeRepository ligneReformeRepository;
    private final CommandeFournisseurRepository commandeRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ItemRepository itemRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final CampagneCollationnementRepository campagneCollationnementRepository;
    private final LigneCollationnementRepository ligneCollationnementRepository;
    private final PrevisionService previsionService;
    private final BarcodeService barcode;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    // ═══════════════════════════ BON DE SORTIE ═══════════════════════════

    public Map<String, Object> bonSortie(Long demandeId) {
        DemandeMateriel demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> new IntrouvableException("Demande de matériel " + demandeId + " introuvable."));
        BonSortie bs = demande.getBonSortie();
        if (bs == null) {
            throw new IntrouvableException("Aucun bon de sortie n'a encore été généré pour la demande "
                    + demande.getNumeroDemande() + " (elle doit d'abord être approuvée).");
        }
        UniteUtilisatrice unite = demande.getUnite();
        Magasin magasin = bs.getMagasinId() != null
                ? magasinRepository.findById(bs.getMagasinId()).orElse(null)
                : demande.getMagasinDesigne();

        List<LigneDemande> lignes = ligneDemandeRepository.findByDemande_Id(demandeId);
        List<Map<String, Object>> lignesModel = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int rang = 1;
        for (LigneDemande l : lignes) {
            Item item = l.getItem();
            BigDecimal qte = l.getQuantiteAccordee() != null ? l.getQuantiteAccordee() : l.getQuantiteDemandee();
            BigDecimal pu = nz(item.getPrixUnitaire());
            BigDecimal valeur = pu.multiply(nz(qte));
            total = total.add(valeur);
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("rang", rang++);
            lm.put("nomenclature", item.getNomenclature());
            lm.put("designation", ns(item.getDesignation()));
            lm.put("uniteGestion", ns(item.getUniteGestionCode()));
            lm.put("qteDemandee", num(l.getQuantiteDemandee()));
            lm.put("qteAccordee", num(qte));
            lm.put("prixUnitaire", money(pu));
            lm.put("valeur", money(valeur));
            lignesModel.add(lm);
        }

        String qrContenu = "BS:" + bs.getNumeroBs()
                + "|DEM:" + demande.getNumeroDemande()
                + "|UNIT:" + (unite != null ? unite.getCode() : "?");

        Map<String, Object> m = base("BON DE SORTIE MATÉRIELS",
                "Sortie de stock pour affectation à l'unité", bs.getNumeroBs(),
                bs.getDateSortie(), qrContenu);
        m.put("uniteId", unite != null ? unite.getId() : null);
        m.put("uniteNom", unite != null ? unite.getNom() : "—");
        m.put("uniteCode", unite != null ? unite.getCode() : "—");
        m.put("uniteBase", unite != null && unite.getBaseNavale() != null ? unite.getBaseNavale() : "—");
        m.put("magasinNom", magasin != null ? magasin.getNom() : "—");
        m.put("magasinCode", magasin != null ? magasin.getCode() : "—");
        m.put("demandeNumero", demande.getNumeroDemande());
        m.put("demandeDate", demande.getDateDemande() != null ? demande.getDateDemande().format(D) : "—");
        m.put("statut", libelleStatutDemande(demande.getStatut() != null ? demande.getStatut().name() : ""));
        m.put("transporteur", ns(bs.getTransporteur(), "Service Transit DA"));
        m.put("vehicule", ns(bs.getVehiculeMatricule(), "—"));
        m.put("bsStatut", "LIVRE".equals(bs.getStatut()) ? "Matériel remis" : "Bon préparé — en attente de retrait");
        m.put("lignes", lignesModel);
        m.put("totalValeur", money(total));
        m.put("visas", List.of(
                Map.of("titre", "L'Officier Expéditeur (DA)", "sous", "Signature &amp; Cachet"),
                Map.of("titre", "Le Chef de Convoi (Transport)", "sous", "Signature &amp; Visa"),
                Map.of("titre", "L'Officier Réceptionnaire (Unité)", "sous", "Signature &amp; Cachet")));
        return m;
    }

    // ═══════════════════════════ BON D'ENTRÉE (réception) ═══════════════════════════

    public Map<String, Object> bonEntree(Long bprId) {
        BonProvisoireReception bpr = bprRepository.findById(bprId)
                .orElseThrow(() -> new IntrouvableException("Bon provisoire de réception " + bprId + " introuvable."));
        PvCommission pv = pvCommissionRepository.findByBpr_Id(bprId).orElse(null);
        BonEntree be = pv != null ? bonEntreeRepository.findByPvCommission_Id(pv.getId()).orElse(null) : null;

        Fournisseur f = bpr.getFournisseur();
        Magasin magasin = bpr.getMagasin();
        CommandeFournisseur cmd = bpr.getCommande();

        List<LigneReception> lignes = ligneReceptionRepository.findByBpr_Id(bprId);
        List<Map<String, Object>> lignesModel = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int rang = 1;
        for (LigneReception l : lignes) {
            Item item = l.getItem();
            BigDecimal pu = nz(l.getPrixUnitaire() != null ? l.getPrixUnitaire() : item.getPrixUnitaire());
            BigDecimal valeur = pu.multiply(nz(l.getQuantiteLivree()));
            total = total.add(valeur);
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("rang", rang++);
            lm.put("nomenclature", item.getNomenclature());
            lm.put("designation", ns(item.getDesignation()));
            lm.put("uniteGestion", ns(item.getUniteGestionCode()));
            lm.put("qteLivree", num(l.getQuantiteLivree()));
            lm.put("prixUnitaire", money(pu));
            lm.put("valeur", money(valeur));
            lignesModel.add(lm);
        }

        String numero = be != null ? be.getNumeroBe() : "BE-" + bpr.getNumeroBpr();
        LocalDate dateDoc = be != null ? be.getDateEntree() : bpr.getDateReception();
        String qrContenu = "BE:" + numero + "|BPR:" + bpr.getNumeroBpr()
                + "|FRN:" + (f != null ? f.getCode() : "?");

        Map<String, Object> m = base("BON D'ENTRÉE EN MAGASIN",
                "Prise en charge du matériel réceptionné", numero, dateDoc, qrContenu);
        m.put("bprNumero", bpr.getNumeroBpr());
        m.put("bprDate", bpr.getDateReception() != null ? bpr.getDateReception().format(D) : "—");
        m.put("fournisseurNom", f != null ? f.getNom() : "—");
        m.put("fournisseurCode", f != null ? f.getCode() : "—");
        m.put("commandeNumero", cmd != null ? cmd.getNumeroCommande() : "Hors commande / dotation directe");
        m.put("magasinNom", magasin != null ? magasin.getNom() : "—");
        m.put("magasinCode", magasin != null ? magasin.getCode() : "—");
        m.put("pvNumero", pv != null ? pv.getNumeroPv() : "—");
        m.put("pvDate", pv != null && pv.getDateCommission() != null ? pv.getDateCommission().format(D) : "—");
        m.put("pvDecision", pv != null && pv.getDecision() != null ? pv.getDecision().name() : "—");
        m.put("pvMembres", pv != null ? ns(pv.getMembres(), "—") : "—");
        m.put("pvObservations", pv != null ? ns(pv.getObservations(), "") : "");
        m.put("lignes", lignesModel);
        m.put("totalValeur", money(total));
        m.put("visas", List.of(
                Map.of("titre", "Le Magasinier (DA)", "sous", "Signature &amp; Cachet"),
                Map.of("titre", "Président de la Commission", "sous", "Signature &amp; Visa"),
                Map.of("titre", "Le Chef de la Division Approvisionnement", "sous", "Signature &amp; Cachet")));
        return m;
    }

    // ═══════════════════════════ PV DE RÉFORME ═══════════════════════════

    public Map<String, Object> pvReforme(Long dossierId) {
        DossierReforme d = dossierReformeRepository.findByIdWithRelations(dossierId)
                .orElseThrow(() -> new IntrouvableException("Dossier de réforme " + dossierId + " introuvable."));
        UniteUtilisatrice unite = d.getUnite();
        List<LigneReforme> lignes = ligneReformeRepository.findByDossierReformeId(dossierId);
        List<Map<String, Object>> lignesModel = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int rang = 1;
        for (LigneReforme l : lignes) {
            Item item = l.getItem();
            BigDecimal pu = nz(item.getPrixUnitaire());
            BigDecimal valeur = pu.multiply(nz(l.getQuantite()));
            total = total.add(valeur);
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("rang", rang++);
            lm.put("nomenclature", item.getNomenclature());
            lm.put("designation", ns(item.getDesignation()));
            lm.put("quantite", num(l.getQuantite()));
            lm.put("prixUnitaire", money(pu));
            lm.put("valeur", money(valeur));
            lm.put("observations", ns(l.getObservations(), "—"));
            lignesModel.add(lm);
        }

        String qrContenu = "REFORME:" + d.getNumeroDossier() + "|UNIT:" + (unite != null ? unite.getCode() : "?");
        Map<String, Object> m = base("PROCÈS-VERBAL DE RÉFORME",
                "Commission de réforme du matériel naval", d.getNumeroDossier(),
                d.getDateCommission() != null ? d.getDateCommission() : d.getDateDemande(), qrContenu);
        m.put("uniteId", unite != null ? unite.getId() : null);
        m.put("uniteNom", unite != null ? unite.getNom() : "—");
        m.put("uniteCode", unite != null ? unite.getCode() : "—");
        m.put("dateDemande", d.getDateDemande() != null ? d.getDateDemande().format(D) : "—");
        m.put("dateCommission", d.getDateCommission() != null ? d.getDateCommission().format(D) : "—");
        m.put("statut", ns(d.getStatut(), "—"));
        m.put("motif", ns(d.getMotif(), "—"));
        m.put("decisionCommission", libelleDecisionReforme(d.getDecisionCommission()));
        m.put("membresCommission", ns(d.getMembresCommission(), "—"));
        m.put("observationsCommission", ns(d.getObservationsCommission(), "—"));
        m.put("lignes", lignesModel);
        m.put("totalValeur", money(total));
        m.put("visas", List.of(
                Map.of("titre", "Président de la Commission", "sous", "Signature"),
                Map.of("titre", "Membre Rapporteur", "sous", "Signature"),
                Map.of("titre", "Le Commandant de l'Unité", "sous", "Signature &amp; Cachet")));
        return m;
    }

    // ═══════════════════════════ FACTURE / BON DE COMMANDE ═══════════════════════════

    public Map<String, Object> facture(Long commandeId) {
        CommandeFournisseur c = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new IntrouvableException("Commande fournisseur " + commandeId + " introuvable."));
        Fournisseur f = c.getFournisseur();

        String qrContenu = "CMD:" + c.getNumeroCommande() + "|FRN:" + (f != null ? f.getCode() : "?");
        Map<String, Object> m = base("BON DE COMMANDE FOURNISSEUR",
                "Engagement de dépense — Division de l'Approvisionnement", c.getNumeroCommande(),
                c.getDateCommande(), qrContenu);
        m.put("fournisseurNom", f != null ? f.getNom() : "—");
        m.put("fournisseurCode", f != null ? f.getCode() : "—");
        m.put("fournisseurMf", f != null ? ns(f.getMatriculeFiscal(), "—") : "—");
        m.put("fournisseurAdresse", f != null ? ns(f.getAdresse(), "—") : "—");
        m.put("fournisseurTel", f != null ? ns(f.getTelephone(), "—") : "—");
        m.put("fournisseurEmail", f != null ? ns(f.getEmail(), "—") : "—");
        m.put("marcheNumero", c.getMarche() != null ? c.getMarche().getNumeroMarche() : "Hors marché");
        m.put("marcheDesignation", c.getMarche() != null ? ns(c.getMarche().getDesignation(), "—") : "—");
        m.put("statut", c.getStatut() != null ? c.getStatut().name() : "—");
        m.put("montantTotal", money(nz(c.getMontantTotal())));
        m.put("visas", List.of(
                Map.of("titre", "Le Chef de la Division Approvisionnement", "sous", "Signature &amp; Cachet"),
                Map.of("titre", "Le Contrôleur des Dépenses", "sous", "Visa"),
                Map.of("titre", "Le Fournisseur (Bon pour accord)", "sous", "Signature &amp; Cachet")));
        return m;
    }

    // ═══════════════════════════ FICHE ARTICLE ═══════════════════════════

    public Map<String, Object> ficheArticle(String itemIdOrNomenclature) {
        Item item = resoudreItem(itemIdOrNomenclature);
        String qrContenu = "ART:" + item.getNomenclature();
        Map<String, Object> m = base("FICHE ARTICLE", "Catalogue technique GAMMA 3",
                item.getNomenclature(), LocalDate.now(), qrContenu);
        m.put("nomenclature", item.getNomenclature());
        m.put("designation", ns(item.getDesignation()));
        m.put("classe", item.getClasseCode());
        m.put("sousClasse", item.getSousClasseCode());
        m.put("categorie", item.getCategorieCode());
        m.put("serie", item.getSerieCode());
        m.put("codeArticle", item.getItemCode());
        m.put("uniteGestion", ns(item.getUniteGestionCode(), "—"));
        m.put("prixUnitaire", money(nz(item.getPrixUnitaire())));
        m.put("stockSecurite", num(item.getStockSecurite()));
        m.put("typeConsommabilite", item.getTypeConsommabilite() != null ? item.getTypeConsommabilite().name() : "—");
        m.put("classeDanger", item.getDangerClass() != null ? item.getDangerClass().name() : "Aucune (non classé)");
        m.put("code128", barcode.code128DataUri(item.getNomenclature(), 520, 90));
        return m;
    }

    // ═══════════════════════════ FICHE FOURNISSEUR ═══════════════════════════

    public Map<String, Object> ficheFournisseur(Long fournisseurId) {
        Fournisseur f = fournisseurRepository.findById(fournisseurId)
                .orElseThrow(() -> new IntrouvableException("Fournisseur " + fournisseurId + " introuvable."));
        String qrContenu = "FRN:" + f.getCode();
        Map<String, Object> m = base("FICHE FOURNISSEUR", "Référentiel des tiers — Division de l'Approvisionnement",
                f.getCode(), LocalDate.now(), qrContenu);
        m.put("code", f.getCode());
        m.put("nom", f.getNom());
        m.put("matriculeFiscal", ns(f.getMatriculeFiscal(), "—"));
        m.put("adresse", ns(f.getAdresse(), "—"));
        m.put("contactNom", ns(f.getContactNom(), "—"));
        m.put("telephone", ns(f.getTelephone(), "—"));
        m.put("fax", ns(f.getFax(), "—"));
        m.put("email", ns(f.getEmail(), "—"));
        m.put("note", f.getNote() != null ? f.getNote() + " / 10" : "Non évalué");
        m.put("statut", f.getStatut() != null ? f.getStatut().name() : "—");
        m.put("code128", barcode.code128DataUri(f.getCode(), 420, 80));
        return m;
    }

    // ═══════════════════════════ ÉTIQUETTES (petit format) ═══════════════════════════

    public Map<String, Object> etiquetteArticle(String itemIdOrNomenclature) {
        Item item = resoudreItem(itemIdOrNomenclature);
        Map<String, Object> m = new HashMap<>();
        m.put("numero", item.getNomenclature());
        m.put("nomenclature", item.getNomenclature());
        m.put("designation", ns(item.getDesignation()));
        m.put("classe", item.getClasseCode());
        m.put("uniteGestion", ns(item.getUniteGestionCode(), ""));
        m.put("classeDanger", item.getDangerClass() != null ? item.getDangerClass().name() : null);
        m.put("qr", barcode.qrCodeDataUri("ART:" + item.getNomenclature(), 300));
        m.put("code128", barcode.code128DataUri(item.getNomenclature(), 440, 70));
        m.put("genereLe", LocalDateTime.now().format(DT));
        return m;
    }

    public Map<String, Object> ficheUnite(Long uniteId) {
        UniteUtilisatrice u = uniteRepository.findById(uniteId)
                .orElseThrow(() -> new IntrouvableException("Unité " + uniteId + " introuvable."));
        int nbDossiersReforme = dossierReformeRepository.findByUniteId(uniteId).size();
        Map<String, Object> m = base("FICHE UNITÉ UTILISATRICE",
                "Référentiel des unités navigantes — Division de l'Approvisionnement",
                u.getCode(), LocalDate.now(), "UNIT:" + u.getCode());
        m.put("uniteId", u.getId());
        m.put("code", u.getCode());
        m.put("nom", u.getNom());
        m.put("baseNavale", u.getBaseNavale() != null ? u.getBaseNavale() : "—");
        m.put("nbDossiersReforme", nbDossiersReforme);
        m.put("code128", barcode.code128DataUri(u.getCode(), 420, 80));
        return m;
    }

    public Map<String, Object> etiquetteUnite(Long uniteId) {
        UniteUtilisatrice u = uniteRepository.findById(uniteId)
                .orElseThrow(() -> new IntrouvableException("Unité " + uniteId + " introuvable."));
        Map<String, Object> m = new HashMap<>();
        m.put("numero", u.getCode());
        m.put("code", u.getCode());
        m.put("nom", u.getNom());
        m.put("baseNavale", u.getBaseNavale() != null ? u.getBaseNavale() : "—");
        m.put("qr", barcode.qrCodeDataUri("UNIT:" + u.getCode(), 300));
        m.put("code128", barcode.code128DataUri(u.getCode(), 440, 70));
        m.put("genereLe", LocalDateTime.now().format(DT));
        return m;
    }

    // ═══════════════════════════ COLLATIONNEMENT & PRÉVISIONS ═══════════════════════════

    public Map<String, Object> pvCollationnement(Long campagneId) {
        CampagneCollationnement c = campagneCollationnementRepository.findByIdWithRelations(campagneId)
                .orElseThrow(() -> new IntrouvableException("Campagne de collationnement " + campagneId + " introuvable."));
        UniteUtilisatrice unite = c.getUnite();
        List<LigneCollationnement> lignes = ligneCollationnementRepository.findByCampagneId(campagneId);

        String refNumero = String.format("PV-COL-%04d", c.getId());
        String qrContenu = "PV-COL:" + c.getId()
                + "|UNIT:" + (unite != null ? unite.getCode() : "?")
                + "|STAT:" + c.getStatut();

        Map<String, Object> m = base("PROCÈS-VERBAL DE COLLATIONNEMENT",
                "Contrôle annuel des dotations du Plan d'Armement — Procédure CCF P07",
                refNumero, c.getDateCloture() != null ? c.getDateCloture() : c.getDatePlanifiee(),
                qrContenu);

        m.put("uniteId", unite != null ? unite.getId() : null);
        m.put("uniteNom", unite != null ? unite.getNom() : "—");
        m.put("uniteCode", unite != null ? unite.getCode() : "—");
        m.put("datePlanifiee", c.getDatePlanifiee() != null ? c.getDatePlanifiee().format(D) : "—");
        m.put("dateCloture", c.getDateCloture() != null ? c.getDateCloture().format(D) : "Non clôturée");
        m.put("statut", c.getStatut());
        m.put("conclusionGenerale", ns(c.getConclusionGenerale(), "—"));
        m.put("membresCommission", ns(c.getMembresCommission(), "Non renseignés"));
        m.put("observationsGenerales", c.getObservationsGenerales());

        List<Map<String, Object>> lignesModel = new ArrayList<>();
        int rang = 1;
        int conformes = 0;
        int ecarts = 0;
        for (LigneCollationnement l : lignes) {
            Item item = l.getItem();
            BigDecimal theo = nz(l.getQuantiteTheorique());
            BigDecimal phys = l.getQuantitePhysiqueConstatee();
            BigDecimal ecart = l.getEcart();

            if (ecart != null && ecart.compareTo(BigDecimal.ZERO) != 0) {
                ecarts++;
            } else if (phys != null) {
                conformes++;
            }

            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("rang", rang++);
            lm.put("nomenclature", item != null ? item.getNomenclature() : "—");
            lm.put("designation", item != null ? ns(item.getDesignation()) : "—");
            lm.put("quantiteTheorique", num(theo));
            lm.put("quantitePhysique", phys != null ? num(phys) : "Non compté");
            lm.put("ecart", ecart != null ? (ecart.compareTo(BigDecimal.ZERO) > 0 ? "+" + num(ecart) : num(ecart)) : "—");
            lm.put("observations", ns(l.getObservations(), "—"));
            lignesModel.add(lm);
        }

        m.put("lignes", lignesModel);
        m.put("totalArticles", lignes.size());
        m.put("totalConformes", conformes);
        m.put("totalEcarts", ecarts);
        return m;
    }

    public Map<String, Object> fichePrevisions() {
        List<PrevisionDto> alertes = previsionService.calculerAlertes();

        String refNumero = "PREV-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String qrContenu = "PREV:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                + "|ALERTES:" + alertes.size();

        Map<String, Object> m = base("ÉTAT DES PRÉVISIONS DE RÉAPPROVISIONNEMENT",
                "Procédure P01 du CCF — Calcul des Seuils d'Alerte et Besoins Annuels",
                refNumero, LocalDate.now(), qrContenu);

        List<Map<String, Object>> lignesModel = new ArrayList<>();
        int rang = 1;
        for (PrevisionDto a : alertes) {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("rang", rang++);
            lm.put("nomenclature", a.nomenclature());
            lm.put("designation", ns(a.designation()));
            lm.put("stockActuel", num(a.stockActuel()));
            lm.put("stockReserve", num(a.quantiteReservee()));
            lm.put("stockPotentiel", num(a.stockPotentiel()));
            lm.put("seuilAlerte", num(a.seuilAlerte()));
            lm.put("cmoy3", num(a.consommationMoyenneAnnuelle()));
            lignesModel.add(lm);
        }

        m.put("lignes", lignesModel);
        m.put("totalArticles", alertes.size());
        return m;
    }

    // ═══════════════════════════ Helpers ═══════════════════════════

    private Item resoudreItem(String ref) {
        // accepte un UUID ou directement une nomenclature (12 caractères)
        try {
            return itemRepository.findById(UUID.fromString(ref))
                    .orElseThrow(() -> new IntrouvableException("Article " + ref + " introuvable."));
        } catch (IllegalArgumentException notUuid) {
            return itemRepository.findByNomenclature(ref)
                    .orElseThrow(() -> new IntrouvableException("Article de nomenclature " + ref + " introuvable."));
        }
    }

    /** Squelette commun : titre, sous-titre, numéro, date, QR d'en-tête. */
    private Map<String, Object> base(String titre, String sousTitre, String numero, LocalDate dateDoc, String qrContenu) {
        Map<String, Object> m = new HashMap<>();
        m.put("titre", titre);
        m.put("sousTitre", sousTitre);
        m.put("numero", numero);
        m.put("dateDoc", dateDoc != null ? dateDoc.format(D) : LocalDate.now().format(D));
        m.put("qr", barcode.qrCodeDataUri(qrContenu, 220));
        m.put("genereLe", "Généré par GAMMA 3 le " + LocalDateTime.now().format(DT));
        return m;
    }

    private static BigDecimal nz(BigDecimal b) { return b != null ? b : BigDecimal.ZERO; }
    private static String ns(String s) { return s != null && !s.isBlank() ? s : "—"; }
    private static String ns(String s, String def) { return s != null && !s.isBlank() ? s : def; }
    private static String num(BigDecimal b) {
        if (b == null) return "0";
        return b.stripTrailingZeros().toPlainString();
    }
    private static String money(BigDecimal b) {
        return String.format(Locale.US, "%,.3f", nz(b)) + " DT";
    }

    private static String libelleStatutDemande(String s) {
        return switch (s) {
            case "APPROUVE_TOTAL" -> "Approuvée totalement";
            case "APPROUVE_PARTIEL" -> "Approuvée partiellement";
            case "LIVRE" -> "Matériel remis";
            case "SOUMIS" -> "En attente d'arbitrage";
            default -> s;
        };
    }

    private static String libelleDecisionReforme(String s) {
        if (s == null) return "En attente de décision";
        return switch (s) {
            case "REFORME_COMPLETE" -> "Réforme complète (sortie définitive)";
            case "DECLASSEMENT" -> "Déclassement";
            case "REPARATION" -> "Renvoi en réparation";
            case "REJETE" -> "Rejetée";
            default -> s;
        };
    }

    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    static class IntrouvableException extends RuntimeException {
        IntrouvableException(String m) { super(m); }
    }
}
