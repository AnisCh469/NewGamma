package tn.defense.gamma3.catalogue.repository;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import tn.defense.gamma3.catalogue.domain.Item;

import java.util.Arrays;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROTOCOLE DE DOCUMENTATION ÉDUCATIVE — Moteur de Recherche Multi-Axes
 * Classe : ItemSpecification
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * JUSTIFICATION 1 : Filtrage côté SERVEUR vs côté CLIENT pour 63 283 articles
 * ─────────────────────────────────────────────────────────────────────────────
 * Deux stratégies de filtrage existent pour une liste paginée :
 *
 * ❌ Option A — Filtrage côté CLIENT (JavaScript / Array.filter()) :
 *    - Nécessite de charger l'intégralité du catalogue en mémoire navigateur.
 *    - 63 283 articles × ~500 octets/article ≈ ~31 Mo de JSON sur le réseau.
 *    - Le moteur JS doit parser et maintenir ce tableau dans le heap (risque
 *      d'OOM sur appareils mobiles ou embarqués).
 *    - La pagination serveur existante devient incompatible : on ne peut pas
 *      filtrer côté client une liste qu'on charge page par page.
 *    → ÉLIMINÉE pour ce volume de données.
 *
 * ✅ Option B — Filtrage côté SERVEUR (SQL via JPA Specification) — RETENUE :
 *    - The frontend sends only the search string (a few bytes).
 *    - The backend generates an optimized SQL WHERE clause via Criteria API.
 *    - Only the current page (e.g., 25 lines) is transmitted over the network.
 *    - Natively compatible with existing server-side pagination and sorting.
 *    - Scalable: works identically for 100 or 1,000,000 items.
 *
 * JUSTIFICATION 2 : Debouncing de 300ms côté Frontend
 * ────────────────────────────────────────────────────
 * Sans debounce, chaque frappe clavier déclenche un appel HTTP/SQL.
 * Pour "SEGMENT MT01" (11 caractères), cela générerait jusqu'à 11 appels.
 * Avec 300ms de debounce : 1 seul appel émis après la dernière frappe.
 * Seuil de 300ms = optimal ergonomiquement (imperceptible, absorbe les rafales).
 *
 * JUSTIFICATION 3 : Les 4 axes de recherche disponibles (modèle Article v1)
 * ──────────────────────────────────────────────────────────────────────────
 * Le modèle Article Gamma 3 prévoit 6 blocs de données. Les champs actuellement
 * persistés en base de données (v1) permettent 4 axes de recherche :
 *
 *   AXE 1 — Nomenclature composée (12 chars, Données de Base)
 *     CONCAT(classeCode, sousClasseCode, categorieCode, serieCode, itemCode)
 *     Permet de chercher par Groupe (ex: "100"), Constructeur (ex: "MT"),
 *     Série (ex: "01"), Item (ex: "0001"), ou toute combinaison.
 *
 *   AXE 2 — Désignation (Données de Base)
 *     Recherche LIKE sur le libellé complet de l'article.
 *
 *   AXE 3 — Unité de Gestion (Données de Base / Tables de référence)
 *     Ex: EA (Each), HD (Hundred), KT (Kit), LB (Pound)...
 *     Permet de retrouver tous les articles gérés en "KT" par exemple.
 *
 *   AXE 4 — Classe Danger ADR (Données de Stockage)
 *     Ex: FLAMMABLE, TOXIC, EXPLOSIVE, CORROSIVE, RADIOACTIVE...
 *     Stockée en base comme chaîne (EnumType.STRING).
 *     Permet de filtrer les matières dangereuses par classe ADR.
 *
 *   ⚠ Axes futurs (blocs 2–6 non encore persistés en base v1) :
 *     NSN, OEM, code ABC, mode approv, position administrative, code soutien...
 *     Seront ajoutés à cette Specification lors de la mise en œuvre des
 *     migrations de schéma correspondantes.
 *
 * JUSTIFICATION 4 : JPA Specification pour la logique multi-token
 * ───────────────────────────────────────────────────────────────
 * Saisie "SEGMENT MT01" → tokens = ["SEGMENT", "MT01"]
 * WHERE
 *   (AXE1 LIKE '%SEGMENT%' OR AXE2 LIKE '%SEGMENT%' OR AXE3 LIKE '%SEGMENT%' OR AXE4 LIKE '%SEGMENT%')
 *   AND
 *   (AXE1 LIKE '%MT01%'    OR AXE2 LIKE '%MT01%'    OR AXE3 LIKE '%MT01%'    OR AXE4 LIKE '%MT01%')
 * Chaque token = AND obligatoire. Pour chaque token, n'importe quel axe suffit (OR).
 * ═══════════════════════════════════════════════════════════════════════════════
 */
public class ItemSpecification {

    /**
     * Construit une Specification JPA pour la recherche multi-axe et multi-token.
     *
     * <p><b>Axes de recherche actifs (modèle Article v1) :</b></p>
     * <ol>
     *   <li>Nomenclature composée : CONCAT(classeCode + sousClasseCode + categorieCode + serieCode + itemCode)</li>
     *   <li>Désignation : libellé textuel complet de l'article</li>
     *   <li>Unité de gestion : code court (EA, KT, LB, HD...)</li>
     *   <li>Classe danger ADR : (FLAMMABLE, TOXIC, EXPLOSIVE...)</li>
     * </ol>
     *
     * <p><b>Logique multi-token :</b> la saisie est découpée par espaces.
     * Chaque token doit matcher sur AU MOINS un axe (OR).
     * Tous les tokens doivent matcher (AND entre eux).</p>
     *
     * @param searchQuery La chaîne saisie par l'utilisateur (peut contenir des espaces).
     * @return Une {@link Specification} combinant les prédicats.
     */
    public static Specification<Item> searchByNomenclatureOrDesignation(String searchQuery) {
        return (root, query, cb) -> {

            // ── 1. Tokenisation ─────────────────────────────────────────────────────────
            List<String> tokens = Arrays.stream(searchQuery.trim().split("\\s+"))
                    .filter(t -> !t.isEmpty())
                    .toList();

            if (tokens.isEmpty()) {
                // Aucun token effectif → pas de restriction → retourner tout
                return cb.conjunction();
            }

            // ── 2. Expression : Nomenclature unique (12 chars) ──────────────────────────
            // Résultat SQL : UPPER(nomenclature)
            Expression<String> nomenclatureExpr = cb.upper(root.<String>get("nomenclature"));

            // ── 3. Expression : Désignation en majuscules ────────────────────────────────
            Expression<String> designationExpr = cb.upper(root.<String>get("designation"));

            // ── 4. Construction des prédicats par token ──────────────────────────────────
            // Pour chaque token : (designation LIKE %TOKEN%) OR (nomenclature LIKE %TOKEN%)
            Predicate[] tokenPredicates = tokens.stream()
                    .map(token -> {
                        String pattern = "%" + token.toUpperCase() + "%";
                        Predicate onDesignation  = cb.like(designationExpr, pattern);
                        Predicate onNomenclature = cb.like(nomenclatureExpr, pattern);
                        return cb.or(onDesignation, onNomenclature);
                    })
                    .toArray(Predicate[]::new);

            // ── 5. Combinaison finale : AND entre tous les tokens ────────────────────────
            // Tous les tokens doivent matcher (au moins un axe chacun).
            return cb.and(tokenPredicates);
        };
    }

    /**
     * Filtre les articles pour ne renvoyer que ceux qui appartiennent au Plan d'Armement 
     * d'une unité spécifique identifiée par son code matricule (ex: DMEN, DRC).
     */
    public static Specification<Item> belongsToPlanArmement(String uniteCode) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<tn.defense.gamma3.tiers.domain.PlanArmement> planRoot = subquery.from(tn.defense.gamma3.tiers.domain.PlanArmement.class);
            subquery.select(planRoot.get("id"));
            subquery.where(
                cb.and(
                    cb.equal(planRoot.get("item"), root),
                    cb.equal(planRoot.get("unite").get("code"), uniteCode)
                )
            );
            return cb.exists(subquery);
        };
    }

    /**
     * Filtre les articles par type de consommabilité.
     */
    public static Specification<Item> hasTypeConsommabilite(tn.defense.gamma3.catalogue.domain.TypeConsommabilite type) {
        return (root, query, cb) -> cb.equal(root.get("typeConsommabilite"), type);
    }

    /**
     * Filtre les articles pour ne renvoyer que ceux qui ont du stock (quantité > 0)
     * dans un magasin spécifique identifié par son identifiant.
     */
    public static Specification<Item> isInMagasin(Long magasinId) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<tn.defense.gamma3.stock.domain.Stock> stockRoot = subquery.from(tn.defense.gamma3.stock.domain.Stock.class);
            subquery.select(stockRoot.get("id"));
            subquery.where(
                cb.and(
                    cb.equal(stockRoot.get("item"), root),
                    cb.equal(stockRoot.get("magasin").get("id"), magasinId),
                    cb.greaterThan(stockRoot.get("quantite"), java.math.BigDecimal.ZERO)
                )
            );
            return cb.exists(subquery);
        };
    }
}
