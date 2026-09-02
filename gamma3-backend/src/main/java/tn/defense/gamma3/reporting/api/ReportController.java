package tn.defense.gamma3.reporting.api;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.reporting.api.dto.*;
import tn.defense.gamma3.reporting.service.ReportingService;
import tn.defense.gamma3.stock.domain.Stock;
import tn.defense.gamma3.stock.domain.MouvementStock;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.stock.repository.MouvementStockRepository;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportingService reportingService;
    private final StockRepository stockRepository;
    private final MouvementStockRepository mouvementRepository;
    private final PlanArmementRepository planRepository;
    private final UniteUtilisatriceRepository uniteRepository;

    @GetMapping("/inventaire")
    public ResponseEntity<byte[]> getInventaireReport(@RequestParam(defaultValue = "pdf") String format) {
        List<Stock> stocks = stockRepository.findAll();
        List<InventaireReportDto> dtoList = stocks.stream()
                .map(s -> InventaireReportDto.builder()
                        .nomenclature(s.getItem().getNomenclature())
                        .designation(s.getItem().getDesignation())
                        .magasinCode(s.getMagasin().getCode())
                        .magasinNom(s.getMagasin().getNom())
                        .quantite(s.getQuantite())
                        .prixUnitaire(s.getItem().getPrixUnitaire())
                        .valeurTotale(s.getQuantite().multiply(s.getItem().getPrixUnitaire()))
                        .build())
                .collect(Collectors.toList());

        BigDecimal grandTotal = dtoList.stream()
                .map(InventaireReportDto::getValeurTotale)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_TITLE", "REGISTRE GÉNÉRAL DE L'INVENTAIRE DES STOCKS");
        parameters.put("REPORT_SUBTITLE", "Bilan comptable valorisé de l'ensemble des soutes et magasins");
        parameters.put("VALEUR_TOTALE_GLOBAL", String.format(Locale.US, "%,.3f", grandTotal));

        byte[] reportBytes;
        String filename = "inventaire_" + LocalDate.now() + "." + format;
        MediaType mediaType = format.equalsIgnoreCase("xlsx") 
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;

        if (format.equalsIgnoreCase("xlsx")) {
            reportBytes = reportingService.exportToXlsx("inventaire", parameters, dtoList);
        } else {
            reportBytes = reportingService.exportToPdf("inventaire", parameters, dtoList);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(reportBytes);
    }

    @GetMapping("/mouvements")
    public ResponseEntity<byte[]> getMouvementsReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "pdf") String format) {

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<MouvementStock> mvtList = mouvementRepository.findByDateMouvementBetweenOrderByDateMouvementDesc(start, end);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<MouvementReportDto> dtoList = mvtList.stream()
                .map(m -> MouvementReportDto.builder()
                        .dateMouvement(m.getDateMouvement().format(dtf))
                        .typeMouvement(m.getTypeMouvement().toString())
                        .magasinNom(m.getMagasin().getNom())
                        .nomenclature(m.getItem().getNomenclature())
                        .designation(m.getItem().getDesignation())
                        .quantite(m.getQuantite())
                        .referenceBon(m.getReferenceBon())
                        .motif(m.getMotif())
                        .build())
                .collect(Collectors.toList());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_TITLE", "FICHE RÉCAPITULATIVE DES MOUVEMENTS DE STOCK");
        parameters.put("REPORT_SUBTITLE", "Période du " + startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) 
                + " au " + endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        byte[] reportBytes;
        String filename = "mouvements_" + startDate + "_to_" + endDate + "." + format;
        MediaType mediaType = format.equalsIgnoreCase("xlsx") 
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;

        if (format.equalsIgnoreCase("xlsx")) {
            reportBytes = reportingService.exportToXlsx("mouvements", parameters, dtoList);
        } else {
            reportBytes = reportingService.exportToPdf("mouvements", parameters, dtoList);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(reportBytes);
    }

    @GetMapping("/consommation/unite/{uniteId}")
    public ResponseEntity<byte[]> getConsommationReport(
            @PathVariable Long uniteId,
            @RequestParam(defaultValue = "pdf") String format) {

        UniteUtilisatrice unite = uniteRepository.findById(uniteId)
                .orElseThrow(() -> new IllegalArgumentException("Unité introuvable"));

        List<PlanArmement> plans = planRepository.findByUniteId(uniteId);
        List<ConsommationReportDto> dtoList = plans.stream()
                .map(p -> ConsommationReportDto.builder()
                        .nomenclature(p.getItem().getNomenclature())
                        .designation(p.getItem().getDesignation())
                        .quantiteType(p.getQuantiteType())
                        .quantiteReelle(p.getQuantiteReelle())
                        .quantiteVirtuelle(p.getQuantiteVirtuelle())
                        .build())
                .collect(Collectors.toList());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_TITLE", "RAPPORT DES DOTATIONS & CONSOMMATION ANNUELLE");
        parameters.put("REPORT_SUBTITLE", "Bilan comparatif entre les dotations types et le stock réel à bord");
        parameters.put("UNITE_NOM", unite.getNom());
        parameters.put("UNITE_CODE", unite.getCode());
        parameters.put("UNITE_BASE", unite.getBaseNavale() != null ? "Base Navale de " + unite.getBaseNavale() : "Non spécifié");

        byte[] reportBytes;
        String filename = "consommation_" + unite.getCode() + "_" + LocalDate.now() + "." + format;
        MediaType mediaType = format.equalsIgnoreCase("xlsx") 
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;

        if (format.equalsIgnoreCase("xlsx")) {
            reportBytes = reportingService.exportToXlsx("consommation_unite", parameters, dtoList);
        } else {
            reportBytes = reportingService.exportToPdf("consommation_unite", parameters, dtoList);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(reportBytes);
    }
}
