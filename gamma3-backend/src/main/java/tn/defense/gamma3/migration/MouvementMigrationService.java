package tn.defense.gamma3.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.repository.UserRepository;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.repository.MagasinRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RÔLE : rapatrier l'HISTORIQUE des mouvements de stock de Gamma 2 (SQL Server) dans la
 * table {@code mouvements_stock} de Gamma 3, pour alimenter la vue « Historique » de la
 * fiche article.
 *
 * SOURCE Gamma 2 :
 *   - {@code dbo.BonMvt}  : en-tête du bon de mouvement (date, type D/R, magasin)
 *   - {@code dbo.LBMvt}   : lignes du bon (nomenclature décomposée, quantité accordée, unité)
 *
 * CORRESPONDANCES :
 *   - article  : nomenclature 12 caractères reconstituée (même règle que
 *                {@link PlanArmementMigrationService#pad}), recherchée dans {@code items} ;
 *                ligne ignorée si l'article n'a pas été migré ;
 *   - magasin  : {@code MDA} (Magasin de la DA) → {@code SGS} ; tout autre → {@code SGS} par
 *                défaut (Gamma 2 ne connaît que 2 codes magasin sur cette table) ;
 *   - type     : {@code D} (Distribution) → {@code SORTIE} ; {@code R} (Remise) → {@code ENTREE} ;
 *   - agent    : compte système {@code SYS-MIGRATION-G2} (désactivé, non connectable).
 *
 * IMPORTANT : ces mouvements sont des ARCHIVES. Ils sont insérés directement dans
 * {@code mouvements_stock} et NE MODIFIENT PAS les niveaux de stock actuels
 * (contrairement à {@code StockService.effectuerMouvement}).
 *
 * IDEMPOTENT : chaque ligne migrée porte une référence préfixée {@code G2-}. Un nouvel appel
 * supprime d'abord toutes les lignes {@code reference_bon LIKE 'G2-%'} puis réinjecte — les
 * mouvements réels de Gamma 3 ne sont jamais touchés.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MouvementMigrationService {

    private final ItemRepository itemRepository;
    private final MagasinRepository magasinRepository;
    private final UserRepository userRepository;
    private final DataSource dataSource; // datasource applicatif (PostgreSQL)

    @Value("${spring.legacy.datasource.url}")
    private String legacyUrl;
    @Value("${spring.legacy.datasource.username}")
    private String legacyUser;
    @Value("${spring.legacy.datasource.password}")
    private String legacyPassword;

    private static final int BATCH = 1000;

    private static final String SRC_SQL =
        "SELECT RTRIM(h.BonMvtCode) AS bon, h.BonMvtDate AS dt, RTRIM(h.TypeBonCode) AS typ, " +
        "       l.ClasseCode AS c, l.SousClasseCode AS sc, l.CategorieCode AS cat, l.SerieCode AS se, l.ItemCode AS it, " +
        "       l.LBMvtQtitItemAcc AS qte, RTRIM(l.UniteCode) AS unite, RTRIM(l.BonMvtMag) AS mag " +
        "FROM dbo.LBMvt l JOIN dbo.BonMvt h ON h.BonMvtCode = l.BonMvtCode " +
        "WHERE l.LBMvtQtitItemAcc > 0 AND h.BonMvtDate BETWEEN '2000-01-01' AND '2026-12-31'";

    private static final String DELETE_SQL = "DELETE FROM mouvements_stock WHERE reference_bon LIKE 'G2-%'";

    private static final String INSERT_SQL =
        "INSERT INTO mouvements_stock (reference_bon, type_mouvement, item_id, magasin_id, quantite, date_mouvement, motif, cree_par) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    public record Resultat(int lus, int inseres, int ignoresArticleInconnu, long dureeMs) {}

    public Resultat runMigration() {
        long t0 = System.currentTimeMillis();
        log.info("Migration de l'historique des mouvements Gamma2 → mouvements_stock : démarrage.");

        // 1. Caches de correspondance
        Map<String, UUID> itemIdParNomenclature = itemRepository.findAll().stream()
                .collect(Collectors.toMap(Item::getNomenclature, Item::getId, (a, b) -> a));
        long magasinSgsId = magasinRepository.findByCode("SGS")
                .map(Magasin::getId)
                .orElseThrow(() -> new IllegalStateException("Magasin SGS introuvable — impossible de migrer les mouvements."));
        long agentId = agentSysteme().getId();
        log.info("{} articles en cache. Magasin cible SGS = {}. Agent = SYS-MIGRATION-G2 (#{}).",
                itemIdParNomenclature.size(), magasinSgsId, agentId);

        int lus = 0, inseres = 0, ignoresItem = 0;

        try (Connection pg = dataSource.getConnection()) {
            pg.setAutoCommit(false);

            // 2. Purge de l'import précédent (idempotence)
            try (Statement del = pg.createStatement()) {
                int supprimes = del.executeUpdate(DELETE_SQL);
                log.info("{} lignes d'un import Gamma2 précédent supprimées.", supprimes);
            }
            pg.commit();

            // 3. Lecture Gamma2 en flux + insertion par lots
            try (Connection sql = DriverManager.getConnection(legacyUrl, legacyUser, legacyPassword);
                 PreparedStatement src = sql.prepareStatement(SRC_SQL,
                         ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                 PreparedStatement ins = pg.prepareStatement(INSERT_SQL)) {

                src.setFetchSize(2000);
                try (ResultSet rs = src.executeQuery()) {
                    int dansLot = 0;
                    while (rs.next()) {
                        lus++;
                        String nomenclature = PlanArmementMigrationService.pad(rs.getString("c"), 2)
                                + PlanArmementMigrationService.pad(rs.getString("sc"), 2)
                                + PlanArmementMigrationService.pad(rs.getString("cat"), 2)
                                + PlanArmementMigrationService.pad(rs.getString("se"), 2)
                                + PlanArmementMigrationService.pad(rs.getString("it"), 4);

                        UUID itemId = itemIdParNomenclature.get(nomenclature);
                        if (itemId == null) { ignoresItem++; continue; }

                        String typ = rs.getString("typ");
                        String typeMouvement = "R".equalsIgnoreCase(typ) ? "ENTREE" : "SORTIE";
                        String unite = rs.getString("unite");
                        String motif = ("R".equalsIgnoreCase(typ) ? "Remise de l'unité " : "Distribution vers l'unité ")
                                + (unite == null || unite.isBlank() ? "?" : unite)
                                + " — archive Gamma 2";

                        java.sql.Date dt = rs.getDate("dt");

                        ins.setString(1, "G2-" + rs.getString("bon"));
                        ins.setString(2, typeMouvement);
                        ins.setObject(3, itemId);
                        ins.setLong(4, magasinSgsId);
                        ins.setBigDecimal(5, rs.getBigDecimal("qte"));
                        ins.setTimestamp(6, dt != null ? new Timestamp(dt.getTime()) : new Timestamp(System.currentTimeMillis()));
                        ins.setString(7, motif);
                        ins.setLong(8, agentId);
                        ins.addBatch();
                        inseres++;

                        if (++dansLot >= BATCH) {
                            ins.executeBatch();
                            pg.commit();
                            dansLot = 0;
                            if (inseres % 20000 == 0) log.info("  … {} mouvements insérés.", inseres);
                        }
                    }
                    if (dansLot > 0) {
                        ins.executeBatch();
                        pg.commit();
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Échec de la migration des mouvements Gamma2", e);
            throw new RuntimeException("Échec de la migration des mouvements : " + e.getMessage(), e);
        }

        long duree = System.currentTimeMillis() - t0;
        log.info("Migration des mouvements terminée : {} lignes lues, {} insérées, {} ignorées (article non migré), en {} ms.",
                lus, inseres, ignoresItem, duree);
        return new Resultat(lus, inseres, ignoresItem, duree);
    }

    /** Compte système porteur des mouvements archivés (créé une seule fois, non connectable). */
    private User agentSysteme() {
        return userRepository.findByMatricule("SYS-MIGRATION-G2").orElseGet(() ->
                userRepository.save(User.builder()
                        .matricule("SYS-MIGRATION-G2")
                        .fullName("Migration Gamma 2 (historique)")
                        .password("!disabled!" + UUID.randomUUID())   // pas un hash bcrypt → connexion impossible
                        .role(Role.SUPER_ADMIN)
                        .enabled(false)                                // bloqué par JwtAuthenticationFilter
                        .build()));
    }
}
