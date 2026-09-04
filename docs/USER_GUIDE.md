# 📘 Guide Utilisateur — Gamma 3

Ce guide est destiné aux utilisateurs finaux pour les accompagner dans la prise en main de l'application Gamma 3.

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

### Pour l'Administrateur Système (ADMIN)
* **Arbitrage centralisé :** L'administrateur peut arbitrer n'importe quelle demande de matériel soumise par les unités et décider d'attribuer une soute spécifique (ex: Magasin Centre SM1 ou Central SGS) pour le prélèvement.
* **Aucun retrait physique direct :** L'administrateur **n'a pas l'autorisation** de confirmer la remise physique des équipements à l'unité (sécurité Zero-Trust). Sur la console d'arbitrage, les demandes en attente de livraison affichent l'indicateur bleu `EN ATTENTE RETRAIT MAGASIN`.

### Pour le Gestionnaire de Soute (DA_MANAGER)
* **Indication et Visibilité :** Dès que l'administrateur (ou le système) valide et attribue un Bon de Sortie à sa soute, la demande apparaît instantanément sur la console d'arbitrage du gestionnaire de cette soute (ex: Magasin Centre).
* **Confirmation du Retrait Réel :** Le bouton vert `Confirmer Retrait` est visible et activable **uniquement par le magasinier assigné à cette soute**. Cliquer dessus effectue la sortie de stock définitive, enregistre le mouvement de prélèvement et transfère les dotations en service réel à bord.

