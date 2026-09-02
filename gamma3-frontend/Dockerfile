# ============================================================================
# Dockerfile - gamma3-frontend
# ============================================================================
# Construit l'application Angular en fichiers statiques optimisés, puis les
# sert via Nginx. Nginx joue aussi le rôle de reverse proxy : toute requête
# "/api/**" est transmise au conteneur backend interne (voir nginx.conf), ce
# qui permet au frontend d'appeler des URLs RELATIVES
# (src/environments/environment.prod.ts -> apiUrl: '') et donc de fonctionner
# depuis n'importe quel poste client de l'intranet, sans connaître à l'avance
# l'adresse IP ou le nom du serveur GAMMA3.
# ============================================================================

# --- Étape 1 : Build ---------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /workspace

# Copier d'abord les manifestes seuls pour profiter du cache Docker : les
# dépendances ne sont réinstallées que si package*.json change.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# --- Étape 2 : Runtime (Nginx) ------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# Angular 17 (builder "application", voir angular.json) place les fichiers
# statiques compilés dans dist/<nom-du-projet>/browser/.
COPY --from=build /workspace/dist/gamma3-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
