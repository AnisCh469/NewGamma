# Script de démarrage pour GAMMA3.0 (Spring Boot + Angular + PostgreSQL) sur Windows 11

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
Write-Host "[2/3] Lancement du Backend (Spring Boot)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Démarrage du Backend...' -ForegroundColor Cyan; cd gamma3-backend; .\gradlew.bat bootRun"

# 3. Lancement du Frontend Angular
Write-Host "[3/3] Lancement du Frontend (Angular)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Démarrage du Frontend...' -ForegroundColor Cyan; cd gamma3-frontend; npm run start"

Write-Host "=============================================" -ForegroundColor Green
Write-Host "Services en cours de lancement dans des terminaux séparés !" -ForegroundColor Green
Write-Host " - Backend API : http://localhost:8080" -ForegroundColor Yellow
Write-Host " - Frontend UI : http://localhost:4200" -ForegroundColor Yellow
Write-Host "=============================================" -ForegroundColor Green
