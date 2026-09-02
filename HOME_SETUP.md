# 🏠 Gamma3 - Home Setup Guide

This guide explains how to set up the project on your home computer.

## 1. Prerequisites
Install the following tools:
*   **Git**: [Download here](https://git-scm.com/)
*   **Docker Desktop**: [Download here](https://www.docker.com/products/docker-desktop/)
*   **Node.js (v18+)**: [Download here](https://nodejs.org/)
*   **Java 17 (JDK)**: Recommended for the backend.
*   **IDE**: IntelliJ IDEA (recommended) or VS Code.

## 2. Get the Code
Open a terminal and run:
```bash
git clone https://github.com/AnisIA1977/GAMMA3.git
cd GAMMA3
git checkout gamma3-init-331245186370893602
```

## 3. Database Setup (Docker)
1.  Make sure Docker Desktop is running.
2.  In the `GAMMA3` root folder, run:
    ```bash
    docker-compose up -d
    ```
3.  **Note for SQL Server**: If you need the legacy data, copy your `Backup GAMMA2` folder to your home computer and update the path in `docker-compose.yml` (line 25).

## 4. Run the Backend
1.  Open `gamma3-backend` in your IDE.
2.  Wait for Gradle to sync dependencies.
3.  Run `Gamma3BackendApplication.java`.
4.  The backend will automatically create tables and seed demo data.

## 5. Run the Frontend
1.  Open a terminal in `gamma3-frontend`.
2.  Install dependencies:
    ```bash
    npm install
    ```
3.  Start the app:
    ```bash
    npm start
    ```
4.  Open [http://localhost:4200](http://localhost:4200).

## 6. Syncing your work
*   **Before leaving home/office**:
    ```bash
    git add .
    git commit -m "Describe your changes"
    git push
    ```
*   **When arriving**:
    ```bash
    git pull
    ```
