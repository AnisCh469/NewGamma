package tn.defense.gamma3.reforme.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.reforme.domain.DossierReforme;
import tn.defense.gamma3.reforme.domain.LigneReforme;
import tn.defense.gamma3.reforme.repository.DossierReformeRepository;
import tn.defense.gamma3.reforme.repository.LigneReformeRepository;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.domain.Stock;
import tn.defense.gamma3.stock.repository.MagasinRepository;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ReformeServiceIT {

    @Autowired
    private ReformeService reformeService;

    @Autowired
    private UniteUtilisatriceRepository uniteRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private PlanArmementRepository planArmementRepository;

    @Autowired
    private MagasinRepository magasinRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DossierReformeRepository dossierRepository;

    private UniteUtilisatrice testUnite;
    private Item testItem;
    private PlanArmement testPlan;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Seed an admin user if not exists
        testUser = userRepository.findByMatricule("admin")
                .orElseGet(() -> userRepository.save(User.builder()
                        .matricule("admin")
                        .fullName("System Administrator")
                        .password("encoded_pass")
                        .role(Role.ADMIN)
                        .build()));

        // Create test unit
        testUnite = uniteRepository.save(UniteUtilisatrice.builder()
                .code("TUNIT")
                .nom("Test Naval Unit")
                .build());

        // Create test item
        testItem = itemRepository.save(Item.builder()
                .nomenclature("0100MT019999")
                .designation("Test Rifle")
                .prixUnitaire(BigDecimal.valueOf(1200))
                .uniteGestionCode("U")
                .build());

        // Create test plan d'armement dotation
        testPlan = planArmementRepository.save(PlanArmement.builder()
                .unite(testUnite)
                .item(testItem)
                .quantiteType(BigDecimal.valueOf(10))
                .quantiteReelle(BigDecimal.valueOf(5))
                .quantiteVirtuelle(BigDecimal.valueOf(5))
                .build());
    }

    @Test
    void testCreerDossierReforme() {
        List<LigneReforme> lignes = new ArrayList<>();
        lignes.add(LigneReforme.builder()
                .item(testItem)
                .quantite(BigDecimal.valueOf(2))
                .observations("Canon fissuré")
                .build());

        DossierReforme dossier = reformeService.creerDossier(testUnite.getId(), "Test defect", lignes);

        assertNotNull(dossier);
        assertNotNull(dossier.getId());
        assertEquals("SOUMIS", dossier.getStatut());
        assertEquals("Test defect", dossier.getMotif());
        assertEquals(testUnite.getCode(), dossier.getUnite().getCode());
    }

    @Test
    void testTraiterDossierCommissionDeclassement() {
        // 1. Create a dossier
        List<LigneReforme> lignes = new ArrayList<>();
        lignes.add(LigneReforme.builder()
                .item(testItem)
                .quantite(BigDecimal.valueOf(2))
                .observations("Canon fissuré")
                .build());

        DossierReforme dossier = reformeService.creerDossier(testUnite.getId(), "Test defect", lignes);

        // 2. Traiter en commission (Décision: DECLASSEMENT)
        DossierReforme dossierTraite = reformeService.traiterDossierCommission(
                dossier.getId(),
                "DECLASSEMENT",
                "CF Mnasri, LV Nouri",
                "Approuve declassement",
                LocalDate.now(),
                "admin"
        );

        assertNotNull(dossierTraite);
        assertEquals("TRAITE", dossierTraite.getStatut());
        assertEquals("DECLASSEMENT", dossierTraite.getDecisionCommission());

        // 3. Verify Plan d'Armement update (reelle should be 5 - 2 = 3)
        PlanArmement planApres = planArmementRepository.findByUniteIdAndItemId(testUnite.getId(), testItem.getId()).orElseThrow();
        assertEquals(0, planApres.getQuantiteReelle().compareTo(BigDecimal.valueOf(3)));

        // 4. Verify Stock SRR update (should have 2 items)
        Magasin srr = magasinRepository.findByCode("SRR").orElseThrow();
        Stock stockSrr = stockRepository.findByItem_IdAndMagasin_Id(testItem.getId(), srr.getId()).orElseThrow();
        assertEquals(0, stockSrr.getQuantite().compareTo(BigDecimal.valueOf(2)));
    }
}
