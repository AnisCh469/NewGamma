package tn.defense.gamma3.piecejointe.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.piecejointe.domain.DocumentSigne;
import tn.defense.gamma3.piecejointe.domain.TypeDocumentSigne;
import tn.defense.gamma3.piecejointe.service.DocumentSigneService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pièces jointes « documents signés » : on téléverse le scan d'un document officiel une fois
 * signé/cacheté, rattaché à l'enregistrement qui l'a produit. Consultable ensuite depuis
 * l'écran de cet enregistrement (console d'arbitrage, réception, réforme…).
 *
 * Accès : tout utilisateur authentifié peut téléverser et consulter (usage interne à la DA
 * et aux unités). Seul un ADMIN peut supprimer (un document signé est une pièce d'archive).
 */
@RestController
@RequestMapping("/api/v1/documents-signes")
@RequiredArgsConstructor
public class DocumentSigneController {

    private final DocumentSigneService service;

    /** Vue légère renvoyée au client (jamais l'entité brute : elle expose l'objet User complet). */
    public record DocumentSigneDto(
            Long id, TypeDocumentSigne typeDocument, String referenceId, String referenceLibelle,
            String nomFichier, Long taille, String typeMime, String note,
            LocalDateTime dateUpload, String uploadePar) {
        static DocumentSigneDto de(DocumentSigne d) {
            String agent = d.getUploadePar() == null ? "—"
                    : (d.getUploadePar().getFullName() != null && !d.getUploadePar().getFullName().isBlank()
                        ? d.getUploadePar().getFullName() : d.getUploadePar().getMatricule());
            return new DocumentSigneDto(d.getId(), d.getTypeDocument(), d.getReferenceId(), d.getReferenceLibelle(),
                    d.getNomFichier(), d.getTaille(), d.getTypeMime(), d.getNote(), d.getDateUpload(), agent);
        }
    }

    @GetMapping
    public ResponseEntity<?> lister(
            @RequestParam TypeDocumentSigne typeDocument,
            @RequestParam String referenceId,
            Authentication auth) {
        if (user(auth) == null) return ResponseEntity.status(401).body("Authentification requise.");
        List<DocumentSigneDto> docs = service.lister(typeDocument, referenceId).stream()
                .map(DocumentSigneDto::de).toList();
        return ResponseEntity.ok(docs);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> televerser(
            @RequestParam TypeDocumentSigne typeDocument,
            @RequestParam String referenceId,
            @RequestParam(required = false) String referenceLibelle,
            @RequestParam(required = false) String note,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        User u = user(auth);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        try {
            DocumentSigne saved = service.televerser(typeDocument, referenceId, referenceLibelle, note, file, u);
            return ResponseEntity.ok(DocumentSigneDto.de(saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/fichier")
    public ResponseEntity<?> fichier(@PathVariable Long id, Authentication auth) {
        if (user(auth) == null) return ResponseEntity.status(401).body("Authentification requise.");
        try {
            DocumentSigneService.FichierResolu f = service.ouvrir(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(f.typeMime()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename(f.nomFichier()).build().toString())
                    .body(f.resource());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> supprimer(@PathVariable Long id, Authentication auth) {
        User u = user(auth);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        // Supprimer une pièce d'archive est une correction de données : pouvoir SYSTÈME
        // (2026-09-11, docs/PROPOSITION_GOUVERNANCE_ROLES.md) — SUPER_ADMIN, pas ADMIN.
        if (u.getRole() != Role.SUPER_ADMIN) {
            return ResponseEntity.status(403).body("Seul un administrateur système peut supprimer un document signé.");
        }
        try {
            service.supprimer(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    private static User user(Authentication auth) {
        return (auth != null && auth.getPrincipal() instanceof User u) ? u : null;
    }
}
