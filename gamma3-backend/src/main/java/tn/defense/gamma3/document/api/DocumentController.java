package tn.defense.gamma3.document.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.document.service.DocumentDataService;
import tn.defense.gamma3.document.service.DocumentPdfService;

import java.util.Map;

/**
 * RÔLE : point d'entrée unique pour TOUS les documents officiels imprimables de GAMMA 3,
 * générés en PDF côté serveur (voir docs/AUDIT_IMPRESSION.md).
 *
 * Chaque endpoint renvoie un {@code application/pdf} affiché « inline » (le navigateur
 * l'ouvre dans un onglet, l'utilisateur imprime ou enregistre depuis le lecteur PDF).
 *
 * CONVENTION D'ACCÈS :
 *   - Documents logistiques (bon de sortie, bon d'entrée, PV réforme, facture) :
 *     ADMIN / DA_MANAGER, plus l'unité concernée pour SON propre bon de sortie.
 *   - Fiches et étiquettes (article, fournisseur, unité) : tout utilisateur authentifié
 *     (données de référence non sensibles, utiles au marquage physique du matériel).
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentPdfService pdf;
    private final DocumentDataService data;

    // ─────────────────────────── Documents logistiques ───────────────────────────

    @GetMapping("/bon-sortie/{demandeId}")
    public ResponseEntity<byte[]> bonSortie(@PathVariable Long demandeId, Authentication auth) {
        Map<String, Object> model = data.bonSortie(demandeId);
        verifierAccesLogistique(auth, (Long) model.get("uniteId"));
        return pdfInline(pdf.render("bon-sortie", model), (String) model.get("numero"), "bon-sortie");
    }

    @GetMapping("/bon-entree/{bprId}")
    public ResponseEntity<byte[]> bonEntree(@PathVariable Long bprId, Authentication auth) {
        exigerRole(auth, Role.ADMIN, Role.DA_MANAGER);
        Map<String, Object> model = data.bonEntree(bprId);
        return pdfInline(pdf.render("bon-entree", model), (String) model.get("numero"), "bon-entree");
    }

    @GetMapping("/pv-reforme/{dossierId}")
    public ResponseEntity<byte[]> pvReforme(@PathVariable Long dossierId, Authentication auth) {
        Map<String, Object> model = data.pvReforme(dossierId);
        verifierAccesLogistique(auth, (Long) model.get("uniteId"));
        return pdfInline(pdf.render("pv-reforme", model), (String) model.get("numero"), "pv-reforme");
    }

    @GetMapping("/facture/{commandeId}")
    public ResponseEntity<byte[]> facture(@PathVariable Long commandeId, Authentication auth) {
        exigerRole(auth, Role.ADMIN, Role.DA_MANAGER);
        Map<String, Object> model = data.facture(commandeId);
        return pdfInline(pdf.render("facture", model), (String) model.get("numero"), "bon-commande");
    }

    // ─────────────────────────── Fiches ───────────────────────────

    @GetMapping("/fiche-article/{itemId}")
    public ResponseEntity<byte[]> ficheArticle(@PathVariable String itemId, Authentication auth) {
        exigerAuthentifie(auth);
        Map<String, Object> model = data.ficheArticle(itemId);
        return pdfInline(pdf.render("fiche-article", model), (String) model.get("numero"), "fiche-article");
    }

    @GetMapping("/fiche-fournisseur/{fournisseurId}")
    public ResponseEntity<byte[]> ficheFournisseur(@PathVariable Long fournisseurId, Authentication auth) {
        exigerRole(auth, Role.ADMIN, Role.DA_MANAGER);
        Map<String, Object> model = data.ficheFournisseur(fournisseurId);
        return pdfInline(pdf.render("fiche-fournisseur", model), (String) model.get("numero"), "fiche-fournisseur");
    }

    @GetMapping("/fiche-unite/{uniteId}")
    public ResponseEntity<byte[]> ficheUnite(@PathVariable Long uniteId, Authentication auth) {
        Map<String, Object> model = data.ficheUnite(uniteId);
        verifierAccesLogistique(auth, (Long) model.get("uniteId"));
        return pdfInline(pdf.render("fiche-unite", model), (String) model.get("numero"), "fiche-unite");
    }

    // ─────────────────────────── Étiquettes (petit format) ───────────────────────────

    @GetMapping("/etiquette/article/{itemId}")
    public ResponseEntity<byte[]> etiquetteArticle(@PathVariable String itemId, Authentication auth) {
        exigerAuthentifie(auth);
        Map<String, Object> model = data.etiquetteArticle(itemId);
        return pdfInline(pdf.render("etiquette-article", model), (String) model.get("numero"), "etiquette");
    }

    @GetMapping("/etiquette/unite/{uniteId}")
    public ResponseEntity<byte[]> etiquetteUnite(@PathVariable Long uniteId, Authentication auth) {
        exigerAuthentifie(auth);
        Map<String, Object> model = data.etiquetteUnite(uniteId);
        return pdfInline(pdf.render("etiquette-unite", model), (String) model.get("numero"), "etiquette-unite");
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private ResponseEntity<byte[]> pdfInline(byte[] bytes, String numero, String prefixe) {
        String filename = prefixe + "-" + (numero == null ? "document" : numero.replaceAll("[^A-Za-z0-9_-]", "_")) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .body(bytes);
    }

    private static User user(Authentication auth) {
        return (auth != null && auth.getPrincipal() instanceof User u) ? u : null;
    }

    private void exigerAuthentifie(Authentication auth) {
        if (user(auth) == null) throw new AccesRefuseException("Authentification requise.");
    }

    private void exigerRole(Authentication auth, Role... roles) {
        User u = user(auth);
        if (u == null) throw new AccesRefuseException("Authentification requise.");
        for (Role r : roles) if (u.getRole() == r) return;
        throw new AccesRefuseException("Ce document est réservé à la Division de l'Approvisionnement.");
    }

    /** Logistique : ADMIN / DA_MANAGER, ou l'unité elle-même pour un document qui la concerne. */
    private void verifierAccesLogistique(Authentication auth, Long uniteIdDuDocument) {
        User u = user(auth);
        if (u == null) throw new AccesRefuseException("Authentification requise.");
        if (u.getRole() == Role.ADMIN || u.getRole() == Role.DA_MANAGER) return;
        if (u.getRole() == Role.UNIT_USER && u.getUnite() != null
                && u.getUnite().getId().equals(uniteIdDuDocument)) return;
        throw new AccesRefuseException("Vous ne pouvez consulter que les documents de votre unité.");
    }

    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    static class AccesRefuseException extends RuntimeException {
        AccesRefuseException(String m) { super(m); }
    }
}
