package tn.defense.gamma3.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.repository.UserRepository;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.domain.TypeConsommabilite;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.repository.MagasinRepository;
import tn.defense.gamma3.tiers.domain.Fournisseur;
import tn.defense.gamma3.tiers.repository.FournisseurRepository;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;
import tn.defense.gamma3.tiers.domain.Marche;
import tn.defense.gamma3.tiers.domain.StatutMarche;
import tn.defense.gamma3.tiers.repository.MarcheRepository;
import tn.defense.gamma3.tiers.domain.CommandeFournisseur;
import tn.defense.gamma3.tiers.domain.StatutCommandeFournisseur;
import tn.defense.gamma3.tiers.repository.CommandeFournisseurRepository;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.reception.domain.BonProvisoireReception;
import tn.defense.gamma3.reception.domain.LigneReception;
import tn.defense.gamma3.reception.domain.StatutBPR;
import tn.defense.gamma3.reception.repository.BonProvisoireReceptionRepository;
import tn.defense.gamma3.reception.repository.LigneReceptionRepository;
import tn.defense.gamma3.distribution.domain.DemandeMateriel;
import tn.defense.gamma3.distribution.domain.LigneDemande;
import tn.defense.gamma3.distribution.domain.StatutDemande;
import tn.defense.gamma3.distribution.domain.BonSortie;
import tn.defense.gamma3.distribution.repository.DemandeMaterielRepository;
import tn.defense.gamma3.distribution.repository.LigneDemandeRepository;
import tn.defense.gamma3.distribution.repository.BonSortieRepository;
import tn.defense.gamma3.stock.domain.Stock;
import tn.defense.gamma3.stock.repository.StockRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.time.LocalDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MagasinRepository magasinRepository;
    private final ItemRepository itemRepository;
    private final FournisseurRepository fournisseurRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final PasswordEncoder passwordEncoder;
    private final MarcheRepository marcheRepository;
    private final CommandeFournisseurRepository commandeRepository;
    private final PlanArmementRepository planArmementRepository;
    private final BonProvisoireReceptionRepository bprRepository;
    private final LigneReceptionRepository ligneReceptionRepository;
    private final DemandeMaterielRepository demandeRepository;
    private final LigneDemandeRepository ligneDemandeRepository;
    private final BonSortieRepository bonSortieRepository;
    private final StockRepository stockRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Identifiants du compte Administrateur par défaut, injectés depuis
     * application.yml (clés application.default-admin.matricule / .password).
     *
     * Pourquoi : le matricule et le mot de passe ne doivent JAMAIS être écrits en dur
     * dans le code source (règle Zero-Trust / séparation des privilèges, section 1 des
     * instructions système du projet). En environnement de développement, les valeurs
     * par défaut ci-dessous (admin/admin) restent utilisées automatiquement si aucune
     * variable d'environnement n'est fournie -> aucun changement pour le confort de dev.
     * En production, l'équipe DOIT définir ADMIN_MATRICULE et ADMIN_PASSWORD (variables
     * d'environnement) à des valeurs fortes et uniques avant le premier démarrage.
     */
    @Value("${application.default-admin.matricule:admin}")
    private String adminMatricule;

    @Value("${application.default-admin.password:admin}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        if (uniteRepository.count() == 0) {
            uniteRepository.save(UniteUtilisatrice.builder().code("DMEN").nom("Direction du Matériel et des Équipements Navals").baseNavale("Bizerte").build());
            uniteRepository.save(UniteUtilisatrice.builder().code("DRC").nom("Direction du Ravitaillement des Combustibles").baseNavale("Bizerte").build());
            uniteRepository.save(UniteUtilisatrice.builder().code("RPS").nom("Régiment des Plongeurs de la Spécialité").baseNavale("Bizerte").build());
            uniteRepository.save(UniteUtilisatrice.builder().code("CFISM").nom("Centre de Formation et d'Instruction Spécialisée").baseNavale("Kélibia").build());
            System.out.println("✅ Unités utilisatrices par défaut créées.");
        }

        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .matricule(adminMatricule)
                    .fullName("Administrateur Système")
                    .password(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .build();
            userRepository.save(admin);
            // Sécurité : ne jamais journaliser un mot de passe, même celui d'un compte de démo.
            System.out.println("✅ Administrateur par défaut créé (matricule : " + adminMatricule + "). "
                    + "Définissez ADMIN_MATRICULE et ADMIN_PASSWORD en production.");

            User client = User.builder()
                    .matricule("client")
                    .fullName("Utilisateur Client (Unité)")
                    .password(passwordEncoder.encode("client"))
                    .role(Role.UNIT_USER)
                    .build();
            userRepository.save(client);
            System.out.println("✅ Client par défaut créé : client/client");
        }

        // Création/Mise à jour des comptes de connexion pour les unités principales et de test
        List<String> codesATraiter = List.of("DMEN", "DRC", "RPS", "CFISM", "P202");
        for (String code : codesATraiter) {
            uniteRepository.findByCode(code).ifPresent(unite -> {
                if (userRepository.findByMatricule(code).isEmpty()) {
                    User unitUser = User.builder()
                            .matricule(code)
                            .fullName(unite.getNom())
                            .password(passwordEncoder.encode(code))
                            .role(Role.UNIT_USER)
                            .build();
                    userRepository.save(unitUser);
                    System.out.println("✅ Compte client créé pour l'unité " + code + " (Identifiants : " + code + " / " + code + ")");
                }
            });
        }

        // Création des comptes de test pour les magasins Centre (SM1) et Sud (SM2)
        // RÔLE : Chaque DA_MANAGER est lié à son magasin physique.
        //        magasin_centre → SM1 (ID 2), magasin_sud → SM2 (ID 3)
        if (userRepository.findByMatricule("magasin_centre").isEmpty()) {
            // Charger SM1 — il est créé par le bloc magasin juste après, donc utiliser findByCode ici
            Magasin sm1 = magasinRepository.findByCode("SM1").orElse(null);
            User centre = User.builder()
                    .matricule("magasin_centre")
                    .fullName("Gestionnaire Magasin DA Centre (SM1)")
                    .password(passwordEncoder.encode("magasin_centre"))
                    .role(Role.DA_MANAGER)
                    .magasin(sm1)
                    .build();
            userRepository.save(centre);
            System.out.println("✅ Compte magasinier Centre créé : magasin_centre/magasin_centre (soute: " + (sm1 != null ? sm1.getNom() : "non encore assignée") + ")");
        }
        if (userRepository.findByMatricule("magasin_sud").isEmpty()) {
            Magasin sm2 = magasinRepository.findByCode("SM2").orElse(null);
            User sud = User.builder()
                    .matricule("magasin_sud")
                    .fullName("Gestionnaire Magasin DA Sud (SM2)")
                    .password(passwordEncoder.encode("magasin_sud"))
                    .role(Role.DA_MANAGER)
                    .magasin(sm2)
                    .build();
            userRepository.save(sud);
            System.out.println("✅ Compte magasinier Sud créé : magasin_sud/magasin_sud (soute: " + (sm2 != null ? sm2.getNom() : "non encore assignée") + ")");
        }

        if (magasinRepository.count() == 0) {
            magasinRepository.save(Magasin.builder().code("SGS").nom("Service de Gestion des Stocks").localisation("Bâtiment Central").build());
            magasinRepository.save(Magasin.builder().code("SM1").nom("Magasin DA Centre").localisation("Zone Nord").build());
            magasinRepository.save(Magasin.builder().code("SM2").nom("Magasin DA Sud").localisation("Zone Sud").build());
            magasinRepository.save(Magasin.builder().code("M_MEC").nom("Magasin Mécanique").localisation("Atelier B").build());
            System.out.println("✅ Magasins par défaut créés.");
        }

        if (itemRepository.count() == 0) {
            itemRepository.save(Item.builder()
                    .nomenclature("130514123456")
                    .designation("MUNITION 5.56 MM OTAN")
                    .prixUnitaire(new BigDecimal("1.250"))
                    .stockSecurite(new BigDecimal("5000"))
                    .dangerClass(tn.defense.gamma3.catalogue.domain.DangerClass.EXPLOSIVE)
                    .typeConsommabilite(TypeConsommabilite.CONSOMMABLE)
                    .build());

            itemRepository.save(Item.builder()
                    .nomenclature("280514987654")
                    .designation("FILTRE A HUILE MOTEUR MTU")
                    .prixUnitaire(new BigDecimal("145.500"))
                    .stockSecurite(new BigDecimal("50"))
                    .dangerClass(tn.defense.gamma3.catalogue.domain.DangerClass.NONE)
                    .typeConsommabilite(TypeConsommabilite.CONSOMMABLE)
                    .build());

            itemRepository.save(Item.builder()
                    .nomenclature("911123440001")
                    .designation("CABLE ELECTRIQUE HTA 630MM2")
                    .prixUnitaire(new BigDecimal("89.900"))
                    .stockSecurite(new BigDecimal("200"))
                    .dangerClass(tn.defense.gamma3.catalogue.domain.DangerClass.NONE)
                    .typeConsommabilite(TypeConsommabilite.NON_CONSOMMABLE)
                    .build());
            System.out.println("✅ Articles de test créés dans le catalogue.");
        }

        if (fournisseurRepository.count() == 0) {
            fournisseurRepository.save(Fournisseur.builder().code("F001").nom("SOCIETE TUNISIENNE D'EQUIPEMENT NAVAL").matriculeFiscal("1234567/A").adresse("Zone Portuaire, Bizerte").contactNom("Ahmed M.").telephone("72123456").build());
            fournisseurRepository.save(Fournisseur.builder().code("F002").nom("MTU FRIEDRICHSHAFEN GMBH").matriculeFiscal("INT-DE-987").adresse("Maybachplatz 1, Allemagne").contactNom("Hans G.").telephone("+49 123 456").build());
            System.out.println("✅ Fournisseurs par défaut créés.");
        }

        if (marcheRepository.count() == 0) {
            fournisseurRepository.findByCode("F001").ifPresent(f1 -> {
                marcheRepository.save(Marche.builder()
                        .numeroMarche("M-2026-001")
                        .designation("Fourniture d'Équipements Navals de Secours")
                        .montantTotalHt(new BigDecimal("150000.0000"))
                        .montantTotalTtc(new BigDecimal("178500.0000"))
                        .dateDebut(java.time.LocalDate.now().minusMonths(3))
                        .dateFin(java.time.LocalDate.now().plusMonths(9))
                        .fournisseur(f1)
                        .statut(StatutMarche.ACTIF)
                        .build());
            });
            fournisseurRepository.findByCode("F002").ifPresent(f2 -> {
                Marche m2 = marcheRepository.save(Marche.builder()
                        .numeroMarche("M-2026-002")
                        .designation("Maintenance et Pièces de Rechange Moteurs MTU")
                        .montantTotalHt(new BigDecimal("300000.0000"))
                        .montantTotalTtc(new BigDecimal("357000.0000"))
                        .dateDebut(java.time.LocalDate.now().minusMonths(6))
                        .dateFin(java.time.LocalDate.now().plusMonths(18))
                        .fournisseur(f2)
                        .statut(StatutMarche.ACTIF)
                        .build());

                // Seed orders for F002
                commandeRepository.save(CommandeFournisseur.builder()
                        .numeroCommande("BC-2026-050")
                        .dateCommande(java.time.LocalDate.now().minusMonths(2))
                        .montantTotal(new BigDecimal("45200.0000"))
                        .fournisseur(f2)
                        .marche(m2)
                        .statut(StatutCommandeFournisseur.LIVRE)
                        .build());

                commandeRepository.save(CommandeFournisseur.builder()
                        .numeroCommande("BC-2026-051")
                        .dateCommande(java.time.LocalDate.now().minusDays(10))
                        .montantTotal(new BigDecimal("12000.0000"))
                        .fournisseur(f2)
                        .marche(m2)
                        .statut(StatutCommandeFournisseur.EN_ATTENTE)
                        .build());
            });
            System.out.println("✅ Marchés et bons de commande par défaut créés.");
        }

        if (planArmementRepository.count() == 0) {
            List<Item> items = itemRepository.findAll();
            List<UniteUtilisatrice> unites = uniteRepository.findAll();

            if (!items.isEmpty() && !unites.isEmpty()) {
                for (UniteUtilisatrice unite : unites) {
                    for (int i = 0; i < Math.min(items.size(), 3); i++) {
                        Item item = items.get(i);
                        planArmementRepository.save(PlanArmement.builder()
                                .unite(unite)
                                .item(item)
                                .quantiteType(new BigDecimal(10 * (i + 1)))
                                .quantiteReelle(new BigDecimal(2 * (i + 1)))
                                .quantiteVirtuelle(new BigDecimal(2 * (i + 1)))
                                .build());
                    }
                }
                System.out.println("✅ Plans d'Armement (Dotations) créés par défaut pour toutes les unités.");
            }
        }

        if (bprRepository.count() == 0) {
            fournisseurRepository.findByCode("F002").ifPresent(f2 -> {
                List<Magasin> magasins = magasinRepository.findAll();
                if (!magasins.isEmpty()) {
                    Magasin mag = magasins.get(0);
                    BonProvisoireReception bpr = BonProvisoireReception.builder()
                        .numeroBpr("BPR-2026-001")
                        .dateReception(LocalDate.now())
                        .fournisseur(f2)
                        .magasin(mag)
                        .statut(StatutBPR.ATTENTE_PV)
                        .build();
                    bprRepository.save(bpr);

                    List<Item> allItems = itemRepository.findAll();
                    if (!allItems.isEmpty()) {
                        Item item1 = allItems.get(0);
                        ligneReceptionRepository.save(LigneReception.builder()
                            .bpr(bpr)
                            .item(item1)
                            .quantiteLivree(new BigDecimal("50"))
                            .prixUnitaire(item1.getPrixUnitaire())
                            .build());
                    }
                    System.out.println("✅ BPR de test créé (BPR-2026-001).");
                }
            });
        }

        if (demandeRepository.count() == 0) {
            List<UniteUtilisatrice> unites = uniteRepository.findAll();
            List<Item> items = itemRepository.findAll();
            if (unites.size() >= 3 && items.size() >= 2) {
                // Demande 1 (SOUMIS) pour DMEN
                UniteUtilisatrice dmen = unites.get(0);
                DemandeMateriel d1 = DemandeMateriel.builder()
                        .numeroDemande("DEM-2026-042")
                        .dateDemande(LocalDate.now().minusDays(3))
                        .statut(StatutDemande.SOUMIS)
                        .unite(dmen)
                        .build();
                demandeRepository.save(d1);
                ligneDemandeRepository.save(LigneDemande.builder()
                        .demande(d1)
                        .item(items.get(0))
                        .quantiteDemandee(new BigDecimal("5"))
                        .build());

                // Demande 2 (APPROUVE_TOTAL) pour DRC
                UniteUtilisatrice drc = unites.get(1);
                BonSortie bs1 = BonSortie.builder()
                        .numeroBs("BS-DEM-2026-015")
                        .dateSortie(LocalDate.now().minusDays(10))
                        .transporteur("Service Transit DA")
                        .vehiculeMatricule("MDN-10452")
                        .build();
                bonSortieRepository.save(bs1);

                DemandeMateriel d2 = DemandeMateriel.builder()
                        .numeroDemande("DEM-2026-015")
                        .dateDemande(LocalDate.now().minusDays(12))
                        .statut(StatutDemande.APPROUVE_TOTAL)
                        .unite(drc)
                        .bonSortie(bs1)
                        .build();
                demandeRepository.save(d2);
                ligneDemandeRepository.save(LigneDemande.builder()
                        .demande(d2)
                        .item(items.get(1))
                        .quantiteDemandee(new BigDecimal("2"))
                        .quantiteAccordee(new BigDecimal("2"))
                        .build());

                // Demande 3 (REFUSE) pour RPS
                UniteUtilisatrice rps = unites.get(2);
                DemandeMateriel d3 = DemandeMateriel.builder()
                        .numeroDemande("DEM-2025-894")
                        .dateDemande(LocalDate.now().minusDays(30))
                        .statut(StatutDemande.REFUSE)
                        .motifRefus("Dépassement du quota budgétaire annuel alloué.")
                        .unite(rps)
                        .build();
                demandeRepository.save(d3);
                ligneDemandeRepository.save(LigneDemande.builder()
                        .demande(d3)
                        .item(items.get(0))
                        .quantiteDemandee(new BigDecimal("10"))
                        .build());

                System.out.println("✅ Demandes de matériel de test créées.");
            }
        }

        // Seed des coordonnées logistiques (Rayon) pour les stocks existants si absent
        String[] rayons = {"Rayon A-1", "Rayon A-2", "Rayon B-1", "Rayon B-3", "Rayon C-2", "Rayon D-1"};
        String[] emplacements = {"Étagère 1", "Étagère 2", "Allée Centre", "Étagère 4", "Sol", "Mezzanine"};
        List<Stock> allStocks = stockRepository.findAll();
        boolean stocksUpdated = false;
        for (Stock s : allStocks) {
            if (s.getRayon() == null || s.getRayon().isBlank()) {
                int idx = (int)(s.getId() % rayons.length);
                s.setRayon(rayons[idx]);
                s.setEmplacement(emplacements[idx]);
                stockRepository.save(s);
                stocksUpdated = true;
            }
        }
        if (stocksUpdated) {
            System.out.println("✅ Coordonnées logistiques (Rayon & Emplacement) assignées aux stocks.");
        }

        // Synchronisation de TypeConsommabilite depuis la table legacy.item si elle est disponible
        boolean syncedFromLegacy = false;
        try {
            // Vérifier si la table legacy.item existe
            jdbcTemplate.execute("SELECT 1 FROM legacy.item LIMIT 1");
            
            // Exécuter la mise à jour SQL native optimisée
            int updated = jdbcTemplate.update(
                "UPDATE items i " +
                "SET type_consommabilite = CASE WHEN trim(l.consommabilitecode) = 'C' THEN 'CONSOMMABLE' ELSE 'NON_CONSOMMABLE' END " +
                "FROM legacy.item l " +
                "WHERE i.nomenclature = (trim(l.classecode::text) || trim(l.sousclassecode) || trim(l.categoriecode) || trim(l.seriecode) || trim(l.itemcode))"
            );
            System.out.println("✅ Synchronisation réussie de type_consommabilite pour " + updated + " articles depuis la table legacy.item.");
            syncedFromLegacy = true;
        } catch (Exception e) {
            System.out.println("⚠️ Impossible de synchroniser type_consommabilite depuis legacy.item : " + e.getMessage() + ". Utilisation de l'heuristique par défaut.");
        }

        if (!syncedFromLegacy) {
            // Seed heuristique de TypeConsommabilite en fallback
            java.util.Set<String> consommableKeywords = java.util.Set.of(
                "huile", "peinture", "graisse", "filtre", "joint", "lubrif", "solvant",
                "carburant", "fuel", "gazole", "alcool", "antirouille", "vernis", "colle",
                "seal", "gasket", "chiffon", "papier", "emballage", "pile",
                "batterie", "ampoule", "lampe", "munition", "cartouche", "bougie"
            );
            
            // Enlever la valeur par défaut pour forcer la mise à jour des éléments non définis
            List<Item> itemsSansType = itemRepository.findAll().stream()
                    .filter(i -> i.getTypeConsommabilite() == null)
                    .toList();
            int updatedCount = 0;
            for (Item item : itemsSansType) {
                String desig = item.getDesignation() != null ? item.getDesignation().toLowerCase() : "";
                boolean isConsommable = consommableKeywords.stream().anyMatch(desig::contains);
                item.setTypeConsommabilite(isConsommable ? TypeConsommabilite.CONSOMMABLE : TypeConsommabilite.NON_CONSOMMABLE);
                itemRepository.save(item);
                updatedCount++;
            }
            if (updatedCount > 0) {
                System.out.println("✅ TypeConsommabilite assigné à " + updatedCount + " articles par heuristique de fallback.");
            }
        }

        // Peuplement automatique de 200 articles dans les magasins Centre (SM1) et Sud (SM2)
        Magasin sm1 = magasinRepository.findByCode("SM1").orElse(null);
        Magasin sm2 = magasinRepository.findByCode("SM2").orElse(null);
        if (sm1 != null && sm2 != null) {
            long countSm1 = stockRepository.countByMagasin_Id(sm1.getId());
            long countSm2 = stockRepository.countByMagasin_Id(sm2.getId());
            if (countSm1 == 0 || countSm2 == 0) {
                List<Item> allItems = itemRepository.findAll();
                int itemsToSeed = Math.min(allItems.size(), 200);
                for (int i = 0; i < itemsToSeed; i++) {
                    Item item = allItems.get(i);
                    if (countSm1 == 0) {
                        stockRepository.save(Stock.builder()
                                .item(item)
                                .magasin(sm1)
                                .quantite(new BigDecimal(10 + (i % 20)))
                                .rayon("Rayon C-" + (1 + (i % 10)))
                                .emplacement("Étagère " + (1 + (i % 5)))
                                .build());
                    }
                    if (countSm2 == 0) {
                        stockRepository.save(Stock.builder()
                                .item(item)
                                .magasin(sm2)
                                .quantite(new BigDecimal(15 + (i % 15)))
                                .rayon("Rayon S-" + (1 + (i % 10)))
                                .emplacement("Étagère " + (1 + (i % 5)))
                                .build());
                    }
                }
                System.out.println("✅ Seeding automatique de " + itemsToSeed + " stocks dans SM1 et SM2 réalisé.");
            }
        }
    }
}
