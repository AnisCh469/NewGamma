package tn.defense.gamma3.catalogue.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a physical Item in the inventory.
 * Maps to the legacy "Item" table from Gamma 2.
 */
@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Nomenclature compactée (Gamma 3) - Compaction à 12 caractères
    @Column(name = "nomenclature", length = 12, nullable = false, unique = true)
    private String nomenclature;

    public String getClasseCode() {
        return (nomenclature != null && nomenclature.length() >= 2) ? nomenclature.substring(0, 2) : "";
    }

    public String getSousClasseCode() {
        return (nomenclature != null && nomenclature.length() >= 4) ? nomenclature.substring(2, 4) : "";
    }

    public String getCategorieCode() {
        return (nomenclature != null && nomenclature.length() >= 6) ? nomenclature.substring(4, 6) : "";
    }

    public String getSerieCode() {
        return (nomenclature != null && nomenclature.length() >= 8) ? nomenclature.substring(6, 8) : "";
    }

    public String getItemCode() {
        return (nomenclature != null && nomenclature.length() >= 12) ? nomenclature.substring(8, 12) : "";
    }

    @Column(name = "designation", length = 254)
    private String designation;

    @Column(name = "prix_unitaire", precision = 18, scale = 3)
    private BigDecimal prixUnitaire;

    @Column(name = "stock_securite", precision = 10, scale = 2)
    private BigDecimal stockSecurite;

    @Column(name = "unite_gestion_code", length = 2)
    private String uniteGestionCode;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "technical_doc_url")
    private String technicalDocUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "danger_class")
    private DangerClass dangerClass;

    /**
     * Catégorie de consommabilité : CONSOMMABLE (peinture, graisse...) ou NON_CONSOMMABLE (équipements, pièces).
     * Les articles NON_CONSOMMABLE nécessitent un suivi de cycle de vie et peuvent être réformés.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_consommabilite", nullable = false, length = 20, columnDefinition = "varchar(20) default 'CONSOMMABLE'")
    @Builder.Default
    private TypeConsommabilite typeConsommabilite = TypeConsommabilite.CONSOMMABLE;

    // ═══════════════════════════════════════════════════════════════════════════════════
    // FICHE SIGNALÉTIQUE D'ARTICLE (FSA) — champs ajoutés le 2026-09-11 suite à l'audit de
    // conformité CCF (docs/AUDIT_CONFORMITE_CCF_2026-09-11.md §4), sur demande explicite de
    // l'utilisateur de couvrir l'intégralité du modèle décrit dans le document CCF
    // "4 INSTRUCTIONS DE CREATION MODIFICATION FT" (§2-3, modèle de FSA, et §2-4, tableaux
    // d'attributs). Regroupement fidèle aux 5 sections du formulaire papier d'origine.
    // Tous ces champs sont NULLABLES : le CCF lui-même décrit une FSA complétée
    // PROGRESSIVEMENT par plusieurs acteurs au fil du cycle de vie de l'article (demandeur →
    // section comptable → gestionnaire des stocks), jamais entièrement en une seule saisie.
    // ═══════════════════════════════════════════════════════════════════════════════════

    // ─── Données de base ────────────────────────────────────────────────────────────────
    @Column(name = "poids", precision = 12, scale = 3)
    private BigDecimal poids; // kg

    @Column(name = "volume", precision = 12, scale = 4)
    private BigDecimal volume; // m³

    @Column(name = "norme_iso", length = 100)
    private String normeIso;

    @Column(name = "echelon_maintenance", length = 100)
    private String echelonMaintenance;

    /** Ex : "Xx0000" (pas de conditionnement spécial) ou "Fs(0000)" (voir fiche de sécurité
     *  référencée) — voir CCF §2-4-7. Champ libre : le CCF ne fixe pas de liste fermée. */
    @Column(name = "code_conditionnement", length = 50)
    private String codeConditionnement;

    // ─── Données de classification ──────────────────────────────────────────────────────
    /** Statut Critique/Non-Critique (Cr/NCr) — voir la javadoc de {@link Criticite} pour son
     *  lien avec le statut "Essentiel" de la Procédure P01 du CCF. */
    @Enumerated(EnumType.STRING)
    @Column(name = "criticite", length = 20)
    private Criticite criticite;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_type_materiel", length = 5)
    private CodeTypeMateriel codeTypeMateriel;

    // ─── Données de planification ───────────────────────────────────────────────────────
    @Column(name = "stock_minimum", precision = 10, scale = 2)
    private BigDecimal stockMinimum;

    @Column(name = "stock_maximum", precision = 10, scale = 2)
    private BigDecimal stockMaximum;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_gestion", length = 5)
    private CodeGestion codeGestion;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_soutien", length = 5)
    private CodeSoutien codeSoutien;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_position_administrative", length = 5)
    private CodePositionAdministrative codePositionAdministrative;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_mode_approvisionnement", length = 5)
    private CodeModeApprovisionnement codeModeApprovisionnement;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_origine_materiel", length = 5)
    private CodeOrigineMateriel codeOrigineMateriel;

    /** Réparable / Non réparable (Rep/NRep). */
    @Enumerated(EnumType.STRING)
    @Column(name = "reparabilite", length = 20)
    private Reparabilite reparabilite;

    /** Délai de réapprovisionnement (n), en années — voir CCF RG7 : 1 an pour un bon de
     *  commande/marché cadre/gré à gré/lettre de commande, 2 ans pour un appel d'offre,
     *  une consultation ou un FMS. Alimente le calcul du seuil d'alerte (Sa), non encore
     *  implémenté (Procédure P01, hors périmètre de cette session — voir
     *  docs/AUDIT_CONFORMITE_CCF_2026-09-11.md). */
    @Column(name = "delai_approvisionnement_annees")
    private Integer delaiApprovisionnementAnnees;

    /** Bloque explicitement toute nouvelle commande de cet article, indépendamment des
     *  seuils — CCF, champ "Blocage Approv" de la FSA. {@code Boolean} (objet, jamais
     *  {@code boolean} primitif) comme tous les autres champs FSA : Jackson désérialise cette
     *  entité via son constructeur "tous arguments" généré par Lombok (détecté par
     *  jackson-module-parameter-names, déjà présent dans Spring Boot) — un champ JSON absent y
     *  est transmis comme {@code null}, ce qu'un {@code boolean} primitif ne peut pas recevoir
     *  ({@code HttpMessageNotReadableException}, découvert en testant cette fonctionnalité).
     *  {@code null} est traité comme "non bloqué" partout où ce champ est lu. */
    @Column(name = "blocage_approvisionnement")
    private Boolean blocageApprovisionnement;

    /** CMA — Consommation Moyenne Annuelle. */
    @Column(name = "cma", precision = 12, scale = 2)
    private BigDecimal cma;

    /** ÉTAT — champ libre de la FSA (le CCF ne définit pas de liste fermée de valeurs pour ce
     *  champ dans le document source). */
    @Column(name = "etat_article", length = 100)
    private String etatArticle;

    // ─── Données de stockage ────────────────────────────────────────────────────────────
    /** Durée de conservation — champ libre (ex : "24 mois", "2 ans") : le CCF ne fixe pas
     *  d'unité normalisée pour ce champ dans le document source. */
    @Column(name = "duree_conservation", length = 50)
    private String dureeConservation;

    // ─── Autres références constructeur ─────────────────────────────────────────────────
    /** NSN — National Stock Number. */
    @Column(name = "nsn", length = 20)
    private String nsn;

    /** OEM — référence du fabricant d'origine (Original Equipment Manufacturer). */
    @Column(name = "oem", length = 100)
    private String oem;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<ItemDocument> documents = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
