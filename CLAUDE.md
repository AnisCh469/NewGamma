# GAMMA 3 — Contexte projet pour Claude Code

Ce fichier est lu automatiquement par Claude Code à l'ouverture de ce dossier. Il résume
tout ce qu'une session Claude Code doit savoir avant d'intervenir sur ce projet — y compris
ce qui vient d'être fait dans une session Cowork précédente (voir section 7).

## 1. Le projet

**GAMMA 3.0** — système de gestion et d'approvisionnement du matériel naval pour la Marine
Nationale Tunisienne. Refonte moderne de l'application legacy **Gamma 2** (Silverlight +
SQL Server). Système militaire cloisonné (intranet, sans accès Internet requis en
production).

## 2. Protocole obligatoire — à respecter sur CHAQUE intervention

Ce projet impose un protocole strict, défini dans `.antigravityrules` (racine du dépôt —
**le lire intégralement avant toute modification**). Résumé des points qui s'appliquent à
toute session, y compris Claude Code :

- **Séparation stricte des privilèges** : un compte "Administrateur" ne doit jamais saisir
  de données lui-même — l'architecture doit toujours faire remonter les besoins des unités
  vers l'administrateur pour validation (jamais l'inverse).
- **Zero-Trust UI** : sanitation côté client de toute saisie ; ne jamais exposer un bouton
  d'action à un rôle non autorisé.
- **Performance** : debouncing 300ms sur les champs de recherche ; pagination/virtualisation
  au-delà de 100 lignes ; `useMemo`/`useCallback` justifiés ; densité visuelle par défaut.
- **Documentation obligatoire à chaque changement de code** :
  1. `docs/DEV_JOURNAL.md` — **append-only**, jamais réécrit. Une entrée par tâche/bug
     résolu, avec Date / Tâche / Difficulté rencontrée / Cause technique racine / Solution
     retenue. Modèle en bas du fichier.
  2. `docs/USER_GUIDE.md` — mis à jour (en français non technique) uniquement si l'interface
     ou le parcours utilisateur change.
  3. `docs/IMPLEMENTATION_PLAN.md` — **interdiction absolue de le réécrire ou l'écraser**.
     Seules les cases à cocher `[ ]` → `[x]` peuvent être modifiées. Toute proposition de
     changement lourd du plan doit être soumise en texte dans la conversation, jamais
     appliquée directement au fichier.
  4. ⚠️ **Un second fichier `implementation_plan.md` (minuscules) existe aussi à la racine**
     du dépôt, avec un contenu différent (Sprint 10 — Marchés/Commandes/Plans d'Armement),
     distinct de `docs/IMPLEMENTATION_PLAN.md` (Sprint sur la nomenclature 12 caractères).
     Ambiguïté non résolue à ce jour — clarifier avec l'utilisateur lequel fait référence
     avant de s'appuyer dessus.
- **Exigence pédagogique** : code destiné à être repris par une jeune équipe — en-tête JSDoc
  par fichier, commentaires expliquant le "pourquoi", algorithmes complexes décomposés en
  étapes numérotées.
- **Protocole éducatif en fin de réponse** : à chaque génération/modification de code,
  inclure un bloc "Protocole de Documentation Éducative" (Guide étape par étape / Le
  "Pourquoi" des fonctions clés / Analyse d'Impact — positif + risques).

## 3. Pile technique & structure

```
NewGamma/
├── gamma3-backend/     Spring Boot 3.x, Java 21, Gradle, Spring Data JPA/Hibernate
│   └── src/main/java/tn/defense/gamma3/<domaine>/{domain,repository,service,api}
├── gamma3-frontend/    Angular 17 (standalone, signals), PrimeNG 17, TailwindCSS
├── migration/          Script pgloader (gamma2_to_gamma3.load) — import legacy one-shot
├── docker-compose.yml  PostgreSQL 16 (port 5433) + SQL Server 2019 legacy (port 1434)
├── start.ps1           Démarrage unifié (corrigé le 2026-09-09, voir section 7)
└── docs/                Toute la documentation (voir section 5)
```

Base applicative **PostgreSQL**, schéma généré par **`ddl-auto: update`** (Hibernate) —
**aucun outil de migration versionnée** (pas de Flyway/Liquibase). Voir
`docs/DATABASE_ARCHITECTURE.md` section 6.6 pour les implications.

## 4. Démarrer et tester l'application

```powershell
./start.ps1     # démarre Docker (Postgres+SQLServer), puis backend et frontend dans 2 fenêtres PowerShell séparées
```
- Backend : `http://localhost:8080`
- Frontend : `http://localhost:4200`

**Comptes de test** (créés par `DataSeeder.java` au premier démarrage si la base est vide) :

> ⚠️ **2026-09-11 — nouveau rôle `SUPER_ADMIN`** (voir `docs/PROPOSITION_GOUVERNANCE_ROLES.md`
> et `docs/DEV_JOURNAL.md` entrée du même jour) : `admin` est désormais l'**Administrateur
> Système** (comptes/rôles, paramètres système, migrations, journal d'audit — plus, en
> superset, tout ce que `ADMIN` peut faire). Un second compte `sgs` a été créé pour
> l'**Administrateur Logistique** (arbitrage des demandes, supervision des magasins/SGS) —
> c'est lui qui incarne maintenant le rôle `ADMIN` "métier" au quotidien.

| Matricule | Mot de passe | Rôle | Détail |
|---|---|---|---|
| `admin` | `admin` | **SUPER_ADMIN** | Administrateur système (comptes, paramètres, migrations, audit) + superset logistique |
| `sgs` | `sgs2026` | ADMIN | Administrateur logistique — arbitrage des demandes, supervision de tous les magasins (SGS) |
| `client` | `client` | UNIT_USER | Compte générique sans unité rattachée |
| `DMEN` / `DRC` / `RPS` / `CFISM` | = matricule | UNIT_USER, `fonctionUnite=GESTIONNAIRE` | Compose et soumet les demandes de son unité |
| `DMEN_CDT` / `DRC_CDT` / `RPS_CDT` / `CFISM_CDT` | = matricule | UNIT_USER, `fonctionUnite=COMMANDANT` | Valide/refuse les demandes de la même unité avant GAMMA3 |
| `magasin_centre` | `magasin_centre` | DA_MANAGER | Rattaché au magasin SM1 |
| `magasin_sud` | `magasin_sud` | DA_MANAGER | Rattaché au magasin SM2 |

⚠️ **Note issue de la session Cowork précédente** : tester l'application depuis un
navigateur de prévisualisation en bac à sable (ex. le "Browser pane" de Cowork) échoue
systématiquement — toute requête `fetch`/XHR vers `localhost:8080` y est bloquée
(`ERR_BLOCKED_BY_CLIENT`), même si le backend répond correctement en navigation directe.
**Toujours tester dans un vrai navigateur (Chrome/Edge) lancé sur la machine**, ou via
`curl`/tests automatisés en ligne de commande — pas via un navigateur intégré sandboxé.

## 5. Documentation existante (à lire selon le besoin, pas systématiquement)

- **`docs/DATABASE_ARCHITECTURE.md`** — référence complète et à jour du schéma (22 entités,
  11 enums), diagramme ER Mermaid, règles métier hors DDL, état des migrations Gamma2, 9
  lacunes connues + recommandations. **Document de référence prioritaire pour toute question
  sur le modèle de données** — rédigé le 2026-09-09, vérifié ligne à ligne contre le code.
- **`docs/DEV_JOURNAL.md`** — historique chronologique complet des tâches/bugs/décisions,
  au format Date/Tâche/Difficulté/Cause racine/Solution. Consulter les entrées récentes
  (fin de fichier) pour comprendre le contexte des derniers changements avant d'intervenir.
- **`docs/USER_GUIDE.md`** — guide utilisateur en français non technique.
- **`docs/AUDIT_GAMMA3_2026-08-31.md`** — audit de conformité au cahier des charges (CCF).
- **`docs/IMPLEMENTATION_PLAN.md`** vs **`implementation_plan.md`** (racine) — voir
  l'avertissement en section 2.
- **`docs/GAMMA3 CCF/`** — cahier des charges fonctionnel original (référence normative).

## 6. État Git actuel

Branche `main`, **9 commits en avance sur `origin/main` — rien n'a été poussé (push)**.
Derniers commits (du plus récent au plus ancien) :
```
23bc79b fix: start.ps1 utilise des chemins absolus pour lancer backend/frontend
ef4b284 docs: ajout de l'architecture complète de la base de données (passation équipe)
c85f44f Double verification des demandes : Gestionnaire + Commandant par unite
4605941 Diagnostic liste utilisateurs vide + audit Gamma2 vs Gamma3
07df6b2 Securite: blocage de compte effectif immediatement + Gestion des Utilisateurs (Admin)
9a13ef7 Securite: verrouille /auth/register et la gestion 2FA (faille critique)
```
Décider avec l'utilisateur s'il faut pousser (`git push`) avant de poursuivre, notamment
avant toute action qui supposerait un état distant à jour.

## 7. Ce qui vient d'être fait (session Cowork du 2026-09-09, contexte immédiat)

Dans l'ordre chronologique :

1. **Diagnostic + correctif** : l'écran "Gestion des Utilisateurs" affichait une liste vide
   malgré des comptes existants — cause : backend non redémarré depuis l'ajout de
   `UserAdminController` (404 avalé silencieusement côté Angular). Correctif : gestion
   d'erreur explicite avec bandeau + bouton "Réessayer" (`gestion-utilisateurs.component.ts`).
2. **Nouvelle règle métier majeure implémentée** : chaque unité navigante dispose de deux
   comptes — un **Gestionnaire** qui compose/soumet les demandes de matériel, et un
   **Commandant** (chef d'unité) qui valide/refuse chaque demande AVANT qu'elle n'atteigne
   GAMMA3 (DA_MANAGER/ADMIN). Implémenté de bout en bout : nouveau modèle de données
   (`User.unite`/`User.fonctionUnite`, enum `FonctionUnite`), nouveaux statuts
   `StatutDemande` (`EN_ATTENTE_VALIDATION_COMMANDANT`, `REFUSE_COMMANDANT`), nouvel endpoint
   `POST /demandes/{id}/valider-commandant`, nouvel écran Angular "Valider les Demandes",
   correction d'une faille de sécurité au passage (`unite.id` du corps JSON n'était pas
   recoupé avec l'identité authentifiée). Détails complets : `docs/DEV_JOURNAL.md`,
   entrées du 2026-09-04 (7) — et règles métier expliquées dans
   `docs/DATABASE_ARCHITECTURE.md` section 6.2.
3. **Audit Gamma2 → Gamma3** : construction d'un outil d'audit (`LegacyAuditController`,
   `GET /api/v1/migration/audit`, écran "Administration > Audit Gamma2") pour lister
   exactement ce qui a été repris de la base legacy SQL Server et ce qui ne l'a pas été —
   car aucune trace fiable n'existait dans le code. Détails : `docs/DATABASE_ARCHITECTURE.md`
   section 7.
4. **Rédaction de `docs/DATABASE_ARCHITECTURE.md`** (nouveau document, demande explicite de
   l'utilisateur pour la passation à l'équipe de développement) — voir section 5 ci-dessus.
5. **Correctif `start.ps1`** : `Start-Process` héritait du dossier courant du terminal
   appelant (`C:\WINDOWS\System32` si lancé sans `cd` préalable) plutôt que du dossier du
   script, faisant échouer `cd gamma3-backend`/`cd gamma3-frontend` en silence. Corrigé avec
   `$PSScriptRoot` + `-WorkingDirectory`.
6. **Tentative de test end-to-end** : backend et frontend confirmés opérationnels (backend
   répond sur `:8080`, frontend compile et sert sur `:4200`), mais impossible de compléter le
   test de connexion depuis le navigateur de prévisualisation Cowork (bac à sable réseau
   bloquant `localhost:8080`, voir avertissement section 4). **Le workflow de double
   validation Gestionnaire/Commandant n'a donc PAS encore été testé de bout en bout en
   conditions réelles — c'est la prochaine étape logique.**

## 8. Prochaines étapes suggérées

1. Tester le workflow de double validation dans un vrai navigateur : connexion `DMEN` →
   créer une demande → connexion `DMEN_CDT` → valider/refuser → vérifier que le DA_MANAGER
   (`magasin_centre`) ne voit la demande qu'après validation du Commandant.
2. Tester "Gestion des Utilisateurs" (ADMIN) et "Audit Gamma2" (ADMIN) après ce redémarrage.
3. Décider s'il faut pousser les 9 commits locaux vers `origin/main`.
4. Traiter les lacunes listées dans `docs/DATABASE_ARCHITECTURE.md` section 9/10 selon les
   priorités de l'utilisateur (Flyway/Liquibase en premier lieu, recommandé).
