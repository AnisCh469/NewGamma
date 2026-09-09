# Audit & refonte du système d'impression — GAMMA 3

> Rédigé le 2026-09-09. Ce document décrit l'état AVANT, la refonte réalisée, et la
> marche à suivre pour l'équipe qui reprend le projet.

---

## 1. Constat (avant le 2026-09-09)

L'application produisait ses documents de **deux façons incohérentes** :

### 1.1 PDF serveur (JasperReports) — 3 registres analytiques seulement
`ReportController` (`/api/v1/reports/**`) : inventaire valorisé, mouvements de stock,
dotations/consommation. PDF + Excel.
*Défauts :*
- le `.jrxml` est recompilé à chaque requête (lent) ;
- **aucun contrôle de rôle** sur `/reports/**` — un `UNIT_USER` peut télécharger l'inventaire
  valorisé de toute la Marine (à corriger, même logique que `DocumentController`) ;
- ⚠️ **les 3 modèles `.jrxml` sont actuellement CASSÉS** : ils utilisent `$Parameter{...}` au
  lieu de la syntaxe JasperReports `$P{...}` → `JRException` au runtime (HTTP 500). Bug
  pré-existant, non lié à la présente refonte. À corriger (`resources/reports/*.jrxml`) OU,
  mieux, migrer ces 3 registres vers le nouveau moteur HTML→PDF (voir §3.3) et supprimer
  la dépendance JasperReports.

### 1.2 `window.print()` du navigateur — TOUT le reste
Bon de sortie, bon d'entrée, PV de réforme, fiches et étiquettes (article, fournisseur,
unité) : chaque composant Angular avait son propre bloc HTML caché + sa feuille
`@media print`, révélé en ajoutant une classe sur `<body>` puis `window.print()`.

**Problèmes :**
1. `window.print()` imprime **la page du navigateur** → Chrome ajoute son propre en-tête
   (« 9/9/26, 15:38 — Gamma3Frontend ») et son pied de page, non supprimables de façon
   fiable en CSS. L'opérateur devait décocher « En-têtes et pieds de page » à chaque fois.
2. Rendu **non déterministe** : dépend du navigateur, de l'imprimante, des réglages du poste.
3. **Aucune archive** du document émis (impossible de ré-imprimer à l'identique).
4. **6 mises en page bricolées séparément**, sans identité institutionnelle commune.
5. **Étiquettes** : le bouton « Étiquette » ré-affichait la page en plus petit ; la taille
   réelle (ex. 70×40 mm) n'était pas gérée → sortie sur A4.
6. Le **Bon de Sortie** était dupliqué (console d'arbitrage **et** fiche unité).
7. Aucune **facture / bon de commande fournisseur** imprimable (fonctionnalité absente).

---

## 2. Refonte réalisée

**Tous les documents officiels sont désormais générés en PDF par le serveur**, à partir de
**modèles HTML/CSS Thymeleaf** (éditables et prévisualisables dans un navigateur, sans outil
spécialisé — contrairement aux `.jrxml`).

### 2.1 Pile technique ajoutée (`gamma3-backend/build.gradle`)
| Dépendance | Rôle |
|---|---|
| `spring-boot-starter-thymeleaf` | moteur de modèles HTML |
| `io.github.openhtmltopdf:openhtmltopdf-pdfbox` | HTML/CSS → PDF (aucun JavaScript, rendu déterministe) |
| `org.jsoup:jsoup` | normalise le HTML5 de Thymeleaf en XHTML strict attendu par openhtmltopdf |
| `com.google.zxing:core` + `javase` | codes QR et codes-barres Code 128, calculés côté serveur en PNG `data:` URI |

### 2.2 Classes (`tn.defense.gamma3.document`)
```
document/
├── api/DocumentController.java      → endpoints /api/v1/documents/**  (+ contrôle d'accès)
└── service/
    ├── DocumentDataService.java     → construit le "modèle" (Map) de chaque document
    │                                  (toute la navigation JPA, transaction lecture seule)
    ├── DocumentPdfService.java      → Thymeleaf -> jsoup -> openhtmltopdf -> PDF
    └── BarcodeService.java          → QR / Code 128 -> data:image/png;base64,...
```

### 2.3 Modèles (`gamma3-backend/src/main/resources/templates/documents/`)
```
fragments/common.html   → feuille de style + cartouche institutionnel (en-tête/pied) partagés
bon-sortie.html         bon-entree.html      pv-reforme.html
facture.html            fiche-fournisseur.html   fiche-unite.html   fiche-article.html
etiquette-article.html  etiquette-unite.html     (petit format 70×40 mm, @page dédié)
```
`spring.thymeleaf.cache: false` (dans `application.yml`) : l'équipe peut ajuster une mise en
page **sans redémarrer le backend**.

### 2.4 Endpoints
| Endpoint | Document | Accès |
|---|---|---|
| `GET /api/v1/documents/bon-sortie/{demandeId}` | Bon de Sortie Matériels | DA_MANAGER / ADMIN, + l'unité concernée |
| `GET /api/v1/documents/bon-entree/{bprId}` | Bon d'Entrée en Magasin | DA_MANAGER / ADMIN |
| `GET /api/v1/documents/pv-reforme/{dossierId}` | Procès-Verbal de Réforme | DA_MANAGER / ADMIN, + l'unité concernée |
| `GET /api/v1/documents/facture/{commandeId}` | Bon de Commande Fournisseur | DA_MANAGER / ADMIN |
| `GET /api/v1/documents/fiche-fournisseur/{id}` | Fiche Fournisseur | DA_MANAGER / ADMIN |
| `GET /api/v1/documents/fiche-unite/{id}` | Fiche Unité | DA_MANAGER / ADMIN, + l'unité elle-même |
| `GET /api/v1/documents/fiche-article/{id\|nomenclature}` | Fiche Article | tout utilisateur authentifié |
| `GET /api/v1/documents/etiquette/article/{id\|nomenclature}` | Étiquette article (70×40 mm) | tout utilisateur authentifié |
| `GET /api/v1/documents/etiquette/unite/{id}` | Étiquette unité (70×40 mm) | tout utilisateur authentifié |

Réponse : `application/pdf` en `Content-Disposition: inline` (le navigateur l'ouvre dans un
onglet, l'utilisateur imprime/enregistre depuis le lecteur PDF).

### 2.5 Frontend
`core/services/document.service.ts` — `ouvrir('bon-sortie/19')` : récupère le PDF en `blob`
(l'intercepteur JWT ajoute le jeton), l'ouvre dans un nouvel onglet. L'onglet est ouvert
**au moment du clic** (avant l'appel réseau) pour ne pas être bloqué par le bloqueur de pop-ups.

Boutons « Imprimer » / « Étiquette » recâblés dans : `console-arbitrage`, `unite-detail`,
`item-detail`, `fournisseur-detail` (+ nouveau bouton « Bon de commande » dans l'historique
des commandes), `reception-detail`, `reforme-console`.

---

## 3. Ce qui reste à faire (pour l'équipe)

### 3.1 Nettoyage (sans risque fonctionnel, non fait faute de temps)
Les anciens blocs HTML d'impression (`.print-document-container`, `.print-label-container`,
et les `<div>` cachés `printDemande` / `printDossierReforme`…) + leurs `@media print` +
les champs TypeScript associés sont **désormais morts** (jamais déclenchés). À supprimer dans :
`item-detail`, `fournisseur-detail`, `unite-detail`, `console-arbitrage`, `reforme-console`,
`reception-detail` (`.component.html` + `.component.css` + `.component.ts`). Retirer aussi la
dépendance front `angularx-qrcode` / `jsbarcode` **une fois** que plus aucun écran n'affiche
de QR à l'écran (attention : `unite-detail` s'en sert encore pour le QR de configuration 2FA —
à conserver).

### 3.2 Améliorations des modèles
- **Logo** : le cartouche (`fragments/common.html`) réserve la place mais n'affiche pas de
  logo. Ajouter le logo officiel en `data:` URI (base64) dans le fragment `entete`.
- **Police** : les PDF utilisent Helvetica (police standard PDF, couvre le français). Pour une
  police de marque, déposer un `.ttf` dans `resources/fonts/` et l'enregistrer via
  `PdfRendererBuilder.useFont(...)` dans `DocumentPdfService`.
- **Étiquettes** : format fixé à 70×40 mm. Pour un autre format (rouleau 62 mm, planche A4
  de N étiquettes), ajuster la règle `@page` de `etiquette-*.html` (et, pour une planche,
  générer N étiquettes dans une grille).
- Le bouton « Étiquette » de la **fiche fournisseur** n'a plus de sens (un fournisseur ne
  reçoit pas d'étiquette physique) — il ouvre actuellement la fiche ; à retirer du gabarit.

### 3.3 Sécurité / cohérence
- Ajouter un contrôle de rôle sur `ReportController` (`/api/v1/reports/**`) — même logique
  que `DocumentController`.
- Envisager de migrer les 3 registres Jasper vers le même moteur HTML→PDF pour n'avoir
  qu'une seule technologie de documents (et supprimer la dépendance JasperReports, lourde).

### 3.4 Documents éventuellement à ajouter (même patron)
- Bon de transfert inter-magasin (généré lors d'une escalade validée par SGS).
- État récapitulatif mensuel des sorties par unité.

---

## 4. Comment ajouter un nouveau document (recette)

1. **Modèle** : créer `resources/templates/documents/mon-document.html` — copier
   `bon-sortie.html`, garder les `th:replace` de l'en-tête et du pied.
2. **Données** : ajouter une méthode `monDocument(Long id)` à `DocumentDataService` qui
   renvoie une `Map<String,Object>` (types simples uniquement — pas d'entité JPA).
   Utiliser `base(titre, sousTitre, numero, date, contenuQr)` pour le squelette commun.
3. **Endpoint** : ajouter une méthode à `DocumentController` (choisir le bon helper d'accès :
   `exigerRole`, `verifierAccesLogistique`, `exigerAuthentifie`).
4. **Frontend** : `this.documentService.ouvrir('mon-document/' + id)` sur le bouton.
5. **Test** : `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/documents/mon-document/1 -o test.pdf`
