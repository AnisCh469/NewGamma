package tn.defense.gamma3.tiers.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.defense.gamma3.tiers.domain.Fournisseur;
import tn.defense.gamma3.tiers.repository.FournisseurRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fournisseurs")
@RequiredArgsConstructor
public class FournisseurController {

    private final FournisseurRepository fournisseurRepository;

    @GetMapping
    public List<Fournisseur> getAllFournisseurs() {
        return fournisseurRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Fournisseur> getFournisseurById(@PathVariable Long id) {
        return fournisseurRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Fournisseur createFournisseur(@RequestBody Fournisseur fournisseur) {
        return fournisseurRepository.save(fournisseur);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Fournisseur> updateFournisseur(@PathVariable Long id, @RequestBody Fournisseur fournisseurDetails) {
        return fournisseurRepository.findById(id)
                .map(fournisseur -> {
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
                    return ResponseEntity.ok(fournisseurRepository.save(fournisseur));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/upload-logo")
    public ResponseEntity<Fournisseur> uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
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
            return ResponseEntity.ok(updatedFournisseur);
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
    public ResponseEntity<Void> deleteFournisseur(@PathVariable Long id) {
        if (fournisseurRepository.existsById(id)) {
            fournisseurRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
