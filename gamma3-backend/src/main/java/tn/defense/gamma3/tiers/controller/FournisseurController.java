package tn.defense.gamma3.tiers.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.service.JournalAuditService;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.notification.service.BackofficeNotificationService;
import tn.defense.gamma3.tiers.domain.Fournisseur;
import tn.defense.gamma3.tiers.domain.StatutFournisseur;
import tn.defense.gamma3.tiers.repository.FournisseurRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * RÔLE : Gestion et consultation du référentiel des Fournisseurs de la Marine.
 *
 * RÈGLE DE SÉCURITÉ STRICTE (2026-11-19) :
 * Seul le SUPER_ADMIN peut créer, modifier les informations ou suspendre un fournisseur.
 * L'administrateur logistique (SGS / ADMIN / DA_MANAGER) ne dispose que d'un droit de consultation (lecture seule).
 * Toute modification est consignée dans le Journal d'Audit et fait l'objet d'une alerte/email aux responsables backoffice.
 */
@RestController
@RequestMapping("/api/v1/fournisseurs")
@RequiredArgsConstructor
public class FournisseurController {

    private final FournisseurRepository fournisseurRepository;
    private final JournalAuditService journalAuditService;
    private final BackofficeNotificationService backofficeNotificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DA_MANAGER')")
    public List<Fournisseur> getAllFournisseurs() {
        return fournisseurRepository.findAll(Sort.by(Sort.Order.asc("code").nullsLast()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DA_MANAGER')")
    public ResponseEntity<Fournisseur> getFournisseurById(@PathVariable Long id) {
        return fournisseurRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Fournisseur createFournisseur(@RequestBody Fournisseur fournisseur, Authentication authentication, HttpServletRequest httpRequest) {
        User u = (User) authentication.getPrincipal();
        validerNote(fournisseur.getNote());
        Fournisseur saved = fournisseurRepository.save(fournisseur);

        String details = "Fournisseur créé: " + saved.getCode() + " - " + saved.getNom() + " (Statut: " + saved.getStatut() + ")";
        journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.FOURNISSEUR_CREE, details, httpRequest.getRemoteAddr());
        backofficeNotificationService.notifierModificationBackoffice("Création Fournisseur", details + " par " + u.getFullName(), saved.getId().toString());

        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Fournisseur> updateFournisseur(@PathVariable Long id, @RequestBody Fournisseur fournisseurDetails, Authentication authentication, HttpServletRequest httpRequest) {
        User u = (User) authentication.getPrincipal();
        validerNote(fournisseurDetails.getNote());
        return fournisseurRepository.findById(id)
                .map(fournisseur -> {
                    String oldNom = fournisseur.getNom();
                    Integer oldNote = fournisseur.getNote();
                    StatutFournisseur oldStatut = fournisseur.getStatut();

                    fournisseur.setCode(fournisseurDetails.getCode());
                    fournisseur.setNom(fournisseurDetails.getNom());
                    fournisseur.setMatriculeFiscal(fournisseurDetails.getMatriculeFiscal());
                    fournisseur.setAdresse(fournisseurDetails.getAdresse());
                    fournisseur.setContactNom(fournisseurDetails.getContactNom());
                    fournisseur.setTelephone(fournisseurDetails.getTelephone());
                    fournisseur.setEmail(fournisseurDetails.getEmail());
                    fournisseur.setFax(fournisseurDetails.getFax());
                    fournisseur.setNote(fournisseurDetails.getNote());
                    fournisseur.setLogoUrl(fournisseurDetails.getLogoUrl());
                    if (fournisseurDetails.getStatut() != null) {
                        fournisseur.setStatut(fournisseurDetails.getStatut());
                    }
                    Fournisseur saved = fournisseurRepository.save(fournisseur);

                    String details = "Fournisseur modifié (" + saved.getCode() + ") : " + oldNom + " -> " + saved.getNom()
                            + ", Note: " + oldNote + " -> " + saved.getNote() + ", Statut: " + oldStatut + " -> " + saved.getStatut();
                    journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.FOURNISSEUR_MODIFIE, details, httpRequest.getRemoteAddr());
                    backofficeNotificationService.notifierModificationBackoffice("Modification Fournisseur", details + " par " + u.getFullName(), saved.getId().toString());

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void validerNote(Integer note) {
        if (note != null && (note < 0 || note > 10)) {
            throw new IllegalArgumentException("La note d'évaluation fournisseur doit être comprise entre 0 et 10.");
        }
    }

    @PostMapping("/{id}/upload-logo")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Fournisseur> uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file, Authentication authentication, HttpServletRequest httpRequest) {
        User u = (User) authentication.getPrincipal();
        try {
            Fournisseur fournisseur = fournisseurRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Fournisseur not found"));

            Path uploadPath = Paths.get("uploads").resolve("logos");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = StringUtils.cleanPath(file.getOriginalFilename());
            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            Path filePath = uploadPath.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/api/v1/fournisseurs/logos/" + uniqueFileName;
            fournisseur.setLogoUrl(fileUrl);

            Fournisseur updatedFournisseur = fournisseurRepository.save(fournisseur);

            String details = "Logo mis à jour pour fournisseur: " + fournisseur.getCode() + " (" + fileName + ")";
            journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.FOURNISSEUR_MODIFIE, details, httpRequest.getRemoteAddr());
            backofficeNotificationService.notifierModificationBackoffice("Mise à jour Logo Fournisseur", details + " par " + u.getFullName(), id.toString());

            return ResponseEntity.ok(updatedFournisseur);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/logos/{fileName:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getLogo(@PathVariable String fileName) {
        if (fileName == null || fileName.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Path baseDir = Paths.get("uploads", "logos").toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(fileName).normalize();
            if (!filePath.startsWith(baseDir)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                String contentType = "application/octet-stream";
                try {
                    contentType = Files.probeContentType(filePath);
                } catch (Exception e) {}
                
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Fournisseur> deleteFournisseur(@PathVariable Long id, Authentication authentication, HttpServletRequest httpRequest) {
        User u = (User) authentication.getPrincipal();
        return fournisseurRepository.findById(id)
                .map(fournisseur -> {
                    // RÈGLE MÉTIER & TRAÇABILITÉ MILITAIRE :
                    // La suppression définitive physique d'un fournisseur est interdite afin de préserver
                    // l'historique réglementaire des marchés, commandes et réceptions (BPR).
                    // L'opération bascule le statut en SUSPENDU (désactivation logique).
                    StatutFournisseur nouveauStatut = (fournisseur.getStatut() == StatutFournisseur.SUSPENDU) 
                            ? StatutFournisseur.HOMOLOGUE 
                            : StatutFournisseur.SUSPENDU;
                    fournisseur.setStatut(nouveauStatut);
                    Fournisseur saved = fournisseurRepository.save(fournisseur);

                    TypeActionAudit action = (nouveauStatut == StatutFournisseur.SUSPENDU) ? TypeActionAudit.FOURNISSEUR_SUSPENDU : TypeActionAudit.FOURNISSEUR_REACTIVE;
                    String details = "Fournisseur " + fournisseur.getCode() + " (" + fournisseur.getNom() + ") basculé en statut " + nouveauStatut;
                    journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), action, details, httpRequest.getRemoteAddr());
                    backofficeNotificationService.notifierModificationBackoffice("Changement Statut Fournisseur", details + " par " + u.getFullName(), id.toString());

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
