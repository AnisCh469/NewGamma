# Audit Global — GAMMA 3.0 (31 août 2026)

> Document produit par un audit assisté par IA (Claude, session Cowork). À valider par l'équipe technique et le chef de projet avant diffusion. Portée : hygiène du dépôt, sécurité, couverture fonctionnelle vs Cahier des Charges Fonctionnel (CCF), état de déploiement. Les vulnérabilités listées en section 2 ont déjà été corrigées dans le code (voir `docs/DEV_JOURNAL.md`, entrée du 2026-08-31).

---

## 1. Résumé exécutif

GAMMA 3.0 est un système fonctionnellement riche et déjà largement avancé : les modules Catalogue, Stock, Réception, Distribution, Réforme, Tiers (Fournisseurs/Unités), Marchés & Commandes Fournisseurs, Plans d'Armement (dotations), Notifications et Reporting existent tous en code, avec séparation des rôles (ADMIN / DA_MANAGER / UNIT_USER) et données de démonstration réalistes. Le Sprint 10 (Marchés, Commandes Fournisseurs, Plans d'Armement) décrit dans `implementation_plan.md` est **déjà implémenté**, de même que la refonte de la nomenclature à 12 caractères décrite dans `docs/IMPLEMENTATION_PLAN.md`.

Trois constats majeurs ressortent de cet audit, par ordre de gravité :

1. **Hygiène Git critique** : environ 90 % du code applicatif actuel (modules Distribution, Migration, Notification, Réception, Réforme, Reporting, Tiers côté backend ; Distribution, Réception, Réforme, Tiers, Analytics côté frontend) n'est **pas suivi par Git** (`Untracked files`). La branche locale est également 13 commits derrière `origin`. **Risque concret de perte de travail** en cas de problème sur le poste de développement.
2. **Trois vulnérabilités de sécurité critiques** étaient présentes dans le code (identifiants administrateur en dur, secrets JWT/BDD en dur, traversée de répertoire sur les endpoints de fichiers). Elles ont été **corrigées durant cet audit** (voir section 2). Des branches Git préparées en amont montraient que l'équipe (ou un contributeur précédent) avait déjà identifié ces failles sans les fusionner.
3. **Le déploiement de production était non fonctionnel** : `docker-compose.prod.yml` référençait des `Dockerfile` qui n'existaient pas, et le frontend appelait l'API via une URL absolue (`http://localhost:8080`) codée en dur dans 17 fichiers, ce qui aurait empêché tout accès depuis un poste client autre que celui du développeur. **Corrigé durant cet audit** (voir section 5).

---

## 2. Sécurité — vulnérabilités identifiées et corrigées

| # | Vulnérabilité | Fichier(s) | Sévérité | Statut |
|---|---|---|---|---|
| 1 | Identifiants administrateur par défaut (`admin`/`admin`) écrits en dur et journalisés en clair au démarrage | `DataSeeder.java` | Élevée | ✅ Corrigé — externalisé via `ADMIN_MATRICULE`/`ADMIN_PASSWORD`, plus de mot de passe dans les logs |
| 2 | Clé de signature JWT et mots de passe PostgreSQL / SQL Server legacy en dur dans `application.yml` (et mot de passe PostgreSQL en dur dans `docker-compose.prod.yml`) | `application.yml`, `docker-compose.prod.yml` | Critique | ✅ Corrigé — toutes les valeurs sensibles passent par des variables d'environnement (`.env`, voir `.env.example`) |
| 3 | Traversée de répertoire (Path Traversal / CWE-22) sur les endpoints publics `GET /api/v1/items/photos/{fileName}` et `/documents/{fileName}`, et sur l'upload `POST /{id}/upload-photo` / `/upload-doc` | `ItemUploadController.java` | Critique | ✅ Corrigé — rejet des séquences `..` + vérification que le chemin résolu reste contenu dans le dossier `uploads/` |

**Comment ces failles ont été retrouvées :** le dépôt contenait déjà des branches distantes non fusionnées nommées explicitement `fix-hardcoded-admin-credentials-*`, `security/fix-hardcoded-secrets-*` et `fix/item-upload-path-traversal-*`. Leur simple existence confirmait que ces trois failles avaient déjà été détectées (probablement par un outil d'analyse statique ou un audit précédent) mais jamais intégrées à la branche de travail. Le code actuel de `ItemUploadController.java` ayant évolué depuis la création de ces branches, les correctifs ont été **réécrits à jour** plutôt que fusionnés tels quels, et étendus à `serveFile()` (lecture) qui n'était pas couvert par la branche d'origine (qui ne corrigeait que l'upload).

**Vérification effectuée :** compilation TypeScript du frontend (`npx tsc --noEmit`) sans erreur nouvelle ; relecture ligne à ligne des fichiers backend modifiés. **Non vérifié dans cette session** (limitations d'environnement, voir section 6) : build Gradle complet et exécution réelle des endpoints corrigés. **Action recommandée avant mise en production : exécuter `./gradlew test` et effectuer un test manuel des 4 endpoints listés ci-dessus avec des noms de fichiers contenant `../`.**

### Recommandations de sécurité non traitées dans cette session

* Les comptes de démonstration non-admin (`client`, codes d'unité `DMEN`/`DRC`/`RPS`/`CFISM`/`P202`, `magasin_centre`, `magasin_sud`) utilisent toujours des mots de passe prévisibles liés au matricule. Acceptable pour un environnement de démonstration/UAT, mais **à désactiver ou changer avant toute mise en production réelle** (ces comptes ne sont pas administrateurs, sévérité plus faible que le cas admin).
* `gamma3-backend/uploads/` (photos/documents réellement téléversés par les utilisateurs pendant les tests) est actuellement suivi par Git (visible dans `git status`). Committer des fichiers binaires utilisateur dans l'historique Git n'est pas une bonne pratique (taille du dépôt, fuite de données de test). À exclure via `.gitignore` pour les prochains ajouts — ne pas supprimer rétroactivement sans décision de l'équipe (cela réécrirait l'historique).
* CORS est actuellement restreint à `http://localhost:4200` en dur dans `SecurityConfig.java` — à rendre configurable par variable d'environnement en même temps qu'un futur passage en production (actuellement non bloquant car le reverse-proxy Nginx sert le frontend et l'API sous la même origine en production, mais reste une dette technique).

---

## 3. Hygiène du dépôt Git — risque de perte de travail

État constaté (`git status` / `git branch -a`) :

* Branche locale `gamma3-init-331245186370893602`, **13 commits en retard sur `origin`** (peut être mis à jour par un simple `git pull`, aucun conflit détecté à ce stade).
* **Modifications non indexées et non committées sur la quasi-totalité du projet** : fichiers modifiés dans `auth`, `catalogue`, `stock`, `config`, tout le frontend (`app.routes.ts`, `auth.service.ts`, composants catalogue, configuration Angular/TypeScript...).
* **Répertoires entiers non suivis par Git** (`Untracked files`) : côté backend — `distribution/`, `migration/`, `notification/`, `reception/`, `reforme/`, `reporting/`, `tiers/` (ce dernier contient tout le module Marchés/Plans d'Armement du Sprint 10) ; côté frontend — `features/analytics/`, `features/distribution/`, `features/reception/`, `features/reforme/`, `features/tiers/`, `core/services/`. **Autrement dit, la majorité des fonctionnalités livrées n'existe qu'en local, sur ce seul poste.**
* **22 branches distantes** existent sur `origin`, pour beaucoup avec des noms qui ressemblent à des branches générées automatiquement par un outil d'agent IA (suffixes numériques longs), couvrant des correctifs de sécurité, des tests et des optimisations de performance — dont les 3 déjà analysées en section 2. **Aucune de ces branches n'a été auditée en détail dans cette session au-delà des 3 liées à la sécurité** ; certaines contiennent peut-être d'autres correctifs utiles (ex : `perf-optimize-seeding-*`, `improve-testing-crud-subclass-*`).

### Recommandation prioritaire (action humaine requise)

Ceci **n'a pas été corrigé automatiquement** dans cette session : committer l'historique Git est une décision qui doit rester entre les mains de l'équipe (choix des messages de commit, découpage logique, revue). Recommandation concrète pour la prochaine session de travail :

1. `git add` + commit du travail en cours, par domaine fonctionnel si possible (ex : un commit pour `tiers/` + Sprint 10, un commit pour la refonte nomenclature, un commit pour les correctifs de sécurité de cet audit).
2. `git pull` pour récupérer les 13 commits manquants d'`origin` (après le commit local, pour éviter tout conflit avec du travail non sauvegardé).
3. Passer en revue les 22 branches distantes : fusionner celles qui apportent une vraie valeur (sécurité, tests), fermer/supprimer celles obsolètes.
4. Mettre en place une règle d'équipe simple : commit + push au moins en fin de journée (le fichier `HOME_SETUP.md` du projet le recommande déjà, mais ce n'est visiblement pas appliqué systématiquement).

---

## 4. Architecture technique (constat)

* **Backend** : Java 21 (toolchain Gradle), Spring Boot **4.0.6** (`build.gradle`) — à noter, plus récent que le Spring Boot 3.x annoncé dans `README.md`, à corriger dans la documentation. Spring Data JPA/Hibernate, Spring Security (JWT + 2FA TOTP), Gradle 9.4.1 (wrapper).
* **Frontend** : Angular 17 (builder `application`, standalone components), PrimeNG 17, TailwindCSS, Chart.js, génération de QR codes et codes-barres (2FA, étiquettes).
* **Base de données** : PostgreSQL 16 (cible), SQL Server 2019 (legacy Gamma 2, pour migration uniquement).
* **Modules backend présents** : `audit`, `auth`, `catalogue`, `config`, `distribution`, `migration`, `notification`, `reception`, `reforme`, `reporting`, `stock`, `tiers` (Fournisseurs, Marchés, Commandes Fournisseurs, Plans d'Armement, Unités Utilisatrices, Demandes de Dotation).
* **Modules frontend présents** : `auth`, `catalogue` (+ item-detail, demander-dotation), `stock`, `reception` (+ detail/list), `distribution` (+ console-arbitrage), `reforme`, `tiers` (fournisseurs, unites), `analytics` (dashboard).
* **Environnement de build local (sandbox utilisé pour cet audit)** : JDK 11 uniquement disponible, pas de Docker — **le build Gradle complet (JDK 21 requis) et les tests d'intégration n'ont pas pu être exécutés dans cette session**. Le build Angular (`ng build`) a été lancé mais n'a pas pu être mené à terme dans le temps imparti par l'environnement d'audit ; seule la vérification TypeScript (`tsc --noEmit`, plus rapide) a pu être menée à bien avec succès. **Recommandation : l'équipe doit exécuter `./gradlew test` et `npm run build` sur un poste de développement standard (celui décrit dans `HOME_SETUP.md`) pour confirmer que l'ensemble compile et que les tests passent, y compris après les correctifs de cet audit.**

---

## 5. Déploiement — état constaté et corrections apportées

| Élément | État avant audit | État après audit |
|---|---|---|
| `gamma3-backend/Dockerfile` | **Absent** (référencé par `docker-compose.prod.yml` mais inexistant → build impossible) | ✅ Créé (multi-étapes Gradle/JDK 21 → JRE 21, utilisateur non-root) |
| `gamma3-frontend/Dockerfile` | **Absent** | ✅ Créé (multi-étapes Node → Nginx) |
| `gamma3-frontend/nginx.conf` | Absent | ✅ Créé (sert l'app Angular + reverse-proxy `/api/**` et `/uploads/**` vers le backend) |
| URL de l'API dans le frontend | Codée en dur (`http://localhost:8080`) dans 17 fichiers → inutilisable depuis un autre poste que celui du développeur | ✅ Centralisée dans `src/environments/environment.ts` (dev) / `environment.prod.ts` (prod, URL relative) |
| Secrets dans `docker-compose.prod.yml` | Mots de passe en dur | ✅ Lus depuis un fichier `.env` non versionné (modèle : `.env.example`) |
| Persistance des fichiers téléversés en production | Non prévue (répertoire `uploads/` interne au conteneur, perdu à chaque redéploiement) | ✅ Volume Docker nommé `gamma3_uploads` ajouté |

**Non vérifié dans cette session** (Docker n'est pas disponible dans l'environnement d'audit) : que `docker compose -f docker-compose.prod.yml build` réussit réellement de bout en bout. **Action recommandée : exécuter ce build sur un poste avec Docker Desktop avant toute mise en production**, en particulier pour confirmer la présence de toutes les dépendances Gradle en mode hors-ligne restreint et la taille finale des images.

`docker-compose.yml` (développement local) référence un chemin Windows en dur (`c:\Users\anisc\Desktop\Gamma3 Claude\Backup GAMMA2`) pour le volume de sauvegarde SQL Server — spécifique au poste d'un développeur en particulier. Non corrigé (choix volontaire : ce fichier sert au développement local individuel, chaque développeur doit adapter ce chemin chez lui ; `HOME_SETUP.md` le mentionne déjà).

---

## 6. Couverture fonctionnelle vs Cahier des Charges Fonctionnel (CCF)

Le CCF (`docs/GAMMA3 CCF/3 RECEUIL DES PROCEDURES ET REGLES DE GESTION.doc`, ~90 pages) définit 9 procédures métier officielles de la Direction d'Approvisionnement (DA). Le tableau ci-dessous rapproche chaque procédure des modules applicatifs identifiés dans le code. **Ce mapping est un sondage réalisé en début d'audit (titres de sections + recherche de mots-clés dans le code), pas une lecture exhaustive des ~1500 lignes du document ligne par ligne — il doit être validé par l'équipe fonctionnelle avant d'être considéré comme définitif.**

| Procédure CCF | Module applicatif correspondant | Constat |
|---|---|---|
| P01 — Prévisions du matériel | Aucun module dédié identifié | ⚠️ **Gap probable** — aucun package `prevision`, aucune fonctionnalité de planification annuelle repérée |
| P02 — Réception | `reception` (backend + frontend) | ✅ Présent |
| P03 — Stockage du matériel | `stock` (backend + frontend) | ✅ Présent |
| P04 — Distribution | `distribution` (backend + frontend, incl. console d'arbitrage) | ✅ Présent |
| P05 — Réforme et sortie des comptes | `reforme` (backend + frontend) | ✅ Présent |
| P06 — Comptabilité | Aucun module dédié identifié | ⚠️ **Gap probable** — aucune fonctionnalité de comptabilité-matière repérée (au-delà du suivi de valeur unitaire des articles) |
| P07 — Mise à jour (du catalogue/nomenclature) | `catalogue` | ✅ Présent (incl. refonte nomenclature 12 caractères) |
| P08 — Réapprovisionnement | `tiers` (Marchés, Commandes Fournisseurs, Fournisseurs) | ✅ Présent (Sprint 10) |
| P09 — Remise du matériel | Logique partiellement présente dans `distribution` et `reforme` | ⚠️ À confirmer — pas de module dédié « Remise », logique probablement répartie ailleurs |

**Recommandation :** demander à l'équipe fonctionnelle/métier de confirmer si P01 (Prévisions) et P06 (Comptabilité) sont réellement hors périmètre du MVP actuel ou constituent un backlog non encore priorisé, et de vérifier avec le référent Marine si « Remise » (P09) est bien couverte par les écrans existants de Distribution/Réforme ou nécessite un écran dédié.

Les documents `docs/GAMMA3 CCF/4 INSTRUCTIONS DE CREATION MODIFICATION FT.docx` (fiches techniques articles) et `2 Définition Abréviation.docx` (glossaire métier) n'ont pas été confrontés en détail au code dans cette session, faute de temps ; ils décrivent probablement des règles de validation de champs qu'il serait utile de vérifier contre les DTOs/validateurs Angular du module Catalogue dans une prochaine passe.

---

## 7. État des sprints en cours

* **Sprint 10 (Marchés, Commandes Fournisseurs, Plans d'Armement)** décrit dans `implementation_plan.md` : **implémenté** — entités `Marche`, `CommandeFournisseur`, `PlanArmement`, contrôleurs et repositories présents dans `tiers/`, seeding de démonstration en place dans `DataSeeder.java` (marchés actifs, bons de commande, dotations par unité). **Non vérifié** : que le filtrage du catalogue par rôle `UNIT_USER` (plan d'armement) fonctionne réellement à l'exécution — nécessite un backend démarré.
* **Refonte de la nomenclature à 12 caractères** décrite dans `docs/IMPLEMENTATION_PLAN.md` (document protégé, non modifié par cet audit conformément à la règle 4 des instructions système) : **implémentée** — `Item.java` porte déjà la colonne unique `nomenclature` avec des getters virtuels pour la compatibilité ascendante des 5 sous-codes, exactement comme décrit dans l'entrée du journal du 2026-05-30.

Ces deux constats sont une bonne nouvelle : le code est **en avance sur la documentation de suivi**, mais cela renforce l'urgence de la section 3 (commit Git) — un travail aussi avancé et non sauvegardé dans l'historique est le risque le plus élevé actuellement sur ce projet.

---

## 8. Dette technique et pistes d'amélioration (non traitées dans cette session)

* **Tests automatisés** : plusieurs branches distantes (`improve-testing-crud-subclass-*`, `testing-improvement-*`, `add-crud-series-tests-*`) suggèrent un effort de couverture de tests en cours mais non consolidé sur la branche principale. À auditer et fusionner sélectivement.
* **`gamma3-frontend/fix-tests.js`** : présence d'un script correctif ad hoc pour les tests, signe probable d'une instabilité de la suite de tests existante — à investiguer (le skill `engineering:testing-strategy` est recommandé pour cadrer cet effort, voir section 9).
* **Respect des règles de performance déclarées** (`.antigravityrules`, section 2 : debouncing 300ms, virtualisation au-delà de 100 lignes, `useMemo`/`useCallback` équivalents Angular) : non audité systématiquement dans cette session au-delà du composant Catalogue (dont le journal de développement confirme un debouncing 300ms sur la recherche par nomenclature). Une revue dédiée des grilles de données (stock, catalogue, distribution) serait utile.
* **CORS et configuration multi-environnement** : voir section 2, recommandation sécurité.

---

## 9. Skills Claude recommandés pour la suite du projet

Ce compte dispose déjà d'un plugin `engineering` avec des skills directement applicables à GAMMA3 :

* **`engineering:code-review`** — à utiliser systématiquement avant de fusionner une branche (notamment les 19 branches distantes restantes non encore auditées).
* **`engineering:tech-debt`** — pour transformer la section 8 ci-dessus en backlog priorisé formel.
* **`engineering:testing-strategy`** — pour cadrer l'effort de tests déjà entamé sur plusieurs branches distantes.
* **`engineering:deploy-checklist`** — à exécuter avant chaque mise en production, une fois le build Docker validé (section 5).
* **`engineering:architecture`** — utile pour documenter formellement (ADR) des décisions déjà prises implicitement, comme le choix Nginx-reverse-proxy pour le déploiement, ou la stratégie de compatibilité ascendante de la nomenclature.
* **`engineering:incident-response`** — à garder sous le coude vu le contexte opérationnel (système de gestion logistique navale) : utile si un incident survient après mise en production.
* **`engineering:documentation`** — déjà partiellement suivi de facto par les règles du projet (`.antigravityrules`) ; ce skill peut aider à maintenir `docs/USER_GUIDE.md` à jour à mesure que l'UI évolue.

**Skill dédié au projet proposé séparément** : les règles `.antigravityrules` (protocole de documentation éducative, séparation des privilèges, règles de performance) sont spécifiques à l'outil Antigravity IDE et ne s'appliquent pas automatiquement aux autres sessions Claude (Cowork, claude.ai, Claude Code). Une proposition de skill portable encodant ces mêmes règles a été soumise séparément (voir le message accompagnant ce rapport) afin que toute session Claude travaillant sur GAMMA3, quel que soit l'outil, applique le même protocole.

---

## 10. Plan d'action priorisé pour l'équipe

1. **Urgent — Git** : committer le travail en cours par domaine fonctionnel, puis `git pull` (13 commits de retard), puis trier les 22 branches distantes (section 3).
2. **Urgent — Sécurité** : relire les correctifs de cette session (`DataSeeder.java`, `application.yml`, `ItemUploadController.java`, `docker-compose.prod.yml`), créer un fichier `.env` réel à partir de `.env.example` avec des secrets forts, exécuter `./gradlew test`.
3. **Avant toute mise en production** : valider `docker compose -f docker-compose.prod.yml build` sur un poste avec Docker Desktop ; tester manuellement les 4 endpoints de fichiers avec des noms contenant `../` pour confirmer les correctifs.
4. **Court terme** : faire confirmer par l'équipe fonctionnelle le statut des procédures P01 (Prévisions) et P06 (Comptabilité) du CCF (section 6).
5. **Continu** : adopter les skills listés en section 9, en commençant par `engineering:code-review` sur les branches distantes restantes.

---

*Fin du rapport. Généré durant une session Claude (Cowork) le 2026-08-31, en complément du travail de correction de sécurité et de déploiement effectué dans la même session (voir `docs/DEV_JOURNAL.md`).*
