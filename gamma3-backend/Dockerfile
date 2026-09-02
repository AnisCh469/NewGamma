# ============================================================================
# Dockerfile - gamma3-backend
# ============================================================================
# Construit et exécute le backend Spring Boot de GAMMA 3.0 en un conteneur
# autonome, prêt pour un déploiement intranet (Marine Nationale Tunisienne).
#
# Étape 1 (build)   : compile le projet avec Gradle et le JDK 21, exigé par
#                      build.gradle (java.toolchain.languageVersion = 21).
# Étape 2 (runtime) : copie uniquement le .jar exécutable dans une image JRE
#                      (sans JDK ni Gradle), beaucoup plus légère et avec une
#                      surface d'attaque réduite — bonne pratique de sécurité
#                      pour un système militaire déployé sur intranet cloisonné.
# ============================================================================

# --- Étape 1 : Build ---------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copier d'abord les fichiers de configuration Gradle seuls : Docker met en
# cache cette étape tant qu'ils ne changent pas, ce qui évite de re-télécharger
# toutes les dépendances à chaque modification du code Java (build plus rapide).
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --version

# Copier le reste du code source puis compiler. Les tests ne sont pas exécutés
# ici (-x test) : ils doivent tourner en amont, dans le pipeline CI, pour ne
# pas ralentir/faire échouer la construction de l'image de déploiement.
COPY src ./src
RUN ./gradlew clean bootJar -x test --no-daemon

# --- Étape 2 : Runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Principe de moindre privilège (Zero-Trust, section 1 des instructions
# système) : le processus Java ne doit jamais tourner en tant que root.
RUN addgroup --system gamma3 && adduser --system --ingroup gamma3 gamma3

COPY --from=build /workspace/build/libs/*.jar app.jar

# Le dossier "uploads" (photos/documents des articles) doit survivre aux
# redéploiements : il est monté en volume Docker par docker-compose.prod.yml,
# il n'est donc pas figé dans l'image elle-même.
RUN mkdir -p /app/uploads && chown -R gamma3:gamma3 /app
USER gamma3

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
