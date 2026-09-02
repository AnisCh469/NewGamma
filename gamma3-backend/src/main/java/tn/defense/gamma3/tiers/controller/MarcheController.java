package tn.defense.gamma3.tiers.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.tiers.domain.CommandeFournisseur;
import tn.defense.gamma3.tiers.domain.Marche;
import tn.defense.gamma3.tiers.repository.CommandeFournisseurRepository;
import tn.defense.gamma3.tiers.repository.MarcheRepository;
import tn.defense.gamma3.tiers.repository.FournisseurRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/marches")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class MarcheController {

    private final MarcheRepository marcheRepository;
    private final CommandeFournisseurRepository commandeRepository;
    private final FournisseurRepository fournisseurRepository;

    // --- ENDPOINTS MARCHES ---

    @GetMapping
    public List<Marche> getAllMarches() {
        return marcheRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Marche> getMarcheById(@PathVariable Long id) {
        return marcheRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/fournisseur/{fournisseurId}")
    public List<Marche> getMarchesByFournisseur(@PathVariable Long fournisseurId) {
        return marcheRepository.findByFournisseurId(fournisseurId);
    }

    @PostMapping
    public ResponseEntity<Marche> createMarche(@RequestBody Marche marche) {
        if (marche.getFournisseur() == null || marche.getFournisseur().getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return fournisseurRepository.findById(marche.getFournisseur().getId())
                .map(fournisseur -> {
                    marche.setFournisseur(fournisseur);
                    return ResponseEntity.ok(marcheRepository.save(marche));
                })
                .orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Marche> updateMarche(@PathVariable Long id, @RequestBody Marche marcheDetails) {
        return marcheRepository.findById(id)
                .map(marche -> {
                    marche.setNumeroMarche(marcheDetails.getNumeroMarche());
                    marche.setDesignation(marcheDetails.getDesignation());
                    marche.setMontantTotalHt(marcheDetails.getMontantTotalHt());
                    marche.setMontantTotalTtc(marcheDetails.getMontantTotalTtc());
                    marche.setDateDebut(marcheDetails.getDateDebut());
                    marche.setDateFin(marcheDetails.getDateFin());
                    marche.setStatut(marcheDetails.getStatut());
                    return ResponseEntity.ok(marcheRepository.save(marche));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMarche(@PathVariable Long id) {
        return marcheRepository.findById(id)
                .map(marche -> {
                    // Détacher les commandes associées ou les laisser orphelines de marché
                    List<CommandeFournisseur> cmds = commandeRepository.findByMarcheId(id);
                    for (CommandeFournisseur cmd : cmds) {
                        cmd.setMarche(null);
                        commandeRepository.save(cmd);
                    }
                    marcheRepository.delete(marche);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // --- ENDPOINTS COMMANDES FOURNISSEURS ---

    @GetMapping("/commandes")
    public List<CommandeFournisseur> getAllCommandes() {
        return commandeRepository.findAll();
    }

    @GetMapping("/commandes/fournisseur/{fournisseurId}")
    public List<CommandeFournisseur> getCommandesByFournisseur(@PathVariable Long fournisseurId) {
        return commandeRepository.findByFournisseurId(fournisseurId);
    }

    @GetMapping("/commandes/marche/{marcheId}")
    public List<CommandeFournisseur> getCommandesByMarche(@PathVariable Long marcheId) {
        return commandeRepository.findByMarcheId(marcheId);
    }

    @PostMapping("/commandes")
    public ResponseEntity<CommandeFournisseur> createCommande(@RequestBody CommandeFournisseur commande) {
        if (commande.getFournisseur() == null || commande.getFournisseur().getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return fournisseurRepository.findById(commande.getFournisseur().getId())
                .map(fournisseur -> {
                    commande.setFournisseur(fournisseur);
                    if (commande.getMarche() != null && commande.getMarche().getId() != null) {
                        marcheRepository.findById(commande.getMarche().getId())
                                .ifPresent(commande::setMarche);
                    }
                    return ResponseEntity.ok(commandeRepository.save(commande));
                })
                .orElse(ResponseEntity.badRequest().build());
    }

    @DeleteMapping("/commandes/{id}")
    public ResponseEntity<Void> deleteCommande(@PathVariable Long id) {
        return commandeRepository.findById(id)
                .map(commande -> {
                    commandeRepository.delete(commande);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
