package tn.defense.gamma3.piecejointe.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.piecejointe.domain.DocumentSigne;
import tn.defense.gamma3.piecejointe.domain.TypeDocumentSigne;
import tn.defense.gamma3.piecejointe.repository.DocumentSigneRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Stockage et récupération des documents officiels signés (scans) téléversés dans GAMMA 3.
 *
 * SÉCURITÉ (même défense que {@code ItemUploadController}) : le nom de fichier vient du
 * client et n'est PAS de confiance. On rejette toute séquence « .. », on préfixe d'un UUID,
 * et on vérifie que le chemin résolu reste STRICTEMENT sous {@code uploads/documents-signes/}
 * (protection contre la traversée de répertoire, CWE-22). Ces fichiers ne sont JAMAIS servis
 * par le handler statique public {@code /uploads/**} : ils ne sortent que par un endpoint
 * authentifié qui relit le chemin depuis la base.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSigneService {

    private final DocumentSigneRepository repository;

    private static final String SOUS_DOSSIER = "documents-signes";
    private static final long TAILLE_MAX = 15L * 1024 * 1024; // 15 Mo (aligné sur application.yml)
    private static final Set<String> MIME_AUTORISES = Set.of(
            "application/pdf",
            "image/jpeg", "image/jpg", "image/png", "image/tiff", "image/webp");

    @Transactional(readOnly = true)
    public List<DocumentSigne> lister(TypeDocumentSigne type, String referenceId) {
        return repository.findByTypeDocumentAndReferenceIdOrderByDateUploadDesc(type, referenceId);
    }

    @Transactional
    public DocumentSigne televerser(TypeDocumentSigne type, String referenceId, String referenceLibelle,
                                    String note, MultipartFile fichier, User auteur) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier fourni.");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new IllegalArgumentException("Fichier trop volumineux (max 15 Mo).");
        }
        String mime = fichier.getContentType();
        if (mime == null || !MIME_AUTORISES.contains(mime.toLowerCase())) {
            throw new IllegalArgumentException("Format non accepté. Déposez un PDF ou une image (scan du document signé).");
        }
        String nomOriginal = fichier.getOriginalFilename();
        if (nomOriginal == null || nomOriginal.isBlank() || nomOriginal.contains("..")) {
            throw new IllegalArgumentException("Nom de fichier invalide.");
        }
        nomOriginal = Paths.get(nomOriginal).getFileName().toString(); // ne garder que le nom, jamais un chemin

        try {
            Path dossier = Paths.get("uploads", SOUS_DOSSIER).toAbsolutePath().normalize();
            Files.createDirectories(dossier);

            String nomStocke = UUID.randomUUID() + "_" + nomOriginal;
            Path cible = dossier.resolve(nomStocke).normalize();
            if (!cible.startsWith(dossier)) {
                throw new IllegalArgumentException("Nom de fichier invalide.");
            }
            Files.copy(fichier.getInputStream(), cible, StandardCopyOption.REPLACE_EXISTING);

            DocumentSigne doc = DocumentSigne.builder()
                    .typeDocument(type)
                    .referenceId(referenceId)
                    .referenceLibelle(referenceLibelle)
                    .nomFichier(nomOriginal)
                    .cheminStockage("uploads/" + SOUS_DOSSIER + "/" + nomStocke)
                    .taille(fichier.getSize())
                    .typeMime(mime)
                    .note(note != null && !note.isBlank() ? note.trim() : null)
                    .uploadePar(auteur)
                    .build();
            DocumentSigne saved = repository.save(doc);
            log.info("Document signé téléversé : {} ({} o) pour {}#{} par {}",
                    nomOriginal, fichier.getSize(), type, referenceId, auteur.getMatricule());
            return saved;
        } catch (IOException e) {
            throw new RuntimeException("Échec de l'enregistrement du fichier : " + e.getMessage(), e);
        }
    }

    /** Résout le fichier physique d'un document signé pour le renvoyer au client. */
    @Transactional(readOnly = true)
    public FichierResolu ouvrir(Long id) {
        DocumentSigne doc = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable."));
        Path base = Paths.get("uploads", SOUS_DOSSIER).toAbsolutePath().normalize();
        Path chemin = Paths.get(doc.getCheminStockage()).toAbsolutePath().normalize();
        if (!chemin.startsWith(base)) {
            throw new IllegalStateException("Chemin de stockage incohérent.");
        }
        try {
            Resource resource = new UrlResource(chemin.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Le fichier n'est plus disponible sur le serveur.");
            }
            return new FichierResolu(resource, doc.getNomFichier(),
                    doc.getTypeMime() != null ? doc.getTypeMime() : "application/octet-stream");
        } catch (java.net.MalformedURLException e) {
            throw new RuntimeException("Chemin de fichier invalide.", e);
        }
    }

    @Transactional
    public void supprimer(Long id) {
        DocumentSigne doc = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable."));
        try {
            Path base = Paths.get("uploads", SOUS_DOSSIER).toAbsolutePath().normalize();
            Path chemin = Paths.get(doc.getCheminStockage()).toAbsolutePath().normalize();
            if (chemin.startsWith(base)) {
                Files.deleteIfExists(chemin);
            }
        } catch (IOException e) {
            log.warn("Fichier physique non supprimé pour le document signé #{} : {}", id, e.getMessage());
        }
        repository.delete(doc);
    }

    public record FichierResolu(Resource resource, String nomFichier, String typeMime) {}
}
