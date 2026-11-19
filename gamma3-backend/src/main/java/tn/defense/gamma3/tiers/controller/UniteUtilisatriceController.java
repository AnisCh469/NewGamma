package tn.defense.gamma3.tiers.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.service.JournalAuditService;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.repository.UserRepository;
import tn.defense.gamma3.notification.service.BackofficeNotificationService;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * RÔLE : Gestion et consultation du référentiel des Unités Utilisatrices (Clients militaires).
 *
 * RÈGLE DE SÉCURITÉ STRICTE (2026-11-19) :
 * Seul le SUPER_ADMIN peut créer, modifier ou supprimer des unités clientes.
 * L'administrateur logistique (SGS / ADMIN / DA_MANAGER) ne dispose que d'un droit de consultation (lecture seule).
 * Toute modification est consignée dans le Journal d'Audit et notifiée par alerte/email aux responsables backoffice.
 */
@RestController
@RequestMapping("/api/v1/unites")
@RequiredArgsConstructor
public class UniteUtilisatriceController {

    private final UniteUtilisatriceRepository uniteRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlanArmementRepository planArmementRepository;
    private final JournalAuditService journalAuditService;
    private final BackofficeNotificationService backofficeNotificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DA_MANAGER')")
    public List<UniteUtilisatrice> getAllUnites() {
        List<UniteUtilisatrice> unites = uniteRepository.findAll(Sort.by(Sort.Order.asc("code").nullsLast()));
        List<Object[]> counts = planArmementRepository.countPlansGroupByUniteId();
        java.util.Map<Long, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : counts) {
            if (row[0] != null && row[1] != null) {
                countMap.put((Long) row[0], (Long) row[1]);
            }
        }
        for (UniteUtilisatrice unite : unites) {
            unite.setPlanCount(countMap.getOrDefault(unite.getId(), 0L));
        }
        return unites;
    }

    @GetMapping("/{id}")
    public ResponseEntity<UniteUtilisatrice> getUniteById(@PathVariable Long id, Authentication authentication) {
        User user = (authentication != null && authentication.getPrincipal() instanceof User u) ? u : null;
        if (user != null && user.getRole() == Role.UNIT_USER) {
            if (user.getUnite() == null || !user.getUnite().getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return uniteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<UniteUtilisatrice> getUniteByCode(@PathVariable String code, Authentication authentication) {
        User user = (authentication != null && authentication.getPrincipal() instanceof User u) ? u : null;
        if (user != null && user.getRole() == Role.UNIT_USER) {
            if (user.getUnite() == null || !code.equalsIgnoreCase(user.getUnite().getCode())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return uniteRepository.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UniteUtilisatrice createUnite(@RequestBody UniteUtilisatrice unite, Authentication authentication, HttpServletRequest httpRequest) {
        User u = (User) authentication.getPrincipal();
        UniteUtilisatrice saved = uniteRepository.save(unite);
        createOrUpdateUserForUnite(saved);

        String details = "Unité créée: " + saved.getCode() + " - " + saved.getNom() + " (Base: " + (saved.getBaseNavale() != null ? saved.getBaseNavale() : "N/A") + ")";
        journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.UNITE_CREEE, details, httpRequest.getRemoteAddr());
        backofficeNotificationService.notifierModificationBackoffice("Création Unité Utilisatrice", details + " par " + u.getFullName(), saved.getId().toString());

        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UniteUtilisatrice> updateUnite(@PathVariable Long id, @RequestBody UniteUtilisatrice uniteDetails, Authentication authentication, HttpServletRequest httpRequest) {
        User u = (User) authentication.getPrincipal();
        return uniteRepository.findById(id)
                .map(unite -> {
                    String oldCode = unite.getCode();
                    String oldNom = unite.getNom();
                    unite.setCode(uniteDetails.getCode());
                    unite.setNom(uniteDetails.getNom());
                    unite.setBaseNavale(uniteDetails.getBaseNavale());
                    unite.setLogoUrl(uniteDetails.getLogoUrl());
                    UniteUtilisatrice saved = uniteRepository.save(unite);

                    // Si le code a changé, on supprime l'ancien compte utilisateur
                    if (oldCode != null && !oldCode.equals(saved.getCode())) {
                        userRepository.findByMatricule(oldCode).ifPresent(userRepository::delete);
                    }
                    createOrUpdateUserForUnite(saved);

                    String details = "Unité modifiée: " + oldCode + " -> " + saved.getCode() + " (" + oldNom + " -> " + saved.getNom() + ")";
                    journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.UNITE_MODIFIEE, details, httpRequest.getRemoteAddr());
                    backofficeNotificationService.notifierModificationBackoffice("Modification Unité Utilisatrice", details + " par " + u.getFullName(), saved.getId().toString());

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/upload-logo")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UniteUtilisatrice> uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file, Authentication authentication, HttpServletRequest httpRequest) {
        User u = (User) authentication.getPrincipal();
        try {
            UniteUtilisatrice unite = uniteRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Unite not found"));

            Path uploadPath = Paths.get("uploads").resolve("logos");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = StringUtils.cleanPath(file.getOriginalFilename());
            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            Path filePath = uploadPath.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/api/v1/unites/logos/" + uniqueFileName;
            unite.setLogoUrl(fileUrl);

            UniteUtilisatrice updatedUnite = uniteRepository.save(unite);
            createOrUpdateUserForUnite(updatedUnite);

            String details = "Insigne mis à jour pour l'unité: " + unite.getCode() + " (" + fileName + ")";
            journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.UNITE_MODIFIEE, details, httpRequest.getRemoteAddr());
            backofficeNotificationService.notifierModificationBackoffice("Mise à jour Insigne Unité", details + " par " + u.getFullName(), id.toString());

            return ResponseEntity.ok(updatedUnite);
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
    public ResponseEntity<Void> deleteUnite(@PathVariable Long id, Authentication authentication, HttpServletRequest httpRequest) {
        User u = (User) authentication.getPrincipal();
        return uniteRepository.findById(id)
                .map(unite -> {
                    String details = "Unité supprimée: " + unite.getCode() + " - " + unite.getNom();
                    userRepository.findByMatricule(unite.getCode()).ifPresent(userRepository::delete);
                    uniteRepository.delete(unite);

                    journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.UNITE_SUPPRIMEE, details, httpRequest.getRemoteAddr());
                    backofficeNotificationService.notifierModificationBackoffice("Suppression Unité Utilisatrice", details + " par " + u.getFullName(), id.toString());

                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void createOrUpdateUserForUnite(UniteUtilisatrice unite) {
        String code = unite.getCode();
        if (code == null) return;
        userRepository.findByMatricule(code).ifPresentOrElse(
            user -> {
                user.setFullName(unite.getNom());
                userRepository.save(user);
            },
            () -> {
                User unitUser = User.builder()
                        .matricule(code)
                        .fullName(unite.getNom())
                        .password(passwordEncoder.encode(code))
                        .role(Role.UNIT_USER)
                        .build();
                userRepository.save(unitUser);
                System.out.println("✅ Compte client synchronisé pour l'unité " + code);
            }
        );
    }
}
