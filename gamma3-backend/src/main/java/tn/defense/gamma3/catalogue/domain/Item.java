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
