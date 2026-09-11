# Proposition — Gouvernance des rôles & traçabilité (Super Administrateur)

> Rédigé le 2026-09-11, à la demande de l'utilisateur : *« j'ai remarqué qu'il me manque un
> administrateur système (super admin, backup, restore, modification directe base des
> données, utilisateurs, clients, autorisations…). Propose la meilleure distribution des
> rôles, avec toute la traçabilité de chaque intervenant. »*
>
> **Statut : PROPOSITION, non implémentée.** Ce document présente le diagnostic et un plan ;
> il attend une décision explicite avant toute exécution (voir « Décisions à prendre » en fin
> de document) — un changement de modèle de rôles est difficile à défaire proprement une fois
> des comptes réels et des habitudes construits dessus.

---

## 1. Diagnostic — pourquoi le rôle `ADMIN` actuel ne suffit pas

`ADMIN` fait aujourd'hui **deux métiers très différents** en même temps :

| Métier | Exemples de pouvoirs actuels de `ADMIN` |
|---|---|
| **Administration logistique** (SGS) | Arbitrer les demandes, superviser tous les magasins, valider les transferts inter-magasins, confirmer un retrait à la soute SGS |
| **Administration système** | Créer/bloquer/modifier n'importe quel compte (y compris d'autres ADMIN), changer les paramètres système, lancer les migrations Gamma2, consulter l'audit |

**C'est exactement le type de mélange que `.antigravityrules` interdit par ailleurs** (séparation stricte des privilèges — jamais un compte qui remonte ses propres besoins ET les valide). Ici, le même compte qui **traite les demandes matérielles** peut aussi **créer le compte qui les lui envoie**, **se donner un rôle plus élevé**, ou **changer les règles du jeu** (paramètres système) sans second regard. Sur un système militaire, c'est un point de concentration de pouvoir et un risque en cas de compte compromis.

Il manque en plus, structurellement, tout ce qui touche à l'**infrastructure** du système lui-même :
sauvegarde/restauration de la base, correction directe de données incohérentes, rotation des
secrets (déjà signalé lors de l'incident du dépôt GitHub public le 2026-09-09), supervision des
migrations.

---

## 2. Proposition — un rôle `SUPER_ADMIN` distinct, au-dessus de `ADMIN`

### 2.1 Principe

Ajouter une **nouvelle valeur d'enum** `SUPER_ADMIN` (les 3 rôles actuels — `ADMIN`, `DA_MANAGER`,
`UNIT_USER` — ne changent pas de sens). `SUPER_ADMIN` gouverne le **système**, `ADMIN` gouverne la
**logistique**. Le tableau ci-dessous liste ce qui bascule.

| Pouvoir | Aujourd'hui | Proposition |
|---|---|---|
| Créer / modifier / bloquer un compte, réinitialiser un mot de passe | `ADMIN` | **`SUPER_ADMIN` uniquement** |
| Attribuer un rôle (y compris promouvoir en `SUPER_ADMIN`) | `ADMIN` | **`SUPER_ADMIN` uniquement** |
| Changer un paramètre système (délai d'inactivité, etc.) | `ADMIN` | **`SUPER_ADMIN` uniquement** |
| Consulter le Journal d'Audit | `ADMIN` | `SUPER_ADMIN` (accès complet) ; `ADMIN` en lecture limitée à son propre périmètre logistique, à décider |
| Lancer une migration Gamma2 (mouvements, plan d'armement) | `ADMIN` | **`SUPER_ADMIN` uniquement** (c'est une opération d'infrastructure, pas d'arbitrage quotidien) |
| Sauvegarde / restauration de la base | *n'existe pas* | **`SUPER_ADMIN` uniquement** (nouveau) |
| Correction directe d'une donnée incohérente | *n'existe pas (hors app)* | **`SUPER_ADMIN` uniquement**, via un outil encadré (voir §4) — jamais un accès SQL libre depuis l'IHM |
| Arbitrer une demande, valider un transfert, superviser les magasins | `ADMIN` | **Reste `ADMIN`** (c'est le métier SGS, pas de l'IT) |
| Escalade / validation de transfert inter-magasin | `ADMIN` | Reste `ADMIN` |

**Pourquoi une hiérarchie plutôt qu'une séparation stricte totale** : sur une petite structure (une
Marine, pas une multinationale), exiger deux personnes disponibles à tout moment serait
irréaliste. Le compromis retenu : `SUPER_ADMIN` **peut** aussi agir comme `ADMIN` si nécessaire
(compte superset), mais **`ADMIN` ne peut jamais agir comme `SUPER_ADMIN`** — le sens de la
hiérarchie compte : on retire du pouvoir à l'usage quotidien (`ADMIN`), on ne l'ajoute pas
ailleurs sans contrôle.

### 2.2 Ce que ça change concrètement pour les comptes existants

- Le compte `admin` actuel (`admin`/`admin`) devient le premier `SUPER_ADMIN`.
- Il faudrait créer **un second compte**, de rôle `ADMIN` « classique », pour l'usage
  logistique quotidien (arbitrage) — **si tu veux réellement la séparation** (sinon le même
  compte `admin` continue à tout faire, mais on aura quand même distingué les pouvoirs dans le
  code, prêt pour le jour où tu voudras séparer physiquement les deux personnes).

---

## 3. Traçabilité — ce qui existe déjà (2026-09-11) et ce qui reste à étendre

**Déjà livré cette session** (voir `DEV_JOURNAL.md`, entrée 2026-09-11 (9)) :
- `JournalAudit` (table `journal_audit`, append-only, jamais de suppression/modification exposée).
- Consigné : connexions (réussies/échouées), déconnexions (manuelle/inactivité/fermeture),
  création/modification/blocage/déblocage de compte, réinitialisation de mot de passe,
  changement d'un paramètre système.
- Écran `Administration > Journal d'Audit` (ADMIN aujourd'hui — basculerait sur `SUPER_ADMIN`
  avec ce plan), paginé, filtrable par matricule et par type d'événement.

**À étendre si cette proposition est retenue** (chaque nouveau pouvoir `SUPER_ADMIN` doit
journaliser) :
- Sauvegarde déclenchée, restauration déclenchée (avec le nom/horodatage du fichier restauré).
- Toute correction directe de donnée (voir §4) — **avec un motif obligatoire saisi par
  l'opérateur**, jamais une simple case à cocher.
- Attribution/changement de rôle d'un compte (actuellement fondu dans `COMPTE_MODIFIE` — à
  distinguer en `ROLE_MODIFIE` si on veut pouvoir l'auditer séparément).
- Lancement d'une migration Gamma2.
- Arbitrage des demandes / validation de transfert (actuellement PAS journalisé — seul l'état
  final est visible dans `demandes_materiel`, pas qui a décidé quoi et quand de façon
  consultable centralement).

---

## 4. Sauvegarde / restauration / correction directe — recommandations de conception

Ce sont les trois pouvoirs les plus dangereux à mal exposer dans une interface web. Recommandations :

### 4.1 Sauvegarde
- **Recommandé** : un bouton `SUPER_ADMIN` qui déclenche un `pg_dump` côté serveur (le backend
  a déjà accès à la base) vers un fichier horodaté sur le serveur — PAS un export téléchargé
  depuis le navigateur (fichier potentiellement volumineux, et il transiterait par le poste
  client d'une session web, une exposition inutile pour un système militaire).
- Journaliser chaque sauvegarde (fichier, taille, durée).
- Politique de rétention (ex. garder les 30 dernières) à définir avec toi.

### 4.2 Restauration
- **Ne JAMAIS l'exposer comme un simple bouton en un clic.** Une restauration écrase des
  données réelles pendant que l'application peut être en train de servir des utilisateurs —
  c'est l'action la plus destructrice de tout le système.
- Recommandé : (a) exiger une **sauvegarde de sécurité automatique juste avant** toute
  restauration ; (b) un mode maintenance qui déconnecte les autres utilisateurs pendant
  l'opération ; (c) une double confirmation (retaper un code affiché, comme GitHub le fait pour
  supprimer un dépôt) ; (d) une restauration limitée aux fichiers de sauvegarde **produits par
  GAMMA 3 lui-même** (pas un upload de fichier arbitraire).
- Alternative plus simple à court terme : garder la restauration comme une **procédure
  documentée hors application** (`pg_restore` en ligne de commande, exécutée par toi ou un
  administrateur système de confiance), et se contenter d'automatiser proprement la
  *sauvegarde* dans l'app — la restauration reste rare et mérite qu'un humain regarde ce qu'il
  fait avant de l'exécuter.

### 4.3 Correction directe de données
- **Ne PAS construire un éditeur de base de données générique** (« choisir une table, éditer
  n'importe quelle ligne ») : ça contourne toutes les règles métier de l'application (comme
  l'a montré chaque bug de cette session où une valeur incohérente en base créait un 500 ou un
  comportement silencieusement faux) et transforme n'importe quelle erreur de frappe en
  incident.
- Recommandé : des **outils de correction ciblés**, un par cas réel identifié (ex. « une
  demande reste bloquée dans un état intermédiaire », « un stock affiche une valeur négative »)
  — chacun avec sa propre validation métier et sa propre entrée d'audit, ajoutés au fil de
  l'eau quand un vrai besoin se présente plutôt que d'un coup.
- Pour un correctif véritablement exceptionnel et imprévu, la voie reste `psql` en ligne de
  commande par un `SUPER_ADMIN` — en dehors de l'application, comme cette session l'a déjà
  fait plusieurs fois pour restaurer les données de test.

---

## 5. Plan d'exécution proposé (si tu valides la direction)

**Phase 1 — rôle + périmètre (contenue, ~quelques heures)**
1. Ajouter `SUPER_ADMIN` à l'enum `Role`.
2. Basculer les endpoints listés au §2.1 (gestion des comptes, paramètres, migrations,
   journal d'audit) de `ADMIN` vers `SUPER_ADMIN`.
3. Promouvoir le compte `admin` existant en `SUPER_ADMIN` ; décider avec toi s'il faut créer un
   second compte `ADMIN` dédié à l'arbitrage quotidien.
4. Mettre à jour les gardes de route Angular et la navigation en conséquence.
5. ⚠️ Un `Role` avec `@Enumerated(EnumType.STRING)` génère une contrainte CHECK en base — le
   même piège que celui rencontré le 2026-09-09 avec `StatutDemande`. Cette fois, avec Flyway en
   place, ce sera une vraie migration versionnée (`V2__ajoute_role_super_admin.sql`), pas un
   correctif manuel.

**Phase 2 — sauvegarde automatisée + son suivi dans le journal d'audit.**

**Phase 3 — restauration encadrée** (selon l'option retenue en §4.2).

**Phase 4 — outils de correction ciblés**, au fil des besoins réels rencontrés.

**Phase 5 — extension de la traçabilité** à l'arbitrage des demandes et aux décisions de
réforme (qui a décidé quoi, quand) — actuellement l'état final est stocké mais pas un historique
des décisions consultable centralement.

---

## 6. Décisions à prendre avant d'exécuter la Phase 1

1. **Nom du rôle** : `SUPER_ADMIN` convient-il, ou préfères-tu un autre terme (ex.
   « Administrateur Système », en français, cohérent avec `DA_MANAGER`/`UNIT_USER` déjà en
   anglais technique) ?
2. **Un second compte `ADMIN`** dédié à l'arbitrage quotidien : le crée-t-on maintenant, ou le
   compte `admin` (devenu `SUPER_ADMIN`) continue-t-il aussi à arbitrer pour l'instant ?
3. **Priorité immédiate** : Phase 1 seule (rôle + périmètre) pour l'instant, ou embrayer aussi
   sur la sauvegarde automatisée (Phase 2) dans la foulée ?
4. **Restauration** : bouton encadré dans l'app (§4.2, plus de travail, plus de risque
   résiduel) ou procédure documentée hors application pour l'instant (plus simple, plus sûr) ?
