# 📓 Journal de Développement — Gamma 3

Ce journal répertorie les difficultés techniques rencontrées au cours du développement, leurs causes techniques racines, ainsi que les solutions implémentées, conformément au Protocole Éducatif (Section 4 des instructions globales).

---

## Entrée : 2026-05-30
* **Tâche :** Compaction de la table des articles & Moteur de recherche par nomenclature (12 caractères)
* **Difficulté rencontrée :** Rupture potentielle de la compatibilité descendante avec l'UI existante et le module de Réforme en raison de la suppression des 5 colonnes éclatées (`classe_code`, `sous_classe_code`, etc.). Le code Angular affiche ces fragments séparément dans `item-detail.component.html`.
* **Cause technique racine :** La refonte de la table `items` regroupe toutes les nomenclatures en un champ SQL direct de 12 caractères. Or, le modèle d'UI existant attend des fragments séparés et trie par axes multiples.
* **Solution retenue :** 
  1. *Backend Zero-Regress :* Suppression définitive des 5 colonnes en base PostgreSQL. Ajout de getters virtuels dans `Item.java` (`getClasseCode()`, `getSousClasseCode()`, etc.) calculés sur l'attribut `nomenclature` persistant.
  2. *DTO transparent :* Le record DTO `ItemSummaryDto` continue de sérialiser les 5 propriétés en extrayant dynamiquement des sous-chaînes de la nomenclature compactée.
  3. *Moteur SQL Ultra-Rapide :* Simplification du prédicat Criteria API dans `ItemSpecification.java` pour exécuter une recherche directe sur la colonne indexée `nomenclature` au lieu d'un ensemble de CONCAT SQL coûteux.
  4. *Double-Saisie Premium :* Création d'une zone de recherche dédiée à la nomenclature à 12 caractères avec debouncing de 300ms et indicateur de longueur adaptatif (`X/12`) en vert/orange.

---

## Entrée : 2026-06-01
* **Tâche :** Correction du flux d'arbitrage de retrait physique (Séparation des privilèges Magasinier/Admin et Résolution de la contrainte PostgreSQL)
* **Difficulté rencontrée :** L'administrateur système recevait une erreur (500 Internal Server Error) lors de la confirmation d'un retrait de matériel approuvé (`Confirmer Retrait` pour `DEM-2026-6914`), et le magasin physique centre (`SM1`) n'avait aucune visibilité sur cette demande sur son propre tableau de bord.
* **Cause technique racine :**
  1. *Contrainte check PostgreSQL :* La table `demandes_materiel` avait une contrainte `demandes_materiel_statut_check` héritée qui n'acceptait que `['SOUMIS', 'APPROUVE_PARTIEL', 'APPROUVE_TOTAL', 'REFUSE']`. Elle rejetait donc les mises à jour vers le statut final `LIVRE`.
  2. *Routage statique :* Lors de l'arbitrage d'une demande par l'administrateur, le `magasin_designe_id` de la demande restait positionné sur le magasin géographique initial (`SGS` - `1`) au lieu d'être mis à jour avec le magasin de prélèvement réel choisi lors de l'approbation (`SM1` - `2`). Par conséquent, le magasin désigné ne voyait jamais la demande sur sa console.
  3. *Manque de contrôle Zero-Trust :* L'interface et le service de retrait physique n'imposaient pas la séparation stricte des privilèges : l'administrateur pouvait cliquer sur "Confirmer Retrait" alors que la livraison physique relève exclusivement du gestionnaire de soute (`magasin_centre`).
* **Solution retenue :**
  1. *Migration SQL :* Mise à jour de la contrainte `demandes_materiel_statut_check` en base PostgreSQL pour autoriser les statuts `LIVRE`, `EN_COURS_ARBITRAGE` et `ESCALADE_SGS`.
  2. *Mise à jour d'Arbitrage :* Modification de `DistributionService.arbitrerDemande` pour mettre à jour l'entité `DemandeMateriel` avec la soute réelle de prélèvement (`demande.setMagasinDesigne(newMagasin)`).
  3. *Sécurité des Privilèges :*
     - *Côté Backend :* Restriction de la méthode `confirmerSortiePhysique` pour n'accepter que les utilisateurs ayant le rôle `DA_MANAGER` et étant expressément assignés au magasin physique lié au bon de sortie (`bs.getMagasinId()`).
     - *Côté Frontend :* Injection d'un contrôle réactif (`AuthService`) dans `console-arbitrage.component.html` pour masquer le bouton "Confirmer Retrait" aux utilisateurs `ADMIN` (remplacé par un indicateur statique `EN ATTENTE RETRAIT MAGASIN`) et ne l'exposer qu'aux gestionnaires concernés (`DA_MANAGER`).

---

## Entrée : 2026-08-31
* **Tâche :** Audit de sécurité global du projet et correction de 3 vulnérabilités critiques identifiées (identifiants par défaut, secrets en dur, traversée de répertoire).
* **Difficulté rencontrée :**
  1. Le compte Administrateur par défaut (`admin`/`admin`) était créé automatiquement par `DataSeeder.java` avec un mot de passe littéralement écrit dans le code source, et affiché en clair dans les logs de démarrage.
  2. La clé de signature JWT (`application.security.jwt.secret-key`), le mot de passe PostgreSQL et le mot de passe SQL Server legacy étaient également écrits en dur dans `application.yml` (et dans `docker-compose.prod.yml` pour la base de données).
  3. Les endpoints de téléversement et de lecture de fichiers (`ItemUploadController.handleFileUpload` / `serveFile`, utilisés par `/api/v1/items/{id}/upload-photo`, `/upload-doc`, `/photos/{fileName}`, `/documents/{fileName}`) ne validaient pas le nom de fichier fourni par le client. Les deux derniers endpoints sont publics (`permitAll` dans `SecurityConfig`), donc exploitables sans authentification.
  4. `docker-compose.prod.yml` référençait des fichiers `Dockerfile` dans `gamma3-backend/` et `gamma3-frontend/` qui n'existaient tout simplement pas : le déploiement de production tel quel échouait dès l'étape `docker compose build`.
  5. 17 fichiers TypeScript du frontend appelaient l'API backend via une URL absolue codée en dur (`http://localhost:8080`), rendant impossible tout déploiement sur un poste autre que celui du développeur.
* **Cause technique racine :**
  1. et 2. Absence d'externalisation de la configuration sensible : aucune valeur sensible ne passait par une variable d'environnement, tout était committé en clair dans le code ou les fichiers de configuration versionnés.
  3. Un nom de fichier envoyé par le client (nom de fichier téléversé, ou segment d'URL `{fileName:.+}`) est une donnée non fiable ; sans rejet des séquences `..` ni vérification que le chemin résolu reste contenu dans le dossier `uploads/`, un attaquant peut lire ou écraser des fichiers arbitraires sur le serveur (Path Traversal / CWE-22). Des branches Git préparées en amont (`fix-hardcoded-admin-credentials-*`, `security/fix-hardcoded-secrets-*`, `fix/item-upload-path-traversal-*`) confirmaient que ces failles étaient déjà identifiées mais n'avaient jamais été fusionnées dans la branche de travail principale.
  4. Les `Dockerfile` n'avaient jamais été écrits ; seul `docker-compose.prod.yml` (qui les référence) avait été créé.
  5. Absence d'un mécanisme de configuration d'environnement Angular (`src/environments/`) : l'URL de l'API avait été copiée-collée dans chaque service au fur et à mesure du développement.
* **Solution retenue :**
  1. `DataSeeder.java` lit désormais le matricule et le mot de passe admin via `@Value("${application.default-admin.matricule:admin}")` / `@Value("${application.default-admin.password:admin}")`, surchargeables par les variables d'environnement `ADMIN_MATRICULE` / `ADMIN_PASSWORD`. Le mot de passe n'est plus jamais journalisé.
  2. `application.yml` externalise désormais `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `LEGACY_DB_URL`, `LEGACY_DB_USERNAME`, `LEGACY_DB_PASSWORD`, `JWT_SECRET_KEY`, `ADMIN_MATRICULE`, `ADMIN_PASSWORD` via la syntaxe `${VAR:valeur-par-défaut-actuelle}` — le développement local (`docker-compose up -d` + `./gradlew bootRun`) continue de fonctionner sans configuration supplémentaire, et la production peut tout surcharger via des variables d'environnement fortes (voir `.env.example`, nouvellement créé).
  3. Les deux méthodes de `ItemUploadController` appliquent maintenant une double défense : rejet de tout nom de fichier contenant `".."`, puis vérification que le chemin final normalisé (`Path.normalize()`) reste bien un descendant du dossier autorisé (`Path.startsWith(baseDir)`) avant toute lecture/écriture disque.
  4. Création de `gamma3-backend/Dockerfile` (build multi-étapes Gradle/JDK 21 puis JRE 21, utilisateur non-root) et `gamma3-frontend/Dockerfile` (build Node puis service Nginx avec reverse-proxy `/api/**` vers le backend, voir `gamma3-frontend/nginx.conf`). `docker-compose.prod.yml` a été réécrit pour lire tous les secrets depuis un fichier `.env` non versionné (modèle : `.env.example`) et pour monter un volume Docker persistant pour `uploads/`.
  5. Ajout de `src/environments/environment.ts` (dev) et `environment.prod.ts` (prod, `apiUrl: ''` car Nginx fait le reverse-proxy), câblage du `fileReplacements` correspondant dans `angular.json`, et remplacement des 17 occurrences de `http://localhost:8080` par `${environment.apiUrl}` dans les services et composants concernés. Vérifié par compilation TypeScript (`npx tsc --noEmit`) : aucune erreur introduite.

## [Modèle d'Entrée]
* **Date :** AAAA-MM-JJ
* **Tâche :** Description de la tâche ou de la fonctionnalité
* **Difficulté rencontrée :** Description du problème ou du bug
* **Cause technique racine :** Pourquoi le problème est survenu
* **Solution retenue :** Comment le problème a été résolu (avec détails d'architecture ou de code)

