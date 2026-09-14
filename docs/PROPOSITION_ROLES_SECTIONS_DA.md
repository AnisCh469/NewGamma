# 📋 Proposition — Rôles par section de la DA (GEST/MAG/ADEST/SC/SCS/SEP/SeRef/SR)

**Date :** 2026-09-14
**Déclenché par :** deux précisions successives de l'utilisateur sur les groupes d'utilisateurs
officiels du CCF —
1. le tableau « Groupe d'utilisateur / désignation » (10 groupes : DDM, CDT, GEST, MAG, ADEST,
   SC, SCS, SEP, SeRef, SR), déjà intégré à `docs/AUDIT_CONFORMITE_CCF_2026-09-11.md` §7 ;
2. le logigramme de la Procédure P02 (Commande et suivi), qui montre concrètement **SCS et SEP
   comme "R" (Responsable)** de sections différentes du même processus — ce n'est pas une
   hiérarchie floue, chaque section a un périmètre d'action précis, formalisé façon RACI.

L'utilisateur le résume ainsi : *« chaque groupe d'utilisateur a ses propres règles et les accès
autorisés (…) donc on parle de différents groupes avec des privilèges différents. Analyse ça et
donne-moi ta meilleure proposition. »*

---

## 1. Ce que le CCF décrit précisément

| Groupe | Désignation | Procédure(s) dont il est responsable | Statut GAMMA 3 aujourd'hui |
|---|---|---|---|
| **DDM** | Détenteur Dépositaire de Matériel (unité) | Établit la demande de matériel (P05 OP01) | ✅ `UNIT_USER` / `FonctionUnite=GESTIONNAIRE` |
| **CDT** | Commandant (unité) | Valide la demande du DDM avant transmission à la DA | ✅ `UNIT_USER` / `FonctionUnite=COMMANDANT` |
| **GEST** | Gestionnaire (DA) | Coordination générale, arbitrage, décisions transverses | ⚠️ Fondu dans `ADMIN`/`SUPER_ADMIN` |
| **MAG** | Magasin (DA) | Tenue des fiches de stock, emballage, stockage (P04) | ✅ `DA_MANAGER` |
| **ADEST** | Aire de distribution (DA) | Remise physique du matériel au bénéficiaire (P05 OP04/OP06) | ⚠️ Fondu dans `DA_MANAGER`/`ADMIN` |
| **SC** | Section Comptabilité (DA) | Comptabilité matière, collationnement annuel (P07), dépouillement financier | ⚠️ Fondu dans `ADMIN`/`SUPER_ADMIN` |
| **SCS** | Section Commande et Suivi (DA) | Commande et suivi du matériel (P02, responsable des étapes 1-3-5) | ⚠️ Fondu — P02 hors périmètre actuel |
| **SEP** | Section Étude et Prévision (DA) | Prévisions (P01), **et co-responsable de P02** (étude des offres, nomenclatures — vu sur le logigramme partagé) | ⚠️ Fondu — P01 en projet |
| **SeRef** | Section Réforme (DA) | Réforme et déclassement du matériel (P06) | ⚠️ Fondu dans `ADMIN`/`SUPER_ADMIN` |
| **SR** | Section Recette (DA) | Réception du matériel (P03) | ⚠️ Fondu dans `ADMIN`/`SUPER_ADMIN`/`DA_MANAGER` |

### 1.1 Confirmation par le logigramme complet de P02 (2026-09-14)

L'utilisateur a partagé le logigramme intégral (pages 1/2 et 2/2) de la Procédure P02. Il **confirme
le partage SCS/SEP déjà pressenti**, avec le détail étape par étape :

| Étape | Acteur (R) | Action |
|---|---|---|
| 1 | **SCS** | Exploiter le CDMT |
| 2, 3 | **SCS** | Lancer les fiches de commande ; créer les fiches de suivi et fiches signalétiques |
| 4 | **SEP** (ou Division/Service) | Étudier les offres (5 branches : régie, bon de commande, appel d'offre, marché cadre, gré à gré) |
| 5 | **SEP** + **SCS** (conjoint) | Mettre à jour les fiches signalétiques de commande et de suivi |
| 6 | **SEP** | Préparer les nouvelles nomenclatures |
| 7 | **SCS** | Mettre à jour les fiches signalétiques de commande (après notification du marché) |
| — | **SRE** *(= SR/Section Recette, orthographe alternative rencontrée dans le texte des procédures P03)* | Réception (P03) → PV provisoire → si marché incomplet, boucle sur l'étape 7 ; si complet, PV final |

**Lecture :** SCS porte tout le **cycle de vie administratif** de la commande (fiches, suivi,
notification) ; SEP porte l'**évaluation technique** (étude des offres, nomenclatures). Les deux
sont bien deux responsabilités distinctes, pas une hiérarchie où l'une engloberait l'autre — ce qui
valide le choix de deux `FonctionDA` séparées plutôt qu'une seule "section commandes".

**Ça ne change rien à la recommandation** : P02 reste hors périmètre actuel de GAMMA 3 (§1,
`docs/AUDIT_CONFORMITE_CCF_2026-09-11.md`), donc `SCS` demeure une `FonctionDA` "Phase C, si P02
est construit un jour" — ce logigramme apporte de la précision pour CE jour-là (qui devra créer/
modifier une fiche de commande vs qui devra étudier une offre ou générer une nomenclature), sans
avancer la priorité de ce chantier. Il confirme aussi le lien P02 → P03 déjà capté dans le premier
audit (RG036 : *« Le PVR est provisoire pour les marchés. Un PVR définitif sera établi à
l'expiration de la période de garantie »*) — la boucle "Incomplet" du logigramme est la
représentation visuelle exacte de cette règle.

**Constat central :** DDM/CDT (niveau unité) et MAG (niveau DA) sont déjà correctement modélisés.
Les **7 autres groupes DA** (GEST, ADEST, SC, SCS, SEP, SeRef, SR) sont aujourd'hui fusionnés
dans deux rôles génériques (`ADMIN`, `SUPER_ADMIN`) qui peuvent TOUT faire côté DA — violant le
principe même de séparation des privilèges que le projet applique déjà ailleurs (unité :
Gestionnaire/Commandant ; système : SUPER_ADMIN/ADMIN).

---

## 2. Ma recommandation : un `FonctionDA`, symétrique à `FonctionUnite`

### 2.1 Pourquoi pas un nouveau `Role` top-level par section

Éclater `ADMIN` en 7 nouvelles valeurs de l'enum `Role` (`SC`, `SCS`, `SEP`, `SeRef`, `SR`, `GEST`,
`ADEST`) casserait tous les contrôles d'accès existants qui testent `role == Role.ADMIN` (une
quinzaine de fichiers) — chacun devrait être réécrit pour tester une liste de 7 valeurs au lieu
d'une seule, avec un risque élevé d'en oublier une et de rouvrir un trou de sécurité.

### 2.2 Le patron à réutiliser : `FonctionUnite` existe déjà pour exactement ce problème

GAMMA 3 a déjà résolu ce problème une fois, au niveau des unités :

```java
// User.java (existant)
private FonctionUnite fonctionUnite; // GESTIONNAIRE | COMMANDANT — n'a de sens que pour UNIT_USER
```

Je propose l'équivalent strict côté DA :

```java
// Nouveau
private FonctionDA fonctionDA; // GEST | MAG_... | ADEST | SC | SCS | SEP | SEREF | SR
                                 // n'a de sens que pour ADMIN — toujours null pour SUPER_ADMIN,
                                 // DA_MANAGER (déjà = MAG) et UNIT_USER
```

**Propriété clé, différente de `FonctionUnite` :** `FonctionUnite` est **obligatoire** dès qu'un
compte est `UNIT_USER` (un Gestionnaire ou un Commandant, jamais un flou entre les deux).
`FonctionDA` doit rester **facultatif** :
- **`null`** = comportement **actuel**, inchangé : l'`ADMIN` garde tous les pouvoirs DA (rétro-
  compatible avec `admin`/`sgs`, aucune régression sur les comptes déjà créés).
- **une valeur précise** = le compte est **restreint** à cette section, pour qui veut vraiment
  cloisonner (ex : créer un compte `SeRef` qui ne voit QUE l'écran Réforme).

C'est un modèle **opt-in** : on ajoute la capacité de cloisonner sans rien casser aujourd'hui, et
l'utilisateur décide, section par section, jusqu'où aller.

### 2.3 Valeurs proposées et ce qu'elles débloquent concrètement

| `FonctionDA` | Écran(s) GAMMA 3 concerné(s) aujourd'hui | Écran(s) futurs |
|---|---|---|
| `GEST` | **Tout** (équivalent à `null` — valeur explicite pour la traçabilité/le reporting, sans rien restreindre) | — |
| `ADEST` | Confirmation de retrait physique (`console-arbitrage`, bouton "Confirmer Retrait") | — |
| `SC` | Réforme (sortie des comptes), Analyses | Collationnement (P07) |
| `SCS` | — (P02 hors périmètre, voir audit §1) | Commande et suivi (P02), si construit un jour |
| `SEP` | — | **Prévisions (P01)**, dès sa construction |
| `SeRef` | **Réforme** (`ReformeController`, déjà bien couvert fonctionnellement) | — |
| `SR` | **Réception** (`ReceptionController`, déjà bien couvert fonctionnellement) | — |

`SeRef` et `SR` sont les deux candidats **immédiatement actionnables** : leurs écrans existent
déjà et sont gérés par un contrôle de rôle centralisé (`exigerRoleDA` / vérifications équivalentes
dans `ReformeController`/`ReceptionController`) qu'il suffit d'affiner.
`SEP` devient pertinent **dès que la Procédure P01 sera construite** (voir
`docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md`) — c'est le bon moment de le faire naître
avec ce rôle dès le départ plutôt que de le rattacher à `ADMIN` puis devoir le déplacer ensuite.

`MAG` n'a pas besoin d'exister comme `FonctionDA` : c'est déjà le rôle `DA_MANAGER` à part
entière (avec son propre rattachement à un magasin physique). `ADEST` est son voisin le plus
proche (remise physique du matériel) — deux options possibles, à trancher (voir §4).

---

## 3. Ce que ça change concrètement, une fois câblé

**Contrôle d'accès (exemple, `ReformeController`) :**
```java
// Aujourd'hui : tout ADMIN/SUPER_ADMIN a accès
if (u.getRole() != Role.SUPER_ADMIN && u.getRole() != Role.ADMIN && u.getRole() != Role.DA_MANAGER) { refuser(); }

// Proposé : un ADMIN restreint à une AUTRE section que SeRef est refusé
if (u.getRole() == Role.SUPER_ADMIN) { autoriser(); }               // superset, toujours
else if (u.getRole() == Role.ADMIN) {
    if (u.getFonctionDA() != null && u.getFonctionDA() != FonctionDA.SEREF && u.getFonctionDA() != FonctionDA.GEST) { refuser(); }
    autoriser();
}
else { refuser(); }
```

**Journal d'Audit :** chaque entrée capture déjà `matricule` + `fullName` par valeur (jamais de
FK, voir `JournalAudit`) — y ajouter la `FonctionDA` au moment de l'écriture permettrait de
répondre nativement à *« qui de la section Réforme a fait quoi ce mois-ci »*, exactement la
traçabilité par intervenant demandée à l'origine de la gouvernance des rôles
(`docs/PROPOSITION_GOUVERNANCE_ROLES.md`).

**IHM :**
- `app.component.html` : filtrer les liens de navigation par `FonctionDA` quand elle est définie
  (même patron que le filtrage déjà en place pour `FonctionUnite`).
- `gestion-utilisateurs.component` : nouveau sélecteur "Fonction DA" (facultatif), visible
  uniquement quand `Rôle = ADMIN` — même patron que le sélecteur "Fonction" déjà utilisé pour
  `UNIT_USER`.

---

## 4. Plan de déploiement proposé (3 phases, chacune indépendamment utile)

1. **Phase A — Fondations (petit effort)** : `FonctionDA` (enum) + colonne nullable sur `User` +
   sélecteur facultatif dans Gestion des Utilisateurs. Aucun contrôle d'accès modifié — zéro
   risque de régression, permet déjà de créer un compte "tagué" `SeRef`/`SR`/... à titre
   d'organisation/traçabilité, sans rien bloquer.
2. **Phase B — Cloisonnement des 2 sections déjà construites** : `SeRef` → `ReformeController`,
   `SR` → `ReceptionController`. C'est le test réel du modèle, sur des écrans matures.
3. **Phase C — Alignement au fil de l'eau** : `SEP` dès la construction de P01, `SC` dès celle de
   P07, `ADEST` si le besoin de distinguer "magasinier" de "chef d'aire de distribution" se
   confirme à l'usage.

---

## 5. Décisions à trancher

1. **Valider le principe `FonctionDA` opt-in** (plutôt qu'un `Role` éclaté en 7, ou plutôt que de
   ne rien faire et garder `ADMIN` monolithique) ?
2. **`ADEST` : fondre dans `DA_MANAGER`, ou en faire une vraie `FonctionDA` distincte ?**
   Recommandé : le laisser fondu dans `DA_MANAGER` pour l'instant (aucun workflow GAMMA 3 ne
   distingue aujourd'hui "magasinier qui stocke" de "chef d'aire qui remet au bénéficiaire" — le
   même compte fait les deux) ; séparer plus tard si l'organisation réelle le justifie.
3. **Démarrer par la Phase A seule**, ou enchaîner directement A+B (Réforme/Réception restreintes
   dès cette session) ? Recommandé : A d'abord (fondations sans risque), B dans la foulée si le
   test convainc — les deux sont petits.
4. **`GEST` : valeur explicite pour la traçabilité, ou vraiment équivalente à `null` (donc jamais
   assignée) ?** Recommandé : la garder comme valeur explicite assignable — utile le jour où
   l'utilisateur veut distinguer, dans le Journal d'Audit, un compte GEST (superviseur assumé) d'un
   compte ADMIN non catégorisé (créé avant l'introduction de ce champ).
