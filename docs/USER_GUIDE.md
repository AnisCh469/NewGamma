# 📘 Guide Utilisateur — Gamma 3

Ce guide est destiné aux utilisateurs finaux pour les accompagner dans la prise en main de l'application Gamma 3.

---

## 🖨️ Impression des documents officiels

Depuis 2026-09-09, **tous les documents officiels sont produits sous forme de fichier PDF
par le serveur** — plus d'impression directe de la page web.

**Comment ça marche :** quand vous cliquez sur un bouton d'impression (par ex. « Imprimer le BS »,
« Étiquette », « Bon de commande », « Imprimer PV »…), un **nouvel onglet** s'ouvre avec le
document en PDF. Depuis le lecteur PDF, vous pouvez :
* **imprimer** (bouton imprimante du lecteur) — le rendu sur papier est identique à l'écran,
  sans en-tête ni pied de page ajoutés par le navigateur ;
* **enregistrer** le fichier (pour l'archiver ou le joindre à un courriel).

> Si votre navigateur bloque l'ouverture de l'onglet, autorisez les fenêtres pop-up pour
> l'adresse de GAMMA 3.

**Documents disponibles :**

| Document | Où le trouver |
|---|---|
| Bon de Sortie Matériels | Console d'arbitrage / Fiche unité — sur une demande approuvée |
| Bon d'Entrée en Magasin | Détail d'une réception |
| Procès-Verbal de Réforme | Console de réforme / Fiche unité |
| Bon de Commande Fournisseur | Fiche fournisseur → historique des commandes |
| Fiche Article / Fiche Fournisseur / Fiche Unité | Bouton « Imprimer » de la fiche |
| Étiquette article / Étiquette unité | Bouton « Étiquette » de la fiche — petit format autocollant (70 × 40 mm) avec code QR et code-barres, à apposer sur l'équipement |

Chaque document porte l'en-tête institutionnel (Ministère de la Défense Nationale / Marine
Nationale Tunisienne / Division de l'Approvisionnement), un numéro de référence, un code QR
de traçabilité et les cases de visa. Le PDF peut être ré-imprimé à l'identique à tout moment.

---

## 🔍 Recherche d'Articles dans le Catalogue

### Recherche Générale
* Vous pouvez rechercher des articles par leur **désignation** en saisissant un ou plusieurs mots-clés dans la barre de recherche principale en haut du catalogue.
* **Astuce de productivité :** Appuyez sur la combinaison de touches `Ctrl + K` (ou `Cmd + K` sur Mac) à tout moment pour focaliser automatiquement le curseur sur la barre de recherche.

### Recherche Spécifique par Nomenclature (12 Caractères)
* **Barre dédiée :** Une barre de recherche avec l'icône `#` (Hashtag) est spécialement conçue pour rechercher précisément un article grâce à sa nomenclature officielle à 12 caractères (ex: `130514123456`).
* **Indicateur de longueur dynamique :** Un indicateur visuel situé à droite du champ affiche en temps réel le nombre de caractères saisis sur 12 (ex: `6/12` en orange). Dès que les 12 caractères requis sont saisis, le badge devient vert (`12/12`), validant visuellement la structure réglementaire.
* **Debouncing intelligent :** Pour garantir la réactivité de l'application, la recherche est déclenchée automatiquement 300 millisecondes après la saisie du dernier caractère, évitant ainsi les ralentissements.
* **Exclusion mutuelle :** Saisir un texte dans la barre de recherche par nomenclature efface automatiquement la recherche par désignation, et inversement, afin de vous garantir une navigation claire et ciblée.

## ➕ Création d'un Nouvel Article (Administration)

* **Qui peut créer un article :** Le bouton `Nouvel Article`, situé en haut à droite du Catalogue, n'est visible que pour les utilisateurs de l'administration (il est masqué pour les comptes d'unité clientes) ; le serveur applique la même restriction même si l'appel était tenté autrement.
* **Classification en cascade (Classe → Sous-classe → Catégorie → Série → Code Article) :** Conformément à la règle de gestion du Cahier des Charges ("chaque article doit appartenir à une classe/sous-classe/catégorie bien définie"), la nomenclature à 12 caractères ne se saisit plus en un seul champ libre :
  * **Classe** : liste fixe des 8 classes officielles de matériel (Mécanique, Électronique, Armes, Électrotechnique, Matière d'œuvres, Protection/Plongée, Quincaillerie, Équipement Général de Vie à Bord).
  * **Sous-classe**, **Catégorie**, **Série** : listes déroulantes qui proposent automatiquement les codes déjà utilisés dans le catalogue (avec un exemple d'article et leur nombre) dès que le niveau parent est choisi. Vous pouvez aussi taper directement un nouveau code (2 caractères) si l'équipement n'a jamais été catalogué.
  * **Code Article** : 4 chiffres. Le bouton `↻` à côté du champ suggère automatiquement le prochain code libre dans la série choisie (calculé sur les articles déjà enregistrés), que vous pouvez modifier si besoin.
  * La nomenclature complète reconstituée s'affiche en aperçu sous les 4 champs une fois tous renseignés.
* **Désignation obligatoire.** Le bouton `Enregistrer` reste désactivé tant que la classification complète et la désignation ne sont pas renseignées.
* **Nomenclature unique :** Si la combinaison saisie correspond à un article déjà existant, un message d'erreur explicite vous l'indique et la création est refusée.
* **Champs optionnels :** Prix unitaire, stock de sécurité, unité de gestion, type (Consommable / Non Consommable) et classe de danger (GHS).
* **Photo et document (PDF) :** Peuvent être ajoutés directement depuis ce formulaire — ils sont envoyés juste après la création de l'article. Vous pourrez toujours les ajouter ou les remplacer plus tard depuis la fiche détaillée de l'article.

---

---

## 📦 Arbitrage & Retrait Physique des Matériels (Zero-Trust)

Afin d'assurer la traçabilité militaire et la séparation stricte des privilèges :

### Qui a accès à la console d'arbitrage ?
La page **Arbitrage** (`/distribution`) est réservée à l'**Administrateur** et aux **Gestionnaires de soute (DA_MANAGER)**. Un compte d'unité (Gestionnaire ou Commandant) ne peut pas y accéder — s'il tente d'ouvrir l'adresse directement, il est redirigé vers le catalogue. Chaque Gestionnaire de soute ne voit et ne traite **que** les demandes routées vers **sa propre soute**.

### Pour l'Administrateur Système (ADMIN)
* **Arbitrage centralisé :** L'administrateur peut arbitrer n'importe quelle demande de matériel soumise par les unités.
* **Retrait physique côté SGS :** L'administrateur joue le rôle de magasinier pour la soute centrale **SGS** (aucun DA_MANAGER n'y étant rattaché). Pour les demandes routées vers SGS, c'est donc lui qui voit et actionne le bouton `Confirmer Retrait`. Pour toutes les soutes DA locales (SM1, SM2…), il ne peut pas confirmer le retrait — l'indicateur bleu `EN ATTENTE RETRAIT MAGASIN` s'affiche et l'action revient au magasinier local.

### Pour le Gestionnaire de Soute (DA_MANAGER)
* **Indication et Visibilité :** Dès que le Commandant de l'unité valide la demande, celle-ci est routée vers la soute la plus proche et apparaît sur la console d'arbitrage de son gestionnaire.
* **Confirmation du Retrait Réel :** Le bouton vert `Confirmer Retrait` est visible et activable **uniquement par le magasinier assigné à cette soute**. Cliquer dessus effectue la sortie de stock définitive, enregistre le mouvement de prélèvement et transfère les dotations en service réel à bord.

### Escalade vers SGS (stock local insuffisant)
Si la soute du Gestionnaire ne peut pas couvrir une demande, il clique sur **`Escalader vers SGS`** (visible sur les demandes en attente d'arbitrage) et saisit un motif. La demande passe alors au statut **`ESCALADÉE — TRANSFERT SGS`** et disparaît de sa liste d'actions.

L'**Administrateur** voit ces demandes escaladées avec le bouton **`Statuer sur le transfert`** :
* **Valider le transfert** → le déficit est transféré automatiquement de SGS vers la soute locale (deux mouvements de stock enregistrés : sortie SGS + entrée soute locale), la demande passe en approbation et un bon de sortie est généré pour le retrait.
* **Refuser le transfert** (motif obligatoire) → la demande est rejetée, aucun stock n'est déplacé, l'unité est notifiée.

Une fois le transfert validé, le retrait physique se déroule normalement à la soute locale (bouton `Confirmer Retrait` du magasinier local).


---

## 👤 Administration > Gestion des Utilisateurs

* **Accès :** Un nouveau lien `Utilisateurs` apparaît dans le menu principal, visible **uniquement pour les comptes ADMIN**. Aucun autre rôle ne peut créer, modifier, bloquer ou débloquer un compte — même un Gestionnaire de Magasin (DA_MANAGER) doit passer par un ADMIN.
* **Pourquoi il n'y a pas d'inscription libre-service :** Pour un système militaire, chaque compte doit être créé sciemment par un administrateur ; il n'existe donc aucun bouton "Créer un compte" sur l'écran de connexion.

### Créer un compte
1. Cliquez sur `Nouveau Compte` en haut de l'écran `Utilisateurs`.
2. Renseignez le **Matricule** (identifiant de connexion), un **mot de passe** initial (4 caractères minimum — l'utilisateur pourra le changer via son profil), le **Nom Complet**, et le **Rôle** :
   * **UNIT_USER** (compte d'une unité cliente) : le **Matricule doit être strictement identique au Code** d'une Unité Utilisatrice déjà créée dans `Tiers > Unités`. Si l'unité n'existe pas encore, créez-la d'abord dans cet écran, puis revenez créer son compte de connexion ici. Un message d'erreur explicite vous guidera si le code ne correspond à aucune unité.
   * **DA_MANAGER** (gestionnaire de soute) : un **Magasin** doit obligatoirement être sélectionné dans la liste déroulante — ce magasin devient la soute de rattachement de ce gestionnaire.
   * **ADMIN** : aucun champ supplémentaire requis.
3. Cliquez sur `Enregistrer`. Un message d'erreur s'affiche si le matricule existe déjà, ou si un champ obligatoire manque.

### Modifier un compte
* Le bouton crayon (✏️) permet de modifier le Nom Complet, le Rôle et, le cas échéant, le Magasin d'un compte existant. Le **Matricule ne peut pas être changé** depuis cet écran (il faudrait recréer le compte).

### Bloquer / débloquer un compte
* Le bouton cadenas permet de **bloquer** immédiatement un compte (ex : agent ayant quitté son poste) — la personne ne peut alors plus se connecter, **et son jeton de connexion déjà ouvert est immédiatement invalidé** (effet instantané, pas besoin d'attendre une expiration).
* Il ne s'agit jamais d'une suppression : tout l'historique du compte (mouvements de stock, demandes, etc.) reste intact, et le compte peut être **débloqué** à tout moment par le même bouton.
* **Vous ne pouvez pas bloquer votre propre compte** (le bouton est désactivé sur votre propre ligne, identifiée par la mention `(vous)`), afin d'éviter de vous retrouver bloqué hors du système.

### Réinitialiser un mot de passe
* Le bouton clé permet à un ADMIN de définir directement un nouveau mot de passe pour un compte, par exemple si l'utilisateur l'a oublié (il n'existe pas de fonctionnalité "mot de passe oublié" en libre-service sur ce système cloisonné).

### Colonnes du tableau
* **2FA** : indique si la double authentification est activée pour ce compte (icône verte) ou non (icône grise) — activée par l'utilisateur lui-même depuis son profil, un ADMIN ne peut pas l'activer à sa place.
* **Statut** : `Actif` (badge vert) ou `Bloqué` (badge rouge).

---

## 🔍 Administration > Audit Migration Gamma2

* **Accès :** Réservé aux comptes ADMIN, via le lien `Audit Gamma2` du menu principal.
* **À quoi ça sert :** Cet écran interroge en direct l'ancienne base de données Gamma2 (SQL Server) et liste toutes ses tables avec leur nombre de lignes, à comparer avec les compteurs actuels de Gamma3 affichés juste à côté — pour repérer d'un coup d'œil des données de l'ancien système qui n'auraient jamais été reprises (mouvements de stock historiques, anciens bons de sortie, anciennes réceptions, etc.).
* **Si le bandeau orange "Impossible de joindre la base Gamma2" apparaît :** cela signifie que le conteneur SQL Server de l'ancien système (Gamma2) n'est pas démarré, ou que son adresse réseau a changé. Vérifiez que ce conteneur tourne, puis cliquez sur `Relancer l'audit`.
* **Section "Mécanismes de migration connus" :** rappelle les deux seules données déjà reprises depuis Gamma2 à ce jour — le catalogue d'articles (une seule fois, manuellement) et le Plan d'Armement par unité (rejouable à tout moment via l'écran de migration existant).

---

## ✅ Double Vérification des Demandes de Matériel (Gestionnaire + Commandant)

* **Principe :** Chaque unité navigante ou corps dispose désormais de **deux comptes de connexion distincts** :
  * Le **Gestionnaire** compose et soumet les demandes de matériel (catalogue, panier, "Demander du Matériel").
  * Le **Commandant** (chef d'unité) valide ou refuse chaque demande soumise par le Gestionnaire de SA unité, **avant** qu'elle n'atteigne la Division Approvisionnement (DA_MANAGER/ADMIN).
* **Une demande subit donc toujours deux vérifications** avant d'être traitée par GAMMA3 : celle du Commandant de l'unité, puis celle du gestionnaire de soute GAMMA3.

### Pour le Gestionnaire
* Compose sa demande depuis le Catalogue comme auparavant. Après soumission, la demande apparaît avec le statut "En attente de validation du Commandant" — **elle n'est pas encore visible de la DA**.
* Si le Commandant refuse, la demande apparaît dans l'historique avec le motif du refus. Le Gestionnaire peut alors soumettre une nouvelle demande corrigée.

### Pour le Commandant
* Nouvel écran **`Valider les Demandes`**, visible uniquement pour les comptes Commandant, listant les demandes de sa propre unité en attente de décision.
* **Accepter** : la demande est transmise à la DA (Division Approvisionnement) exactement comme avant cette évolution — routage vers le magasin le plus proche, réservation sur le Plan d'Armement.
* **Refuser** : un motif est obligatoire. La demande n'atteint jamais la DA ; elle reste visible dans l'historique de l'unité avec ce motif.
* Le Commandant ne peut PAS composer de demande lui-même (le bouton "Demander du Matériel" ne lui est pas proposé) — sa fonction se limite à la validation.

### Pour l'Administrateur (création des comptes)
* Dans `Administration > Gestion des Utilisateurs`, un compte UNIT_USER exige maintenant de préciser :
  * l'**Unité Utilisatrice** (liste déroulante — l'unité doit déjà exister dans `Tiers > Unités`),
  * la **Fonction** : Gestionnaire ou Commandant.
* Le matricule est libre pour les deux comptes (il n'est plus obligatoirement égal au code de l'unité).
* Un seul compte actif par (unité, fonction) est autorisé : impossible de créer deux Gestionnaires ou deux Commandants actifs pour la même unité — bloquez d'abord l'ancien compte pour en créer un nouveau.
