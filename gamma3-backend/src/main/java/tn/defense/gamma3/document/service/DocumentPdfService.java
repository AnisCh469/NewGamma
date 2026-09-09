package tn.defense.gamma3.document.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;

/**
 * RÔLE : transformer un modèle HTML/CSS (Thymeleaf, sous {@code resources/templates/documents/})
 * en PDF prêt à imprimer.
 *
 * POURQUOI cette approche plutôt que {@code window.print()} du navigateur (audit 2026-09-09,
 * voir docs/AUDIT_IMPRESSION.md) :
 *   - le rendu est DÉTERMINISTE : même PDF quel que soit le poste, le navigateur, l'imprimante ;
 *   - pas d'en-tête/pied de page parasite ajouté par Chrome ;
 *   - le PDF peut être archivé, ré-imprimé à l'identique, joint à un email ;
 *   - le modèle est un simple fichier HTML+CSS : n'importe quel développeur web l'édite et
 *     le prévisualise dans un navigateur, sans outil spécialisé (contrairement aux .jrxml Jasper).
 *
 * PIPELINE : Thymeleaf produit du HTML5 -> jsoup le normalise en XHTML strict ->
 * openhtmltopdf (moteur CSS 2.1, sans JavaScript) le rend en PDF via PDFBox.
 */
@Service
@RequiredArgsConstructor
public class DocumentPdfService {

    private static final Logger log = LoggerFactory.getLogger(DocumentPdfService.class);

    private final SpringTemplateEngine templateEngine;

    /**
     * @param templateName nom du modèle sous {@code templates/documents/} SANS l'extension
     *                     (ex. {@code "bon-sortie"}, {@code "etiquette-article"})
     * @param variables    variables exposées au modèle (l'objet {@code doc} y est la
     *                     convention pour le contenu principal)
     * @return octets du PDF
     */
    public byte[] render(String templateName, Map<String, Object> variables) {
        // 1. Thymeleaf : modèle + données -> chaîne HTML
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariables(variables);
        String html = templateEngine.process("documents/" + templateName, ctx);

        // 2. jsoup : HTML5 -> XHTML strict (balises auto-fermantes, attributs cités…),
        //    seule forme acceptée par le parseur d'openhtmltopdf.
        Document jsoupDoc = Jsoup.parse(html);
        jsoupDoc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .charset("UTF-8")
                .prettyPrint(false);
        String xhtml = jsoupDoc.html();

        // 3. openhtmltopdf : XHTML + CSS -> PDF
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null); // tout est inline (CSS, images data:) -> pas de base URI
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Échec de génération du PDF pour le modèle '{}'", templateName, e);
            throw new RuntimeException("Impossible de générer le document PDF : " + e.getMessage(), e);
        }
    }
}
