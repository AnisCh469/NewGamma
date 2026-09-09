# Script de démarrage pour GAMMA3.0 (Spring Boot + Angular + PostgreSQL) sur Windows 11
#
# IMPORTANT : ce script doit fonctionner quel que soit le dossier depuis lequel il est
# lancé (ex: C:\WINDOWS\System32> D:\MyDeveloppement\NewGamma\start.ps1). $PSScriptRoot
# est le dossier où SE TROUVE ce fichier .ps1 (toujours correct), jamais le dossier
# courant du terminal qui l'appelle — utilisé ci-dessous pour construire des chemins
# absolus vers gamma3-backend/ et gamma3-frontend/, faute de quoi les fenêtres PowerShell
# lancées par Start-Process héritent du dossier courant de l'appelant (souvent
# C:\WINDOWS\System32) et échouent avec "Cannot find path ...\gamma3-backend".
$backendDir = Join-Path $PSScriptRoot "gamma3-backend"
$frontendDir = Join-Path $PSScriptRoot "gamma3-frontend"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "   Initialisation du Système GAMMA 3.0       " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. Vérification des conteneurs Docker (PostgreSQL et SQL Server)
Write-Host "[1/3] Vérification des bases de données Docker..." -ForegroundColor Yellow
$dockerCheck = docker ps --filter "name=gamma3_" --format "{{.Names}}: {{.Status}}"
if ($dockerCheck) {
    Write-Host "Bases de données actives :" -ForegroundColor Green
    $dockerCheck | ForEach-Object { Write-Host " - $_" -ForegroundColor Green }
} else {
    Write-Host "Lancement des conteneurs Docker de base de données..." -ForegroundColor Yellow
    docker-compose up -d
}

# 2. Lancement du Backend Spring Boot
# -WorkingDirectory (chemin absolu $backendDir) : garantit que la nouvelle fenêtre
# démarre déjà dans gamma3-backend, indépendamment du dossier courant de l'appelant.
Write-Host "[2/3] Lancement du Backend (Spring Boot)..." -ForegroundColor Yellow
Start-Process powershell -WorkingDirectory $backendDir -ArgumentList "-NoExit", "-Command", "Write-Host 'Démarrage du Backend...' -ForegroundColor Cyan; .\gradlew.bat bootRun"

# 3. Lancement du Frontend Angular
Write-Host "[3/3] Lancement du Frontend (Angular)..." -ForegroundColor Yellow
Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList "-NoExit", "-Command", "Write-Host 'Démarrage du Frontend...' -ForegroundColor Cyan; npm run start"

Write-Host "=============================================" -ForegroundColor Green
Write-Host "Services en cours de lancement dans des terminaux séparés !" -ForegroundColor Green
Write-Host " - Backend API : http://localhost:8080" -ForegroundColor Yellow
Write-Host " - Frontend UI : http://localhost:4200" -ForegroundColor Yellow
Write-Host "=============================================" -ForegroundColor Green
