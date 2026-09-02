package tn.defense.gamma3.catalogue.api;

/**
 * Fichier : ItemUploadController.java
 * Rôle : expose les endpoints REST permettant (1) de téléverser une photo ou un
 * document technique associé à un article du catalogue, et (2) de servir ces
 * fichiers en lecture (photos publiques, documents techniques publics).
 *
 * Contexte sécurité (Zero-Trust UI, section 1 des instructions système) :
 * Ces endpoints manipulent directement le système de fichiers du serveur à partir
 * d'un nom de fichier fourni par le client (nom de fichier téléversé, ou segment
 * d'URL). Un nom de fichier n'est PAS une donnée de confiance : sans validation,
 * un attaquant peut y injecter des séquences ".." pour sortir du répertoire
 * "uploads/" et lire ou écraser des fichiers arbitraires sur le serveur
 * (vulnérabilité de type Path Traversal / CWE-22). Toutes les méthodes de cette
 * classe qui touchent au système de fichiers appliquent donc une double défense :
 * (a) rejet de tout nom de fichier contenant "..", et (b) vérification finale que
 * le chemin résolu reste bien à l'intérieur du répertoire autorisé.
 */

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.defense.gamma3.catalogue.domain.DangerClass;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.domain.ItemDocument;
import tn.defense.gamma3.catalogue.repository.ItemRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/items")
@CrossOrigin(origins = "http://localhost:4200")
@org.springframework.transaction.annotation.Transactional
public class ItemUploadController {

    private final ItemRepository itemRepository;
    private final String UPLOAD_DIR = "uploads/";

    public ItemUploadController(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @PostMapping("/{id}/upload-photo")
    public ResponseEntity<Item> uploadPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return handleFileUpload(id, file, "photos", "photoUrl");
    }

    @PostMapping("/{id}/upload-doc")
    public ResponseEntity<Item> uploadDocument(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return handleFileUpload(id, file, "documents", "technicalDocUrl");
    }

    @PutMapping("/{id}/danger-class")
    public ResponseEntity<Item> updateDangerClass(@PathVariable UUID id, @RequestBody DangerClassRequest request) {
        return itemRepository.findById(id).map(item -> {
            item.setDangerClass(request.getDangerClass());
            Item updatedItem = itemRepository.save(item);
            return ResponseEntity.ok(updatedItem);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/photos/{fileName:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getPhoto(@PathVariable String fileName) {
        return serveFile("photos", fileName);
    }

    @GetMapping("/documents/{fileName:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getDocument(@PathVariable String fileName) {
        return serveFile("documents", fileName);
    }

    /**
     * Sert un fichier (photo ou document) précédemment téléversé.
     *
     * Pourquoi la vérification "containment" ci-dessous : cet endpoint est
     * volontairement public (permitAll côté SecurityConfig) pour que les photos
     * s'affichent sans authentification dans le catalogue. C'est justement pour
     * cette raison qu'il doit être le plus strict possible sur le nom de fichier
     * reçu : sans le contrôle "filePath.startsWith(baseDir)", une requête comme
     * GET /api/v1/items/documents/..%2f..%2fapplication.yml permettrait de lire
     * n'importe quel fichier lisible par le processus Java.
     *
     * @param subDir   sous-dossier autorisé ("photos" ou "documents")
     * @param fileName nom de fichier fourni par le client (NON fiable)
     * @return le fichier demandé (200), 400 si le nom est invalide/suspect,
     *         404 s'il n'existe pas, 500 en cas d'erreur inattendue.
     */
    private ResponseEntity<org.springframework.core.io.Resource> serveFile(String subDir, String fileName) {
        try {
            if (fileName == null || fileName.isBlank() || fileName.contains("..")) {
                return ResponseEntity.badRequest().build();
            }

            Path baseDir = Paths.get(UPLOAD_DIR, subDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(fileName).normalize();

            // Double vérification : le chemin résolu doit rester strictement DANS baseDir.
            if (!filePath.startsWith(baseDir)) {
                return ResponseEntity.badRequest().build();
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String contentType = "application/octet-stream";
                try {
                    contentType = Files.probeContentType(filePath);
                } catch (Exception e) {
                    // Type MIME non détectable : on retombe sur le type générique ci-dessus.
                }

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Enregistre sur disque un fichier téléversé (photo ou document technique)
     * et met à jour l'article correspondant avec l'URL du fichier.
     *
     * Étapes : 1) charger l'article, 2) valider le nom de fichier d'origine
     * (rejet des séquences ".." et des noms vides), 3) préfixer d'un UUID pour
     * éviter toute collision ou écrasement d'un fichier existant, 4) vérifier
     * que le chemin final reste bien contenu dans le dossier autorisé avant
     * d'écrire quoi que ce soit sur disque, 5) sauvegarder la référence en base.
     *
     * @param id       identifiant de l'article concerné
     * @param file     fichier envoyé par le client (multipart/form-data)
     * @param subDir   sous-dossier de destination ("photos" ou "documents")
     * @param fieldType champ à mettre à jour sur l'article ("photoUrl" ou "technicalDocUrl")
     * @return l'article mis à jour (200), 400 si le fichier/nom est invalide,
     *         404 si l'article n'existe pas, 500 en cas d'erreur d'écriture.
     */
    private ResponseEntity<Item> handleFileUpload(UUID id, MultipartFile file, String subDir, String fieldType) {
        try {
            Item item = itemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            String fileName = StringUtils.cleanPath(originalFilename);
            if (fileName.contains("..")) {
                // Nom de fichier suspect (tentative de traversée de répertoire) : on rejette.
                return ResponseEntity.badRequest().build();
            }

            Path uploadPath = Paths.get(UPLOAD_DIR, subDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            Path filePath = uploadPath.resolve(uniqueFileName).normalize();

            // Double vérification : même après normalisation, le fichier cible doit
            // rester strictement à l'intérieur du dossier d'upload autorisé.
            if (!filePath.startsWith(uploadPath)) {
                return ResponseEntity.badRequest().build();
            }

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/api/v1/items/" + subDir + "/" + uniqueFileName;

            if (fieldType.equals("photoUrl")) {
                item.setPhotoUrl(fileUrl);
            } else if (fieldType.equals("technicalDocUrl")) {
                item.setTechnicalDocUrl(fileUrl);
                ItemDocument doc = new ItemDocument();
                doc.setItem(item);
                doc.setFileName(fileName);
                doc.setFileUrl(fileUrl);
                item.getDocuments().add(doc);
            }

            Item updatedItem = itemRepository.save(item);
            itemRepository.flush();
            return ResponseEntity.ok(updatedItem);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public static class DangerClassRequest {
        private DangerClass dangerClass;
        public DangerClass getDangerClass() { return dangerClass; }
        public void setDangerClass(DangerClass dangerClass) { this.dangerClass = dangerClass; }
    }
}
