package tn.defense.gamma3.reporting.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

@Service
public class ReportingService {

    public byte[] exportToPdf(String templateName, Map<String, Object> parameters, Collection<?> data) {
        try {
            // Charger le fichier jrxml depuis les ressources
            String resourcePath = "/reports/" + templateName + ".jrxml";
            InputStream jrxmlStream = getClass().getResourceAsStream(resourcePath);
            if (jrxmlStream == null) {
                throw new IllegalArgumentException("Modèle JRXML introuvable : " + resourcePath);
            }

            // Compiler le jrxml dynamiquement
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);

            // Remplir le rapport avec la source de données Bean
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(data);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            // Exporter en PDF
            return JasperExportManager.exportReportToPdf(jasperPrint);
        } catch (JRException e) {
            throw new RuntimeException("Erreur de génération du rapport PDF : " + e.getMessage(), e);
        }
    }

    public byte[] exportToXlsx(String templateName, Map<String, Object> parameters, Collection<?> data) {
        try {
            String resourcePath = "/reports/" + templateName + ".jrxml";
            InputStream jrxmlStream = getClass().getResourceAsStream(resourcePath);
            if (jrxmlStream == null) {
                throw new IllegalArgumentException("Modèle JRXML introuvable : " + resourcePath);
            }

            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(data);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JRXlsxExporter exporter = new JRXlsxExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));

            SimpleXlsxReportConfiguration configuration = new SimpleXlsxReportConfiguration();
            configuration.setOnePagePerSheet(false);
            configuration.setDetectCellType(true);
            configuration.setCollapseRowSpan(false);
            configuration.setRemoveEmptySpaceBetweenRows(true);
            configuration.setRemoveEmptySpaceBetweenColumns(true);
            exporter.setConfiguration(configuration);

            exporter.exportReport();
            return baos.toByteArray();
        } catch (JRException e) {
            throw new RuntimeException("Erreur de génération du rapport Excel : " + e.getMessage(), e);
        }
    }
}
