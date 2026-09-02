/**
 * Fichier : environment.ts
 * Rôle : configuration d'environnement utilisée en développement local
 * (ng serve). Angular remplace automatiquement ce fichier par
 * environment.prod.ts lors d'un build de production, grâce au mapping
 * "fileReplacements" déclaré dans angular.json (configuration "production").
 *
 * Pourquoi : avant ce fichier, l'URL de l'API backend
 * ("http://localhost:8080") était recopiée en dur dans 17 fichiers TypeScript
 * différents. Résultat : impossible de déployer le frontend sur une autre
 * machine que le poste du développeur sans modifier 17 fichiers à la main.
 * Centraliser l'URL ici permet de la changer à un seul endroit par
 * environnement (dev/prod), conformément aux contraintes de déploiement
 * intranet de la Marine (plusieurs postes clients, un seul serveur central).
 */
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080'
};
