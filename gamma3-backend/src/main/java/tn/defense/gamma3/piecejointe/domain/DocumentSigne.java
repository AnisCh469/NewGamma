package tn.defense.gamma3.piecejointe.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.auth.domain.User;

import java.time.LocalDateTime;

/**
 * Un document officiel SIGNÉ, téléversé dans GAMMA 3 comme pièce justificative et rattaché
 * à l'enregistrement qui l'a produit (une demande, une réception, un dossier de réforme…).
 *
 * Cycle : (1) l'utilisateur génère le PDF officiel depuis GAMMA 3, (2) il l'imprime et le
 * fait signer/cacheter, (3) il scanne le document signé et le téléverse ici. Le scan devient
 * une référence permanente, consultable depuis l'écran de l'enregistrement d'origine.
 *
 * Le rattachement est volontairement générique — {@code (typeDocument, referenceId)} —
 * plutôt qu'une clé étrangère par type : {@code referenceId} est une chaîne car elle peut
 * porter un identifiant numérique (demande, réception…), un UUID ou une nomenclature (article).
 */
@Entity
@Table(name = "documents_signes", indexes = {
        @Index(name = "idx_docsigne_ref", columnList = "type_document, reference_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false, length = 30)
    private TypeDocumentSigne typeDocument;

    @Column(name = "reference_id", nullable = false, length = 60)
    private String referenceId;

    /** Libellé lisible de l'enregistrement rattaché (ex. « BS-DEM-2026-6789 »), pour l'affichage. */
    @Column(name = "reference_libelle", length = 120)
    private String referenceLibelle;

    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    /** Chemin RELATIF sur le disque du serveur (sous {@code uploads/}). Jamais exposé au client. */
    @JsonIgnore
    @Column(name = "chemin_stockage", nullable = false, length = 400)
    private String cheminStockage;

    @Column(nullable = false)
    private Long taille;

    @Column(name = "type_mime", length = 100)
    private String typeMime;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "date_upload", nullable = false, updatable = false)
    private LocalDateTime dateUpload;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "uploade_par", nullable = false)
    private User uploadePar;

    @PrePersist
    void onCreate() {
        this.dateUpload = LocalDateTime.now();
    }
}
