# 🔍 Audit de conformité GAMMA 3 vs. Cahier des Charges Fonctionnel (CCF)

**Date :** 2026-09-11
**Objet :** Comparaison systématique de l'implémentation GAMMA 3 avec les 3 documents source du
CCF, à la demande explicite de l'utilisateur ("analyse le cahier de charge fonctionnel pour
déterminer si nous sommes sur la bonne route, trouve les décalages et les lacunes").

**Documents analysés** (`docs/GAMMA3 CCF/`) :
1. `2 Définition Abréviation.docx` — glossaire des sigles.
2. `4 INSTRUCTIONS DE CREATION MODIFICATION FT.docx` — règles de nomenclature (12 caractères) et
   modèle de fiche signalétique d'article (FSA), tableaux d'attributs.
3. `3 RECEUIL DES PROCEDURES ET REGLES DE GESTION.doc` — 9 procédures (P01 à P09, + P07 Bis) et
   **111 règles de gestion** (RG1 à RG111) régissant le cycle de vie complet du matériel.

**Méthode :** chaque procédure et règle de gestion a été confrontée au code source réel (entités,
contrôleurs, services), pas seulement à la documentation existante — voir les fichiers cités à
chaque point.

---

## 1. Vue d'ensemble par procédure

| Procédure | Objet | Couverture GAMMA 3 |
|---|---|---|
| **P01** | Prévisions annuelles (PADI/CDMT, seuils Sa/Ss/Smin, statut Essentiel) | ❌ **Absente** |
| **P02** | Commande et suivi (marchés, appels d'offres, dépouillement) | ⚠️ Partielle — hors périmètre logistique volontaire, probablement correct |
| **P03** | Réception du matériel (PVR, commission, non-conformité) | ✅ Cœur couvert, ⚠️ formalisme papier simplifié |
| **P04** | Stockage (fiche de stock, emballage, inventaire) | ⚠️ Partielle |
| **P05** | Distribution du matériel | ✅ Cœur couvert, ⚠️ plusieurs RG non appliquées (voir §3) |
| **P06** | Réforme et sortie des comptes | ✅ Bonne base (Complet/Déclassement/Réparation existent), ⚠️ formalisme simplifié |
| **P07** | Comptabilité / collationnement annuel | ❌ **Absente** |
| **P07 Bis** | Mise à jour des plans (armement, configuration, allocation...) | ❌ **Absente + faille de sécurité** (voir §2.1) |
| **P08** | Réapprovisionnement des magasins régionaux | ✅ Testé et fonctionnel, ⚠️ formalisme simplifié |
| **P09** | Remise / reversement du matériel | ⚠️ Partielle (upload documents signés ≈ équivalent moderne) |

**Lecture générale :** GAMMA 3 couvre bien le **cœur opérationnel quotidien** (demande → arbitrage
→ retrait, réception, réforme) mais n'a **jamais couvert le volet planification/prévision (P01)
ni le contrôle comptable annuel (P07)** — ce sont deux processus entiers, pas des détails.

---

## 2. Lacunes critiques (impact fonctionnel ou sécurité direct)

### 2.1 Faille de sécurité — `PlanArmementController` sans aucun contrôle de rôle

[`PlanArmementController.java`](../gamma3-backend/src/main/java/tn/defense/gamma3/tiers/controller/PlanArmementController.java)
expose `POST`/`PUT`/`DELETE` sur `/api/v1/plans-armement/**` **sans la moindre vérification de
rôle** — tout compte authentifié (y compris un `UNIT_USER`) peut créer, modifier ou **supprimer**
n'importe quelle ligne de Plan d'Armement de n'importe quelle unité. C'est une violation directe
du principe Zero-Trust du projet, et l'équivalent numérique de contourner entièrement la
Procédure P07 Bis (RG091-RG093), qui exige une demande formelle du corps, visée par son chef,
traitée par une "commission de traitement des demandes de mise à jour des plans".

### 2.2 Seuil de sécurité jamais appliqué (RG16, RG17, RG68, RG102)

Le champ `Item.stockSecurite` existe et s'affiche (fiches, PDF) mais **n'est vérifié nulle part**
dans [`DistributionService.java`](../gamma3-backend/src/main/java/tn/defense/gamma3/distribution/service/DistributionService.java)
pour bloquer une sortie. Le CCF est pourtant explicite et répété (P01, P05, P08) :

> *"Au seuil de sécurité, la distribution de l'article est bloquée. Elle n'est autorisée que sur
> ordre de l'EMAM/BL."*

Aujourd'hui rien n'empêche de vider un article en dessous de son seuil de sécurité par arbitrages
successifs.

### 2.3 Procédure P01 (Prévisions) entièrement absente

Aucune des notions suivantes n'existe dans le code :
- **Seuil d'alerte (Sa)** — point de commande, calculé différemment pour un consommable
  (`Sa = Cmoy3 × n`) et pour du matériel d'armement (`Sa = 10% du parc × n`) (RG3-RG8).
- **Seuil minimal (Smin) / seuil de sécurité (Ss)** pour tout article "Essentiel" (RG13-RG15,
  RG20) — `Ss = Sa/3`, `Smin = Sa - Ss`.
- **Stock potentiel** = stock réel + commandé − réservé − sécurité (RG9).
- **Prévision par lissage exponentiel** `Pn = Pn-1 + α(Rn-1 - Pn-1)` (RG18-RG19).
- **Statut "Essentiel"**, attribuable par une division technique mais jamais révocable par elle
  seule (RG14, RG20).

### 2.4 Procédure P07 (Comptabilité / collationnement) entièrement absente

Pas de registre d'inventaire par unité, pas de planning annuel de collationnement, pas de PV de
collationnement, pas de traitement des écarts (C5/C5 bis pour un déficit, M16 pour un excédent —
RG88-RG90). GAMMA 3 sait *montrer* un état des stocks à un instant T (`InventaireReportDto`) mais
ne modélise aucun **processus de réconciliation périodique tracé**.

---

## 3. Lacunes fonctionnelles significatives (P05 — Distribution)

| RG | Règle | État |
|---|---|---|
| RG49/RG96 | Distribution "premier entré, premier sorti" (PEPS) | ❌ Pas de notion de lot d'entrée — le stock est un simple total agrégé par article/magasin |
| RG56 | Formule d'allocation proportionnelle en cas de pénurie : `Qté accordée = Stock × Qté demandée / Qté programmée` | ❌ GAMMA3 gère la pénurie par **transfert complet** du déficit depuis SGS (`DistributionService.validerTransfertInterMagasin`), pas par répartition entre demandeurs concurrents |
| RG57 | Tolérance de +10% sur le seuil accordé sans dépasser les crédits alloués | ❌ Non implémenté |
| RG70/RG71 | Une demande ne doit pas mélanger consommable/non-consommable, ni figurer sur 2 bons différents | ❌ Non vérifié à la création |
| RG72/RG105 | Validité d'une demande : 30 jours à compter de son traitement | ❌ Aucune expiration |
| OP07 (P05/P08) | Annulation automatique + restitution si non retiré sous 1 mois | ❌ Non implémenté |
| RG58 | Distribution d'armement/configuration/accessoires d'armement soumise à autorisation EMAM/BL | ❌ Aucune distinction de ce type de matériel dans le workflow |
| RG67/RG101 | Numérotation officielle des bons (Code Bon 1/2 + Code corps + N° séquentiel + Année) | ⚠️ GAMMA3 utilise `BS-<numeroDemande>`, un format propre non conforme au CCF |
| RG59/RG73/RG74 | Distinction stricte des canaux de demande selon le type (stock bord = unité seule, échelon division = division technique seule...) | ❌ Le catalogue/panier ne distingue pas ces types de matériel |

---

## 4. Fiche article (FSA) — écart de richesse des données

Le document 4 décrit une fiche signalétique bien plus riche que l'entité
[`Item.java`](../gamma3-backend/src/main/java/tn/defense/gamma3/catalogue/domain/Item.java) actuelle :

**Déjà couvert :** nomenclature 12 car., désignation, prix unitaire, stock de sécurité, unité de
gestion, type de consommabilité, classe de danger GHS, photo/document.

**Absent du modèle actuel :**
- Données physiques : Poids, Volume, Norme ISO, Échelon de maintenance, Durée de conservation.
- Statut Critique/Non-Critique (Cr/NCr), Stock Minimum, Stock Maximum, CMA, ÉTAT.
- **Code Type de matériel** (12 valeurs : MC, EQ, BE, DT, PS, PR, PC, AC, OD, OS, MP, MS).
- **Code de gestion** (6 valeurs : Clf, Clu, Ciu, Cei, Csm, Clm — contrôle des commandes).
- **Code de soutien** (S10 à S50 — soutien normé/restreint/spécial/suspendu/arrêté).
- **Code position administrative** (An, Dc, Da, Sb, Va, Ps, Px).
- **Code conditionnement**, **code mode d'approvisionnement** (GRC, ADO, BDC, ADR, MAX, LCE),
  **code origine du matériel** (AFn, AFe, FMs, DOc, CL1-4, PR1, AD1, AD2, AX1).
- Références constructeur : **NSN** (National Stock Number), **OEM**.

Certains de ces codes (soutien, position administrative) sont eux-mêmes utilisés comme **entrées
de règles de gestion** ailleurs (ex : `Sa = 0` pour un article en soutien arrêté/suspendu, RG8) —
ce ne sont donc pas que des champs d'affichage, plusieurs pilotent une logique métier absente.

---

## 5. Ce qui est déjà bien aligné, ou volontairement modernisé

- **SGS = "Service Gestion de Stock"** confirmé dans le glossaire — la Phase 1 de gouvernance des
  rôles (session du jour) a correctement identifié ce domaine.
- **Nomenclature 12 caractères** (Classe + Sous-classe + Constructeur/Catégorie + Série + Item) —
  conforme au document 4, vérifié en détail lors d'une session précédente.
- **Réforme** : la distinction Complet/Non-complet et surtout **Réforme complète vs Déclassement
  vs Réparation** existe bien dans `DossierReforme.decisionCommission` — la structure de fond du
  CCF est respectée, même si le formalisme (PV1-PV5, fiches M42/M43/M45/M30) est simplifié.
- **Double vérification Gestionnaire → Commandant** avant transmission à GAMMA3 (fonctionnalité
  ajoutée le 2026-09-04) : ~~n'est décrite nulle part dans le CCF fourni~~ — **correction du
  2026-09-11, après que l'utilisateur a partagé le tableau officiel des groupes d'utilisateurs du
  CCF (voir §7)** : le CCF prévoit bien un groupe **CDT (Commandant)**, distinct du **DDM
  (Détenteur de Matériel)**, dont le rôle est précisément de valider la demande du DDM de son
  unité avant sa transmission à la DA. GAMMA 3 est donc **conforme au CCF sur ce point** — ce
  n'était pas un écart, c'était un trou dans mon extraction initiale des documents source (ce
  tableau, structuré en image/graphique dans le `.docx`, n'a pas été capturé par l'extraction
  texte utilisée pour cet audit — voir §7 pour la limite méthodologique).
- **Gouvernance SUPER_ADMIN, audit trail, sauvegarde automatisée, sécurité de session** : hors
  périmètre du CCF (document d'origine papier/pré-dématérialisation), logiquement absents de la
  spécification mais cohérents avec une modernisation du système d'information.

---

## 6. Décisions à prendre avant toute implémentation

Le CCF est un document **institutionnel et procédural lourd** (commissions, PV numérotés,
formulaires papier F01 à F11 par procédure). Une partie de ces lacunes correspond à de la
paperasse qu'une modernisation vise justement à simplifier — il serait contre-productif de tout
réimplémenter à l'identique sans validation. Voir les questions posées en fin de conversation.

---

## 7. Précision reçue de l'utilisateur (2026-09-11) — groupes d'utilisateurs officiels du CCF

L'utilisateur a partagé une capture d'écran du tableau officiel « Groupe d'utilisateur /
désignation » du CCF, **non capturé par l'extraction texte** utilisée pour cet audit (ce tableau
est structuré en image/graphique dans le `.docx` source, invisible à une extraction de
`word/document.xml` brut — limite méthodologique à noter pour toute relecture future du CCF).

| Code CCF | Désignation officielle | Niveau |
|---|---|---|
| **DDM** | Détenteur de matériel | Unité |
| **CDT** *(absent du tableau, confirmé par l'utilisateur)* | Commandant — valide la demande du DDM avant transmission à la DA | Unité |
| **GEST** | Gestionnaire | DA |
| **MAG** | Magasin | DA |
| **ADEST** | Aire de distribution | DA |
| **SC** | Section comptabilité | DA |
| **SCS** | Section commande et suivi | DA |
| **SEP** | Section étude et prévision | DA |
| **SeRef** | Section réforme | DA |
| **SR** | Section recette | DA |

Précisions de l'utilisateur : le DDM appartient à l'unité (confirmé — voir correction §5
ci-dessus) ; les 8 autres groupes DA (hors DDM/CDT) sont **inspirés des sections actuelles de la
Division Approvisionnement**, pas une invention du CCF déconnectée de l'organisation réelle.

### Correspondance avec le modèle de rôles GAMMA 3 actuel

| Code CCF | Équivalent GAMMA 3 | Statut |
|---|---|---|
| DDM | `UNIT_USER` (`FonctionUnite=GESTIONNAIRE`) | ✅ Conforme |
| CDT | `UNIT_USER` (`FonctionUnite=COMMANDANT`) | ✅ Conforme (voir correction §5) |
| MAG | `DA_MANAGER` | ✅ Conforme |
| GEST | `ADMIN` / `SUPER_ADMIN` (rôle généraliste) | ⚠️ Fusionné — le CCF semble désigner ici un rôle DA précis (proche du "chef SGS" cité dans plusieurs RG), pas la totalité des pouvoirs `ADMIN`/`SUPER_ADMIN` |
| ADEST | *(aucun)* | ⚠️ Fondu dans `DA_MANAGER`/`ADMIN` — le "chef de l'aire de distribution" (P05 OP4/OP6) n'est pas un rôle distinct aujourd'hui |
| SC | *(aucun)* | ⚠️ Fondu dans `ADMIN`/`SUPER_ADMIN` — porteur naturel d'une future Procédure P07 (comptabilité/collationnement) |
| SCS | *(aucun)* | ⚠️ Fondu — porteur naturel de la Procédure P02 (commande et suivi), jugée hors périmètre logistique au §1 |
| SEP | *(aucun)* | ⚠️ Fondu — **porteur naturel de la future Procédure P01** (prévisions), voir la proposition dédiée |
| SeRef | *(aucun)* | ⚠️ Fondu dans `ADMIN`/`SUPER_ADMIN` — porteur naturel de la Procédure P06 (réforme), déjà bien couverte fonctionnellement (§5) mais sans rôle dédié |
| SR | *(aucun)* | ⚠️ Fondu dans `ADMIN`/`SUPER_ADMIN`/`DA_MANAGER` — porteur naturel de la Procédure P03 (réception), déjà bien couverte fonctionnellement mais sans rôle dédié |

**Constat :** GAMMA 3 a fusionné les 8 rôles internes de la DA (GEST/MAG/ADEST/SC/SCS/SEP/SeRef/SR)
en seulement 2 (`ADMIN` + `DA_MANAGER`, `MAG` mis à part qui correspond proprement à
`DA_MANAGER`). Ce n'est pas nécessairement une erreur — une petite structure n'a pas forcément
intérêt à cloisonner 8 comptes différents pour 8 fonctions administratives — mais c'est une
divergence délibérée à documenter, et une option à considérer si l'utilisateur veut un jour un
cloisonnement plus fin (par ex. limiter qui peut valider un dossier de réforme vs qui peut
lancer une prévision). **Pas d'implémentation proposée ici** : à trancher avec l'utilisateur,
au même titre que les 4 décisions du §6 — voir aussi la proposition P01/P07
(`docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md`), où le rattachement de ces deux écrans à
un rôle SEP/SC dédié (plutôt qu'à `ADMIN`/`SUPER_ADMIN` génériquement) mériterait d'être
reconsidéré à la lumière de ce tableau.
