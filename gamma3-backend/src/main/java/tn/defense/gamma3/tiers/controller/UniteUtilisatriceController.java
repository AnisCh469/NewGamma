package tn.defense.gamma3.tiers.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.repository.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;

@RestController
@RequestMapping("/api/v1/unites")
@RequiredArgsConstructor
public class UniteUtilisatriceController {

    private final UniteUtilisatriceRepository uniteRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlanArmementRepository planArmementRepository;

    @GetMapping
    public List<UniteUtilisatrice> getAllUnites() {
        List<UniteUtilisatrice> unites = uniteRepository.findAll();
        List<Object[]> counts = planArmementRepository.countPlansGroupByUniteId();
        System.out.println("DEBUG: counts size = " + counts.size());
        java.util.Map<Long, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : counts) {
            if (row[0] != null && row[1] != null) {
                System.out.println("DEBUG: row[0] type = " + row[0].getClass().getName() + ", value = " + row[0] + ", row[1] type = " + row[1].getClass().getName() + ", value = " + row[1]);
                countMap.put((Long) row[0], (Long) row[1]);
            }
        }
        for (UniteUtilisatrice unite : unites) {
            unite.setPlanCount(countMap.getOrDefault(unite.getId(), 0L));
            System.out.println("DEBUG: Unite " + unite.getCode() + " id = " + unite.getId() + " count = " + unite.getPlanCount());
        }
        return unites;
    }

    @GetMapping("/{id}")
    public ResponseEntity<UniteUtilisatrice> getUniteById(@PathVariable Long id) {
        return uniteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<UniteUtilisatrice> getUniteByCode(@PathVariable String code) {
        return uniteRepository.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public UniteUtilisatrice createUnite(@RequestBody UniteUtilisatrice unite) {
        UniteUtilisatrice saved = uniteRepository.save(unite);
        createOrUpdateUserForUnite(saved);
        return saved;
    }

    @PutMapping("/{id}")
    public ResponseEntity<UniteUtilisatrice> updateUnite(@PathVariable Long id, @RequestBody UniteUtilisatrice uniteDetails) {
        return uniteRepository.findById(id)
                .map(unite -> {
                    String oldCode = unite.getCode();
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

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/upload-logo")
    public ResponseEntity<UniteUtilisatrice> uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
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
            
            // Mettre à jour le nom de l'utilisateur associé si jamais
            createOrUpdateUserForUnite(updatedUnite);

            return ResponseEntity.ok(updatedUnite);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/logos/{fileName:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getLogo(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get("uploads").resolve("logos").resolve(fileName);
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                String contentType = "application/octet-stream";
                try {
                    contentType = Files.probeContentType(filePath);
                } catch (Exception e) {}
                
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUnite(@PathVariable Long id) {
        return uniteRepository.findById(id)
                .map(unite -> {
                    // Supprimer le compte utilisateur associé
                    userRepository.findByMatricule(unite.getCode()).ifPresent(userRepository::delete);
                    uniteRepository.delete(unite);
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
