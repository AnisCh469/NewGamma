package tn.defense.gamma3;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

@SpringBootTest
class Gamma3BackendApplicationTests {

	@Test
	void contextLoads() {
		String legacyDbUrl = "jdbc:sqlserver://localhost:1434;databaseName=Gamma2;encrypt=false";
		String legacyDbUser = "sa";
		String legacyDbPassword = "Gamma3_Password_123";
		
		System.out.println("=== EXPLORATION DE LA BASE DE DONNÉES LEGACY (GAMMA 2) ===");
		try (Connection conn = DriverManager.getConnection(legacyDbUrl, legacyDbUser, legacyDbPassword)) {
			// 1. Lister toutes les tables
			System.out.println("--- LISTE DES TABLES ---");
			try (ResultSet tables = conn.getMetaData().getTables("Gamma2", null, null, new String[]{"TABLE"})) {
				while (tables.next()) {
					System.out.println("Table: " + tables.getString("TABLE_NAME"));
				}
			}
			
			// 2. Pour chaque table intéressante, lister les colonnes
			String[] targetTables = {"Catalogue", "Article", "Magasin", "Stock", "Emplacement", "Rayon", "Item"};
			for (String t : targetTables) {
				System.out.println("--- COLONNES DE LA TABLE " + t.toUpperCase() + " ---");
				try (ResultSet cols = conn.getMetaData().getColumns("Gamma2", null, t, null)) {
					boolean found = false;
					while (cols.next()) {
						found = true;
						System.out.println("  Col: " + cols.getString("COLUMN_NAME") + " (" + cols.getString("TYPE_NAME") + ")");
					}
					if (!found) {
						System.out.println("  [Table non trouvée]");
					}
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

