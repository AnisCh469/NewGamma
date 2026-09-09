package tn.defense.gamma3.document.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.oned.Code128Writer;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

/**
 * RÔLE : produire des codes QR et des codes-barres Code 128 sous forme d'images PNG
 * encodées en <code>data:</code> URI, directement insérables dans un &lt;img&gt; d'un
 * modèle de document.
 *
 * POURQUOI côté serveur : les documents officiels (bons de sortie, étiquettes, fiches…)
 * sont désormais rendus en PDF par le serveur (openhtmltopdf, voir {@link DocumentPdfService}),
 * moteur qui n'exécute aucun JavaScript. La bibliothèque front <code>angularx-qrcode</code>
 * ne peut donc pas y servir : le code QR doit être une image déjà calculée. ZXing
 * (Google) fait ce calcul de façon déterministe et sans dépendance graphique lourde.
 *
 * Un <code>data:</code> URI évite d'exposer un endpoint d'image séparé et garantit que le
 * code voyage avec le PDF (utile pour l'archivage et la ré-impression à l'identique).
 */
@Service
public class BarcodeService {

    /**
     * Code QR carré.
     *
     * @param contenu   texte encodé (ex. "BS:BS-2026-0042|DEM:DEM-2026-5029|UNIT:RPS")
     * @param taillePx  côté de l'image en pixels (200–400 conseillé selon la taille d'impression)
     * @return chaîne "data:image/png;base64,..." à mettre dans un attribut src
     */
    public String qrCodeDataUri(String contenu, int taillePx) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1,
                    EncodeHintType.CHARACTER_SET, "UTF-8"
            );
            BitMatrix matrix = new QRCodeWriter().encode(
                    contenu == null ? "" : contenu, BarcodeFormat.QR_CODE, taillePx, taillePx, hints);
            return toPngDataUri(matrix);
        } catch (Exception e) {
            // Un document ne doit jamais échouer entièrement à cause d'un code illisible :
            // on renvoie un pixel transparent plutôt qu'une exception.
            return PIXEL_TRANSPARENT;
        }
    }

    /**
     * Code-barres linéaire Code 128 (compatible douchettes standard) — typiquement la
     * nomenclature de l'article.
     *
     * @param contenu    texte encodé (nomenclature, code unité…)
     * @param largeurPx  largeur de l'image
     * @param hauteurPx  hauteur de l'image (barres)
     */
    public String code128DataUri(String contenu, int largeurPx, int hauteurPx) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.MARGIN, 4);
            BitMatrix matrix = new Code128Writer().encode(
                    contenu == null ? "" : contenu, BarcodeFormat.CODE_128, largeurPx, hauteurPx, hints);
            return toPngDataUri(matrix);
        } catch (Exception e) {
            return PIXEL_TRANSPARENT;
        }
    }

    private String toPngDataUri(BitMatrix matrix) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /** PNG 1×1 transparent — repli si l'encodage échoue. */
    private static final String PIXEL_TRANSPARENT =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
}
