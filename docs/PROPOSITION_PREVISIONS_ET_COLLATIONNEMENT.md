# 📋 Proposition — Procédure P01 (Prévisions) & Procédure P07 (Comptabilité/Collationnement)

**Date :** 2026-09-11
**Contexte :** Suite de l'audit de conformité CCF (`docs/AUDIT_CONFORMITE_CCF_2026-09-11.md`),
réponse à la décision de l'utilisateur : *« Les planifier comme prochains chantiers — je prépare
une proposition détaillée (modèle de données, écrans) à valider avant implémentation, comme pour
la gouvernance des rôles. »*

> **Statut : PROPOSITION, non implémentée.** Comme pour `PROPOSITION_GOUVERNANCE_ROLES.md`, ce
> document attend une décision explicite avant toute exécution — voir « Décisions à prendre » en
> fin de document.

---

## 1. Pourquoi traiter ces deux procédures ensemble

P01 (Prévisions) et P07 (Comptabilité/Collationnement) sont les deux seules procédures du CCF
encore **entièrement absentes** de GAMMA 3 (voir le rapport d'audit). Elles partagent un même
principe : ce sont des **cycles périodiques de contrôle**, pas des opérations ponctuelles comme
une demande ou une réception — ce qui change la nature de ce qu'il faut construire (des écrans de
campagne/rapport, pas juste un formulaire de plus).

Les deux procédures, telles que décrites dans le CCF, sont aussi **très institutionnelles** :
commissions désignées, notes de service numérotées, formulaires papier (F01 à F11 par procédure),
validations EMAM/BL à chaque étape. Le paragraphe 6 propose, pour chacune, une version
**allégée** qui capture la valeur opérationnelle réelle (les seuils qui déclenchent une alerte,
la détection d'écart) sans reconstruire tout le formalisme papier — sauf demande contraire.

---

## 2. Procédure P01 — Prévisions

### 2.1 Ce que le CCF décrit

* **Seuil d'alerte (Sa)** — le point de commande. Formule différente selon le type d'article :
  * Consommable (allocation) : `Sa = Cmoy3 × n` (consommation moyenne des 3 dernières années ×
    délai de réapprovisionnement).
  * Armement (dotation) : `Sa = 10% du parc × n`.
  * `Sa = 0` si l'article est en soutien **arrêté** ou **suspendu** (déjà modélisable : voir
    `Item.codeSoutien`, ajouté le 2026-09-11).
* **Seuil de sécurité (Ss) et seuil minimal (Smin)** — uniquement pour un article **Critique**
  (déjà modélisable : `Item.criticite`, ajouté le 2026-09-11) : `Ss = Sa / 3`, `Smin = Sa - Ss`.
  **Le `stockSecurite` actuel de GAMMA 3 (déjà utilisé pour bloquer une distribution depuis
  aujourd'hui) est aujourd'hui une valeur saisie à la main — le CCF la calcule à partir de Sa.**
  C'est le premier arbitrage à trancher (§6.1).
* **Stock potentiel** = stock réel + commandé − réservé − sécurité.
* **Prévision annuelle par lissage exponentiel** : `Pn = Pn-1 + α(Rn-1 - Pn-1)`, avec `Pn ≥ Sa`.
* **Cycle budgétaire PADI/CDMT** : un cycle annuel institutionnel (janvier → prévisions, mars →
  validation EMAM/BL, décembre → PADI de l'année suivante) impliquant plusieurs directions
  (EMAM/BL, DAT, divisions techniques) — hors du système d'information à proprement parler.

### 2.2 Ce qui existe déjà en GAMMA 3 et peut être réutilisé

* `Item.stockSecurite`, `Item.criticite`, `Item.codeSoutien` (Phase FSA, 2026-09-11).
* L'historique complet des mouvements de stock (`mouvements_stock`, 163 594 lignes migrées de
  Gamma2 + les mouvements Gamma3 réels) — la donnée brute nécessaire au calcul de `Cmoy3` existe
  déjà, il ne manque que le calcul.
* Le Plan d'Armement (`plans_armement`) donne le "parc" (matériel en service) par unité, agrégeable
  par article.

### 2.3 Proposition d'implémentation (version allégée)

1. **Calcul de `Cmoy3`** : requête agrégée sur `mouvements_stock` (sorties des 3 dernières
   années / 3), par article — pas un champ stocké, un calcul à la demande (ou recalculé une fois
   par jour par une tâche planifiée, à trancher selon le volume).
2. **Calcul de `Sa`** : appliqué à la volée à partir de `Cmoy3` (ou du parc, selon
   `typeConsommabilite`) × le délai `n` (nouveau champ `Item.delaiApprovisionnementAnnees`, déjà
   ajouté) — pas de nouveau champ stocké, un service de calcul.
3. **Nouvel écran « Prévisions »** (Administration, SUPER_ADMIN/ADMIN) : liste des articles ayant
   franchi leur `Sa`, triée par urgence, avec le stock potentiel calculé — la version numérique de
   l'« état de prévision des nouveaux besoins » du CCF (OP2/OP3 de P01), sans le formalisme des
   fiches F01-F04.
4. **Hors périmètre (sauf demande explicite)** : le cycle budgétaire PADI/CDMT complet
   (validations EMAM/BL, réunions de répartition budgétaire) — reste un processus humain/papier,
   GAMMA 3 se contente de produire la liste d'alerte qui l'alimente.

---

## 3. Procédure P07 — Comptabilité / Collationnement annuel

### 3.1 Ce que le CCF décrit

* Un **registre d'inventaire** par unité/division/service, mis à jour à chaque mouvement de
  matériel non consommable.
* Un **planning annuel de collationnement** (septembre, approuvé par l'EMAM/BL).
* Une **commission** qui se déplace, compare le registre à la réalité physique et aux documents
  (C5/C5 bis, décisions de réforme, notes d'affectation…), signe un **PV de collationnement**.
* **Écarts constatés** : un déficit devient un C5/C5 bis (perte), un excédent un certificat M16.

### 3.2 Ce qui existe déjà en GAMMA 3 et peut être réutilisé

* `ReportController`/`InventaireReportDto` — donne déjà un état des stocks à un instant T.
* Le Plan d'Armement donne la dotation théorique par unité et par article.
* Le Journal d'Audit peut recevoir de nouveaux types d'événements pour tracer une campagne.

### 3.3 Proposition d'implémentation (version allégée)

1. **Nouvelle entité `CampagneCollationnement`** : unité concernée, date planifiée, statut
   (PLANIFIEE / EN_COURS / TERMINEE), membres de la commission (texte libre, comme
   `DossierReforme.membresCommission`).
2. **Comparaison automatique** : au lancement d'une campagne, GAMMA3 compare la dotation théorique
   (Plan d'Armement) au dernier inventaire/mouvement connu et liste les écarts — remplace la partie
   fastidieuse du travail de la commission (rassembler les documents), la commission garde son rôle
   de vérification physique.
3. **PV de collationnement numérique** : conclusion de la campagne (conforme / écart constaté +
   observations), généré en PDF comme les autres documents officiels du système
   (`docs/AUDIT_IMPRESSION.md`).
4. **Hors périmètre (sauf demande explicite)** : les formulaires C5/C5 bis et M16 nominatifs —
   proposé comme un simple champ "observations/écart" sur la campagne dans un premier temps,
   plutôt que de recréer ces documents spécifiques.

---

## 4. Ce que ces deux chantiers NE couvrent PAS (déjà couvert ailleurs ou hors périmètre)

* Le seuil de sécurité **appliqué au blocage d'une distribution** est déjà en production depuis
  cette session (voir `DistributionService.confirmerSortiePhysique`) — ce chantier ne fait que
  proposer de mieux **calculer** ce seuil, pas de construire le blocage lui-même.
* La sauvegarde/restauration, l'audit, la gouvernance des rôles : déjà livrés (Phases 1 et 2 de
  `PROPOSITION_GOUVERNANCE_ROLES.md`).

---

## 5. Effort estimé (ordre de grandeur, pas un chiffrage formel)

| Bloc | Effort relatif |
|---|---|
| P01 — calcul Sa/Cmoy3 (service backend) | Moyen — logique de calcul nouvelle mais données déjà présentes |
| P01 — écran « Prévisions » | Petit — un tableau filtrable, patron déjà utilisé ailleurs |
| P07 — entité `CampagneCollationnement` + workflow | Moyen — nouveau domaine, mais patron proche de `DossierReforme` |
| P07 — comparaison automatique des écarts | Moyen-Grand — logique de rapprochement à concevoir soigneusement |
| P07 — PV PDF | Petit — patron déjà en place (`DocumentDataService`) |

---

## 6. Décisions à prendre

1. **`stockSecurite` : rester une valeur saisie à la main, ou devenir calculée depuis `Sa` ?**
   Le CCF calcule `Ss = Sa/3` à partir de la consommation réelle. GAMMA3 utilise aujourd'hui une
   valeur entrée manuellement par l'administrateur. Recommandé : garder la saisie manuelle comme
   **valeur par défaut modifiable**, mais afficher la valeur calculée par le CCF à titre indicatif
   à côté — l'administrateur reste décisionnaire, mais voit la référence officielle.
2. **Le cycle budgétaire PADI/CDMT complet (P01) : à digitaliser, ou rester un processus externe ?**
   Recommandé : rester externe pour l'instant (implique EMAM/BL, DAT — des acteurs hors GAMMA3) ;
   ne construire que la liste d'alerte qui l'alimente.
3. **Le collationnement (P07) : campagne numérique légère, ou réplication complète du formalisme
   papier (C5/C5 bis/M16) ?** Recommandé : version légère dans un premier temps, les formulaires
   spécifiques pouvant être ajoutés plus tard si le besoin se confirme à l'usage.
4. **Priorité relative entre P01 et P07 :** lequel démarrer en premier, ou les deux en parallèle ?
   Recommandé : P01 d'abord — il complète directement une fonctionnalité déjà en production
   (blocage au seuil de sécurité) et a une valeur opérationnelle immédiate (alertes de
   réapprovisionnement), alors que P07 est un contrôle périodique (annuel) moins urgent.
5. **Rattacher les écrans « Prévisions » (P01) et « Collationnement » (P07) à `ADMIN`/`SUPER_ADMIN`
   génériquement, ou à un rôle dédié inspiré des groupes officiels du CCF ?** L'utilisateur a
   précisé le 2026-09-11 que le CCF distingue des groupes par section de la DA — **SEP** (Section
   Étude et Prévision, porteuse naturelle de P01) et **SC** (Section Comptabilité, porteuse
   naturelle de P07) — voir `docs/AUDIT_CONFORMITE_CCF_2026-09-11.md` §7. GAMMA 3 fusionne
   aujourd'hui ces sections dans `ADMIN`/`SUPER_ADMIN`. Recommandé : garder `ADMIN`/`SUPER_ADMIN`
   pour ces deux premiers écrans (cohérent avec le reste du système, pas de rupture de modèle en
   cours de route) — mais si l'utilisateur souhaite à terme un cloisonnement par section façon
   CCF (SEP/SC/SCS/SeRef/SR distincts), ce serait le bon moment de le décider avant que ces deux
   nouveaux écrans ne soient construits sur un modèle à refaire ensuite.
