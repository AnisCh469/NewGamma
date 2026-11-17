package tn.defense.gamma3.document.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DocumentPdfServiceTest {

    @Autowired
    private DocumentPdfService pdfService;

    @Test
    @DisplayName("Génération PDF du PV de Collationnement P07")
    void testRenderPvCollationnement() {
        Map<String, Object> model = new HashMap<>();
        model.put("titre", "PROCÈS-VERBAL DE COLLATIONNEMENT");
        model.put("sousTitre", "Contrôle annuel des dotations du Plan d'Armement — Procédure CCF P07");
        model.put("numero", "PV-COL-0001");
        model.put("dateDoc", "17/11/2026");
        model.put("qr", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        model.put("uniteNom", "Patrouilleur La Galite");
        model.put("uniteCode", "P501");
        model.put("datePlanifiee", "10/11/2026");
        model.put("dateCloture", "17/11/2026");
        model.put("statut", "TERMINEE");
        model.put("conclusionGenerale", "CONFORME");
        model.put("membresCommission", "LV Ben Salah, MT Trabelsi");
        model.put("observationsGenerales", "Contrôle complet sans anomalie.");
        model.put("lignes", List.of(
                Map.of(
                        "rang", 1,
                        "nomenclature", "0100AL000002",
                        "designation", "Filtre à gazole haute pression",
                        "quantiteTheorique", "10",
                        "quantitePhysique", "10",
                        "ecart", "0",
                        "observations", "RAS"
                )
        ));
        model.put("totalArticles", 1);
        model.put("totalConformes", 1);
        model.put("totalEcarts", 0);

        byte[] pdf = pdfService.render("pv-collationnement", model);

        assertNotNull(pdf, "Le PDF généré ne doit pas être null");
        assertTrue(pdf.length > 500, "Le PDF généré doit avoir une taille significative");
        String entete = new String(pdf, 0, Math.min(pdf.length, 5), StandardCharsets.US_ASCII);
        assertTrue(entete.startsWith("%PDF"), "Le fichier généré doit être un PDF valide commençant par %PDF");
    }

    @Test
    @DisplayName("Génération PDF de la Fiche de Prévisions P01")
    void testRenderFichePrevisions() {
        Map<String, Object> model = new HashMap<>();
        model.put("titre", "ÉTAT DES PRÉVISIONS DE RÉAPPROVISIONNEMENT");
        model.put("sousTitre", "Procédure P01 du CCF — Calcul des Seuils d'Alerte et Besoins Annuels");
        model.put("numero", "PREV-20261117");
        model.put("dateDoc", "17/11/2026");
        model.put("qr", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        model.put("totalArticles", 1);
        model.put("lignes", List.of(
                Map.of(
                        "rang", 1,
                        "nomenclature", "0100AL000002",
                        "designation", "Filtre à gazole haute pression",
                        "stockActuel", "2",
                        "stockReserve", "1",
                        "stockPotentiel", "1",
                        "seuilAlerte", "5",
                        "cmoy3", "3"
                )
        ));

        byte[] pdf = pdfService.render("fiche-previsions", model);

        assertNotNull(pdf, "Le PDF généré ne doit pas être null");
        assertTrue(pdf.length > 500, "Le PDF généré doit avoir une taille significative");
        String entete = new String(pdf, 0, Math.min(pdf.length, 5), StandardCharsets.US_ASCII);
        assertTrue(entete.startsWith("%PDF"), "Le fichier généré doit être un PDF valide commençant par %PDF");
    }
}
