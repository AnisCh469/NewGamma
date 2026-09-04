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

## Entrée : 2026-09-02
* **Tâche :** Création du dépôt propre `NewGamma` (remplaçant `GAMMA3` bloqué par un historique Git désordonné) et tri des 20 branches distantes de l'ancien dépôt pour en extraire la valeur.
* **Difficulté rencontrée :**
  1. Toute commande Git modifiant l'index (`git add`, `git commit`) échouait avec `Unable to create '.git/index.lock': File exists`, y compris sur un dépôt fraîchement initialisé (donc sans processus concurrent réel).
  2. Une première tentative de copie du projet vers `NewGamma` a été écrite au mauvais endroit : `$HOME/mnt/NewGamma` au lieu de `$HOME/mnt/MyDeveloppement/NewGamma`, créant un dossier local à la session au lieu d'écrire sur le disque réel du poste (`D:\MyDeveloppement\NewGamma`).
  3. Sur les 20 branches distantes de l'ancien dépôt (hors les 3 déjà fusionnées : correctifs de sécurité), la majorité concernait un prototype Python (`gamma3_system/`) totalement abandonné, et une branche (`fix/ui-performance-design-*`) supprimerait la quasi-totalité du code actuel si fusionnée naïvement.
  4. 13 contrôleurs dupliquaient chacun `@CrossOrigin(origins = "http://localhost:4200")` en plus de la configuration CORS globale de `SecurityConfig`.
* **Cause technique racine :**
  1. Le pont (bridge) vers le poste utilisateur ne permettait pas la suppression de fichiers par défaut dans les dossiers connectés (protection anti-suppression) ; Git crée normalement `.git/index.lock` puis le supprime après chaque commande, mais cette suppression échouait silencieusement (`Operation not permitted`), laissant un verrou orphelin qui bloquait la commande suivante.
  2. Un dossier nommé "NewGamma" n'était pas un point de montage réel vers le poste tant que le dossier n'avait pas été créé côté disque réel puis explicitement connecté — écrire dedans depuis la session créait un dossier local à la session, invisible du poste.
  3. Ces branches datent probablement d'un stade antérieur du projet (avant la bascule complète vers Java/Spring Boot + Angular) ou d'exécutions d'outils d'analyse automatique jamais suivies de fusion.
  4. Duplication historique : le CORS a été ajouté contrôleur par contrôleur au fil du développement au lieu d'être centralisé dès le départ dans `SecurityConfig`.
* **Solution retenue :**
  1. Activation explicite de la permission de suppression sur le dossier connecté (`device_request_delete_permission`), qui a immédiatement débloqué toutes les commandes Git.
  2. Détection de l'erreur via `mount` (seul `MyDeveloppement` apparaissait comme montage FUSE réel), copie corrective du contenu déjà présent vers `$HOME/mnt/MyDeveloppement/NewGamma` (le vrai chemin), puis nettoyage du dossier local erroné.
  3. Revue individuelle des 20 branches (`git diff <base> <branche> --stat`) : récupération de 2 fichiers de test réellement utiles et compatibles avec le code actuel (`ApplicationConfigTest.java`, `ItemUploadControllerTest.java` — ce dernier valide précisément le correctif anti path-traversal de la session précédente), rejet explicite et documenté de toutes les autres branches (prototype Python abandonné, ou branche dangereuse à base pré-refonte).
  4. Suppression des 13 annotations `@CrossOrigin` redondantes ; unique source de vérité désormais `SecurityConfig.corsConfigurationSource()`, origine(s) surchargeable(s) via `CORS_ALLOWED_ORIGINS`.

## Entrée : 2026-09-04
* **Tâche :** Premier lancement local du frontend `NewGamma` par l'équipe (test manuel de l'application après le clonage du nouveau dépôt).
* **Difficulté rencontrée :**
  1. `npm start` échouait avec `'ng' is not recognized as an internal or external command`.
  2. Après un `npm install` lancé une première fois de façon interrompue (limite de temps de l'outil d'assistance à distance utilisé), un second `npm install` s'est terminé sans erreur apparente, mais `npm start` échouait ensuite avec `Error [ERR_MODULE_NOT_FOUND]: Cannot find module '.../node_modules/@angular/compiler/fesm2022/compiler.mjs'` et plusieurs erreurs `Could not resolve` sur les polices de `node_modules/primeicons`.
* **Cause technique racine :**
  1. `node_modules/` est exclu du dépôt Git (voir `.gitignore`) — c'est la pratique standard (les dépendances ne doivent jamais être versionnées), mais cela signifie qu'après tout nouveau clonage du dépôt, `npm install` est une étape obligatoire avant de pouvoir exécuter `ng serve` : `ng` (Angular CLI) n'existe que dans `node_modules/.bin/ng`, généré par cette commande.
  2. La première tentative de `npm install`, interrompue avant sa fin, a laissé sur disque des paquets partiellement écrits (fichiers manquants) dans `node_modules/`. Un `npm install` classique ne redétecte pas toujours qu'un dossier de paquet déjà présent est en réalité incomplet et ne le retélécharge donc pas — la corruption est restée après le second `npm install`.
* **Solution retenue :**
  1. Documentation de l'étape `npm install` obligatoire après clonage, ajoutée dans `gamma3-frontend/README.md` (section "Installation", nouvelle).
  2. En cas de `node_modules/` suspecté corrompu (erreurs `Cannot find module` ou `Could not resolve` portant sur des fichiers qui devraient exister), la commande de réparation recommandée est `npm ci` : elle supprime intégralement `node_modules/` avant de réinstaller exactement les versions figées dans `package-lock.json`, garantissant un état propre et reproductible. C'est cette commande qui a résolu l'incident ; `npm start` a ensuite démarré sans erreur (`http://localhost:4200`).

## Entrée : 2026-09-04 (2)
* **Tâche :** Implémentation de la création d'article (bouton "Nouvel Article" du Catalogue), signalé par l'utilisateur comme visuellement présent mais non fonctionnel lors d'un test manuel de l'application.
* **Difficulté rencontrée :** Le bouton `p-button label="Nouvel Article"` existait dans `catalogue.component.html` mais n'avait aucun gestionnaire `(onClick)` : aucun clic ne produisait d'effet. Investigation : aucune boîte de dialogue de création n'existait dans le composant (le seul `p-dialog` présent était celui du panier de demande), `item.service.ts` n'exposait aucune méthode `createItem()`, et le endpoint backend `POST /api/v1/items` existait (`ItemController.createItem`) mais sans aucune validation ni contrôle de rôle.
* **Cause technique racine :** Fonctionnalité commencée côté maquette/backend mais jamais terminée côté interface — un cas classique de développement interrompu où seul le squelette visuel (bouton) a été committé.
* **Solution retenue :**
  1. **Backend** (`ItemController.createItem`) : ajout d'un contrôle de rôle (rejette `UNIT_USER` avec 403, cohérent avec la restriction déjà appliquée côté UI via `isClient()`), validation de la nomenclature (obligatoire, exactement 12 caractères) et de la désignation (obligatoire), et capture de `DataIntegrityViolationException` pour renvoyer une erreur 409 explicite en cas de nomenclature déjà existante (contrainte unique en base) plutôt qu'une erreur 500 brute.
  2. **Frontend** (`item.service.ts`) : ajout de la méthode `createItem(item: Partial<Item>): Observable<Item>` (`POST /api/v1/items`).
  3. **Frontend** (`catalogue.component.ts` / `.html`) : ajout d'un formulaire modal complet (`p-dialog`) reprenant le même patron que le formulaire "Nouveau Fournisseur" déjà fonctionnel (`fournisseurs.component`) — champs nomenclature, désignation, prix unitaire, stock de sécurité, unité de gestion, type de consommabilité et classe de danger GHS (`p-dropdown`) — avec validation client (`isNewItemValid()`) désactivant le bouton "Enregistrer" tant que les champs obligatoires ne sont pas valides, et rafraîchissement automatique du catalogue après création réussie.
  4. Vérifié par compilation TypeScript (`npx tsc --noEmit`) : aucune erreur introduite. La compilation backend n'a pas pu être vérifiée par Gradle dans cet environnement (accès réseau à `services.gradle.org` indisponible depuis le pont distant) ; une relecture manuelle du fichier a été effectuée en suivant strictement les patrons déjà utilisés ailleurs dans `ItemController.java` (`Authentication`/`SecurityContextHolder`/`Role.UNIT_USER`) — **à valider par un redémarrage du backend** (pas de Spring Boot DevTools configuré, donc pas de rechargement à chaud).
  5. Documentation utilisateur ajoutée dans `docs/USER_GUIDE.md` (section "Création d'un Nouvel Article").

## Entrée : 2026-09-04 (3)
* **Tâche :** Enrichissement du formulaire "Nouvel Article" (voir entrée précédente), suite au retour utilisateur : la première version (nomenclature en un seul champ libre à 12 caractères, sans upload) était jugée insuffisante — l'utilisateur a explicitement demandé (1) l'ajout d'une photo et d'un document PDF dès la création, (2) la classe de danger (déjà présente mais visiblement pas assez visible), et surtout (3) la saisie séparée de la Classe, Sous-classe, Catégorie et Série, en s'appuyant sur les valeurs déjà enregistrées en base plutôt qu'un champ libre.
* **Recherche préalable :** Lecture du Cahier des Charges (`docs/GAMMA3 CCF/4 INSTRUCTIONS DE CREATION MODIFICATION FT.docx`), qui documente la règle métier officielle de nomenclature : Classe (1 chiffre, 8 classes fixes 1-8), Sous-classe (2 chiffres, multiples de 5), Catégorie/Constructeur (2 lettres), Série (2 chiffres), Item (4 chiffres) — soit 11 caractères dans le CCF d'origine. Vérification du code réel (`PlanArmementMigrationService.java` ligne 83 : `pad(classeCode,2)+pad(sousClasseCode,2)+pad(categorieCode,2)+pad(serieCode,2)+pad(itemCode,4)`) : le projet Gamma 3 zéro-préfixe la classe sur 2 caractères ("01" à "08") pour obtenir un total de 12 caractères à largeur fixe — c'est cette convention (déjà en vigueur dans `Item.getClasseCode()` etc.) qui a été suivie, pas le format CCF brut à 11 caractères.
* **Constat :** Aucune table de référence séparée pour Classe/Sous-classe/Catégorie/Série n'existe en base (`grep` sur le code backend) — ces niveaux sont uniquement dérivés par position dans la chaîne `nomenclature` (voir `Item.java`, méthodes `getClasseCode()`/`getSousClasseCode()`/...). Le CCF ne documente en détail que la Classe 1 (Mécanique) : ses 11 sous-classes, et pour la sous-classe "Moteurs Diesels" ses 15 catégories (constructeurs) et les séries du constructeur MTU. Les autres classes ne sont pas détaillées dans les documents disponibls — d'où le choix de piloter Sous-classe/Catégorie/Série dynamiquement depuis les valeurs déjà présentes dans les 63 000+ articles migrés de Gamma 2, plutôt que d'inventer un référentiel incomplet.
* **Solution retenue :**
  1. **Backend** : nouvel endpoint `GET /api/v1/items/nomenclature/next-level` (`ItemController`) + deux requêtes natives dans `ItemRepository` (`findDistinctNomenclatureSegments`, `findMaxItemCode`) qui interrogent directement les nomenclatures déjà enregistrées (via `SUBSTRING`/`GROUP BY`/`LIKE` préfixe) pour renvoyer, niveau par niveau, les codes déjà utilisés (avec un exemple de désignation et leur nombre), et pour le niveau final le prochain code Item (4 chiffres) libre dans la série choisie.
  2. **Frontend** (`item.service.ts`) : méthode `getNomenclatureNextLevel()` appelant ce nouvel endpoint.
  3. **Frontend** (`catalogue.component.ts`/`.html`) : remplacement du champ nomenclature libre par 4 listes déroulantes en cascade (Classe fixe à 8 valeurs officielles ; Sous-classe/Catégorie/Série éditables, peuplées dynamiquement et permettant malgré tout la saisie d'un nouveau code) + un champ Code Article avec bouton de suggestion automatique. Les libellés officiels connus du CCF (8 classes, 11 sous-classes de la Classe 1, 15 catégories et 6 séries de l'exemple MTU) sont superposés aux codes retournés par l'API quand ils sont connus, sinon un aperçu basé sur un article existant est affiché.
  4. **Photo et document PDF** : ajout de deux `p-fileUpload` en mode `auto=false` (capture du fichier sans upload immédiat, car les endpoints `uploadPhoto`/`uploadDoc` exigent un UUID d'article déjà persistant) ; `saveNewItem()` enchaîne désormais création → upload photo (si fourni) → upload document (si fourni) → rafraîchissement du catalogue, avec des avertissements non bloquants si un upload échoue après une création réussie (l'article reste créé, à compléter depuis sa fiche).
  5. Vérifié par compilation TypeScript (`npx tsc --noEmit`) : aucune erreur. Relecture manuelle attentive du Java (parenthèses/accolades comptées, requêtes SQL natives vérifiées) en l'absence d'accès réseau à Gradle dans cet environnement (voir limitation déjà notée dans l'entrée précédente) — **à valider par un redémarrage du backend**.
  6. Documentation mise à jour (`docs/USER_GUIDE.md`).

## [Modèle d'Entrée]
* **Date :** AAAA-MM-JJ
* **Tâche :** Description de la tâche ou de la fonctionnalité
* **Difficulté rencontrée :** Description du problème ou du bug
* **Cause technique racine :** Pourquoi le problème est survenu
* **Solution retenue :** Comment le problème a été résolu (avec détails d'architecture ou de code)

