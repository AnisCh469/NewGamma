package tn.defense.gamma3.sauvegarde.dto;

import java.time.LocalDateTime;

/**
 * Représentation légère d'un fichier de sauvegarde pour l'écran d'administration —
 * jamais le fichier lui-même (voir {@code SauvegardeService}, aucun endpoint de
 * téléchargement n'est exposé : le fichier reste strictement côté serveur).
 *
 * @param nomFichier    nom du fichier sur le disque serveur (ex : gamma3_20260911_143000.sql)
 * @param tailleOctets  taille du fichier en octets
 * @param dateCreation  date/heure de création du fichier (dernière modification sur disque)
 */
public record SauvegardeDto(String nomFichier, long tailleOctets, LocalDateTime dateCreation) {
}
