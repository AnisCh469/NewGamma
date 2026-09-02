/**
 * Fichier : environment.prod.ts
 * Rôle : configuration d'environnement utilisée pour le build de production
 * ("ng build" / "ng build --configuration production").
 *
 * Pourquoi apiUrl est une chaîne VIDE : en production, le frontend est servi
 * par le conteneur Nginx (voir gamma3-frontend/Dockerfile et
 * gamma3-frontend/nginx.conf), qui redirige (reverse proxy) toute requête
 * "/api/**" vers le service backend Spring Boot à l'intérieur du réseau
 * Docker interne. Le navigateur du client appelle donc une URL RELATIVE
 * (ex: "/api/v1/auth") qui reste valide quel que soit le poste ou l'adresse
 * IP/nom d'hôte du serveur GAMMA3 sur l'intranet — contrairement à une URL
 * absolue "http://localhost:8080" qui ne fonctionnerait que sur le poste du
 * développeur lui-même.
 */
export const environment = {
  production: true,
  apiUrl: ''
};
