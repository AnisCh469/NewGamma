# 📋 GAMMA 3.0 — Système de Gestion du Matériel Naval

> **Système officiel de gestion et d'approvisionnement automatisé pour la Marine Nationale Tunisienne.**
> Refonte moderne de l'application legacy Gamma 2 (Silverlight + SQL Server).

---

## 🏛️ Architecture Technique

GAMMA 3.0 repose sur une architecture robuste, moderne et hautement sécurisée, adaptée aux contraintes intranet de la Marine :

*   **Backend (API REST & Services)** :
    *   **Langage** : Java 21 LTS
    *   **Framework** : Spring Boot 3.x
    *   **Sécurité** : Spring Security (JWT + 2FA TOTP autonome offline)
    *   **Accès aux données** : Spring Data JPA (Hibernate)
    *   **Outil de build** : Gradle
*   **Frontend (Interface Utilisateur)** :
    *   **Langage** : TypeScript
    *   **Framework** : Angular 17 (Architecture standalone, reactive signals)
    *   **UI/UX** : PrimeNG 17 + TailwindCSS (Mode dense premium, responsive, animations fluides)
    *   **Impression** : Templates d'impression A4 réglementaires et étiquettes thermiques via CSS `@media print`
*   **Base de Données** :
    *   **Relationnel** : PostgreSQL 16 (Hébergement cible)
    *   **Migration depuis Gamma 2** : Script pgloader automatisé pour l'extraction de 63 283 articles legacy SQL Server

---

## 📂 Structure du Projet

```
GAMMA3/
├── gamma3-backend/          # Code source Backend (Spring Boot + Gradle)
│   ├── src/main/java/       # Packages par domaine (auth, catalogue, stock, tiers, etc.)
│   └── build.gradle         # Dépendances et configuration Gradle
├── gamma3-frontend/         # Code source Frontend (Angular 17 Standalone)
│   ├── src/app/             # Composants Angular, services et routeur
│   └── package.json         # Dépendances Node.js et scripts npm
├── migration/               # Scripts et configuration pgloader (.load)
├── docker-compose.yml       # Configuration PostgreSQL locale pour le développement
└── start.ps1                # Script PowerShell de démarrage automatique unifié
```

---

## 🚀 Démarrage Rapide (Environnement de Développement)

### Prérequis
1.  **Java JDK 17 ou 21**
2.  **Node.js (v18+)**
3.  **Docker Desktop** (assurez-vous que le démon Docker est lancé)
4.  **PowerShell** (Windows 11)

### Lancement Automatique
Pour démarrer les bases de données (Docker PostgreSQL), compiler et exécuter le backend et le frontend dans des terminaux PowerShell séparés, exécutez simplement à la racine :
```powershell
./start.ps1
```

### Lancement Manuel

#### 1. Démarrer la Base de Données
```bash
docker-compose up -d
```

#### 2. Démarrer le Backend
```bash
cd gamma3-backend
./gradlew bootRun
```
*Le backend s'exécute sur `http://localhost:8080` (génère et alimente automatiquement les données de démo).*

#### 3. Démarrer le Frontend
```bash
cd gamma3-frontend
npm install
npm run start
```
*Le frontend s'exécute sur `http://localhost:4200`.*

---

## 🧪 Validation & Tests

### backend (JUnit 5 & Integration)
Pour valider les règles métier, la sécurité et les transactions :
```bash
cd gamma3-backend
./gradlew test
```

### Frontend (Compilation & Build)
Pour valider l'absence d'erreurs d'intégration et compiler l'application de production :
```bash
cd gamma3-frontend
npm run build
```

---

## 🔒 Sécurité et Conformité
*   **Contexte Militaire** : Conçu pour fonctionner sur intranet cloisonné, sans connexion Internet extérieure requise.
*   **Double Facteur (2FA)** : Algorithme TOTP RFC 6238 autonome (génération locale offline via QR Code).
*   **Audit Trail** : Journalisation exhaustive des actions et des mouvements logistiques.
