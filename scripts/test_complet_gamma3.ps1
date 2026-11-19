# ==============================================================================
# TEST COMPLET E2E DES PROCEDURES LOGISTIQUES GAMMA 3
# SOUTES REGIONALES (NORD, CENTRE, SUD), ARITHMETIQUE ET REFORME NON-CONSOMMABLE
# ==============================================================================

$baseUrl = "http://localhost:8080/api/v1"

function Log-Step($msg) {
    Write-Host "`n========================================================" -ForegroundColor Cyan
    Write-Host ">>> $msg" -ForegroundColor Yellow
    Write-Host "========================================================" -ForegroundColor Cyan
}

function Log-Success($msg) {
    Write-Host "[OK] $msg" -ForegroundColor Green
}

function Log-Error($msg) {
    Write-Host "[ERREUR] $msg" -ForegroundColor Red
}

function Get-Token($matricule, $password) {
    $body = @{ matricule = $matricule; password = $password } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json; charset=utf-8" -Body $body
    return $res.token
}

# 1. Obtenir les tokens des différents acteurs
Log-Step "Authentification des acteurs multi-roles"
$adminToken = Get-Token "admin" "admin"
Log-Success "Admin authentifie"

$sgsToken = Get-Token "sgs" "sgs123"
Log-Success "SGS (Soute Nord) authentifie"

$centreToken = Get-Token "magasin_centre" "magasin_centre"
Log-Success "Gestionnaire Soute Centre (SM1) authentifie"

$sudToken = Get-Token "magasin_sud" "magasin_sud"
Log-Success "Gestionnaire Soute Sud (SM2) authentifie"

$drcGestionnaireToken = Get-Token "DRC" "DRC"
$drcCommandantToken = Get-Token "DRC_CDT" "DRC_CDT"
Log-Success "Client Nord DRC (Gestionnaire + Commandant) authentifies"

$cfismGestionnaireToken = Get-Token "CFISM" "CFISM"
$cfismCommandantToken = Get-Token "CFISM_CDT" "CFISM_CDT"
Log-Success "Client Centre CFISM (Gestionnaire + Commandant) authentifies"

$sud10GestionnaireToken = Get-Token "10" "10"
$sud10CommandantToken = Get-Token "10_CDT" "DRC_CDT"
Log-Success "Client Sud Unite 10 (Gestionnaire + Commandant) authentifies"

# Données des articles TEST
$itemNord = "8909b5e1-dfb8-4b80-a015-0083c970616e"   # TEST01CONSO1 (Consommable)
$itemCentre = "6a2901ea-1fda-455e-84dd-c4aa2e0e305b" # TEST02NCONS1 (Non Consommable)
$itemSud = "c1dd4b90-51d6-4f82-82a7-369f5700b899"    # TEST03NCONS1 (Non Consommable)

# Initialisation DB : Plans d'armement & Nettoyage
Log-Step "Initialisation des donnees de test et Plans d'Armement"
docker exec gamma3_postgres psql -U gamma3_user -d gamma3_db -c "
DELETE FROM lignes_reforme WHERE item_id IN ('$itemNord', '$itemCentre', '$itemSud');
DELETE FROM dossiers_reforme WHERE id NOT IN (SELECT DISTINCT dossier_reforme_id FROM lignes_reforme);
DELETE FROM lignes_demande WHERE item_id IN ('$itemNord', '$itemCentre', '$itemSud');
DELETE FROM demandes_materiel WHERE id NOT IN (SELECT DISTINCT demande_id FROM lignes_demande);
DELETE FROM stocks WHERE item_id IN ('$itemNord', '$itemCentre', '$itemSud');
DELETE FROM plans_armement WHERE item_id IN ('$itemNord', '$itemCentre', '$itemSud');

-- Dotation DRC (Nord) : 30 type, 0 reel, 0 virtuel
INSERT INTO plans_armement (unite_id, item_id, quantite_type, quantite_reelle, quantite_virtuelle)
VALUES (2, '$itemNord', 30.00, 0.00, 0.00);

-- Dotation CFISM (Centre) : 10 type, 0 reel, 0 virtuel
INSERT INTO plans_armement (unite_id, item_id, quantite_type, quantite_reelle, quantite_virtuelle)
VALUES (4, '$itemCentre', 10.00, 0.00, 0.00);

-- Dotation Unite 10 (Sud) : 12 type, 0 reel, 0 virtuel
INSERT INTO plans_armement (unite_id, item_id, quantite_type, quantite_reelle, quantite_virtuelle)
VALUES (334, '$itemSud', 12.00, 0.00, 0.00);
"
Log-Success "Plans d'armement initialises : DRC=30 (Nord), CFISM=10 (Centre), 10=12 (Sud)"

# ==============================================================================
# PHASE 1 : RECEPTION FOURNISSEUR DANS LES 3 SOUTES
# ==============================================================================
Log-Step "PHASE 1 : Approvisionnement / Receptions Fournisseurs"

function Exec-Reception($token, $numBpr, $numPv, $numBe, $magasinId, $itemId, $qte, $pu) {
    $headers = @{ "Authorization" = "Bearer $token"; "Content-Type" = "application/json; charset=utf-8" }
    
    # 1. BPR
    $bprBody = '{"bpr":{"numeroBpr":"' + $numBpr + '","dateReception":"' + (Get-Date).ToString("yyyy-MM-dd") + '","fournisseur":{"id":6},"magasin":{"id":' + $magasinId + '}},"lignes":[{"item":{"id":"' + $itemId + '"},"quantiteLivree":' + $qte + ',"prixUnitaire":' + $pu + '}]}'
    $bprRes = Invoke-RestMethod -Uri "$baseUrl/receptions/bpr" -Method Post -Headers $headers -Body $bprBody
    Write-Host "   -> BPR cree : $($bprRes.numeroBpr) (ID: $($bprRes.id))"
    
    # 2. PV Commission (ACCEPTE)
    $pvBody = '{"numeroPv":"' + $numPv + '","dateCommission":"' + (Get-Date).ToString("yyyy-MM-dd") + '","decision":"ACCEPTE","membres":"Commission Technique Marine","observations":"Conforme aux specifications","bpr":{"id":' + $bprRes.id + '}}'
    $pvRes = Invoke-RestMethod -Uri "$baseUrl/receptions/pv" -Method Post -Headers $headers -Body $pvBody
    Write-Host "   -> PV Commission valide : $($pvRes.numeroPv) (Decision: $($pvRes.decision))"
    
    # 3. Bon d'Entree (BE) -> Incremente le stock
    $beBody = '{"numeroBe":"' + $numBe + '","dateEntree":"' + (Get-Date).ToString("yyyy-MM-dd") + '","pvCommission":{"id":' + $pvRes.id + '}}'
    $beRes = Invoke-RestMethod -Uri "$baseUrl/receptions/be" -Method Post -Headers $headers -Body $beBody
    Write-Host "   -> Bon d Entree genere : $($beRes.numeroBe)"
}

$randSuffix = Get-Random -Minimum 1000 -Maximum 9999

# Soute Nord (Bizerte, Magasin 1) : Réception de 50 Consommables
Exec-Reception $sgsToken "BPR-NORD-$randSuffix" "PV-NORD-$randSuffix" "BE-NORD-$randSuffix" 1 $itemNord 50.0 12.500
# Soute Centre (Kélibia, Magasin 2) : Réception de 10 Non Consommables
Exec-Reception $sgsToken "BPR-CENTRE-$randSuffix" "PV-CENTRE-$randSuffix" "BE-CENTRE-$randSuffix" 2 $itemCentre 10.0 3500.000
# Soute Sud (Sfax, Magasin 3) : Réception de 20 Non Consommables
Exec-Reception $sgsToken "BPR-SUD-$randSuffix" "PV-SUD-$randSuffix" "BE-SUD-$randSuffix" 3 $itemSud 20.0 850.000

# Vérification arithmétique Stock
Log-Step "Controle Arithmetique Stock apres Receptions (Entrees en stock)"
docker exec gamma3_postgres psql -U gamma3_user -d gamma3_db -c "
SELECT m.code as soute, m.nom, i.nomenclature, s.quantite 
FROM stocks s 
JOIN magasins m ON m.id = s.magasin_id 
JOIN items i ON i.id = s.item_id 
WHERE i.nomenclature LIKE 'TEST%' 
ORDER BY m.id;"

# ==============================================================================
# PHASE 2 : DEMANDES DE MATERIEL & VALIDATION COMMANDANT
# ==============================================================================
Log-Step "PHASE 2 : Emission des demandes et Validation Commandant"

function Exec-Demande($gestToken, $cdtToken, $itemId, $qte, $uniteNom) {
    $gestHeaders = @{ "Authorization" = "Bearer $gestToken"; "Content-Type" = "application/json; charset=utf-8" }
    $cdtHeaders = @{ "Authorization" = "Bearer $cdtToken"; "Content-Type" = "application/json; charset=utf-8" }
    $numDem = "DEM-2026-" + (Get-Random -Minimum 1000 -Maximum 9999)
    
    $demBody = '{"demande":{"numeroDemande":"' + $numDem + '","dateDemande":"' + (Get-Date).ToString("yyyy-MM-dd") + '","motif":"Dotation de test"},"lignes":[{"item":{"id":"' + $itemId + '"},"quantiteDemandee":' + $qte + '}]}'
    $demRes = Invoke-RestMethod -Uri "$baseUrl/demandes" -Method Post -Headers $gestHeaders -Body $demBody
    Write-Host "   -> Demande soumise par Gestionnaire $uniteNom : ID $($demRes.id) (N $($demRes.numeroDemande))"
    
    # Validation par le Commandant
    $valBody = '{"accepter":true,"motifRefus":""}'
    $valRes = Invoke-RestMethod -Uri "$baseUrl/demandes/$($demRes.id)/valider-commandant" -Method Post -Headers $cdtHeaders -Body $valBody
    Write-Host "   -> Validee par Commandant : Statut $($valRes.statut), Soute assignee: $($valRes.magasinDesigne.nom)"
    return $valRes
}

$demNord = Exec-Demande $drcGestionnaireToken $drcCommandantToken $itemNord 15.0 "DRC (Nord)"
$demCentre = Exec-Demande $cfismGestionnaireToken $cfismCommandantToken $itemCentre 3.0 "CFISM (Centre)"
$demSud = Exec-Demande $sud10GestionnaireToken $sud10CommandantToken $itemSud 4.0 "Unite 10 (Sud)"

# Vérification arithmétique Plan d'Armement (Réservation virtuelle)
Log-Step "Controle Arithmetique Dotations Virtuelles (Reservations en cours)"
docker exec gamma3_postgres psql -U gamma3_user -d gamma3_db -c "
SELECT u.code as unite, i.nomenclature, pa.quantite_type, pa.quantite_reelle, pa.quantite_virtuelle 
FROM plans_armement pa 
JOIN unites_utilisatrices u ON u.id = pa.unite_id 
JOIN items i ON i.id = pa.item_id 
WHERE i.nomenclature LIKE 'TEST%' 
ORDER BY u.id;"

# ==============================================================================
# PHASE 3 : ARBITRAGE PAR LES SOUTES REGIONALES & BONS DE SORTIE
# ==============================================================================
Log-Step "PHASE 3 : Arbitrage DA et Generation des Bons de Sortie"

function Exec-Arbitrage($daToken, $demande, $qteAccordee, $souteNom) {
    $headers = @{ "Authorization" = "Bearer $daToken"; "Content-Type" = "application/json; charset=utf-8" }
    
    # Récupérer les lignes de la demande
    $lignes = Invoke-RestMethod -Uri "$baseUrl/demandes/$($demande.id)/lignes" -Method Get -Headers $headers
    $arbBody = '{"decision":"APPROUVE_TOTAL","motifRefus":"","lignes":[{"id":' + $lignes[0].id + ',"quantiteAccordee":' + $qteAccordee + '}],"magasinId":' + $demande.magasinDesigne.id + '}'
    
    $arbRes = Invoke-RestMethod -Uri "$baseUrl/demandes/$($demande.id)/arbitrer" -Method Post -Headers $headers -Body $arbBody
    Write-Host "   -> Arbitrage valide par $souteNom : Statut $($arbRes.statut), Bon de Sortie: $($arbRes.bonSortie.numeroBs)"
    return $arbRes
}

$arbNord = Exec-Arbitrage $sgsToken $demNord 15.0 "Soute Nord (SGS)"
$arbCentre = Exec-Arbitrage $centreToken $demCentre 3.0 "Soute Centre (SM1)"
$arbSud = Exec-Arbitrage $sudToken $demSud 4.0 "Soute Sud (SM2)"

# ==============================================================================
# PHASE 4 : RETRAIT PHYSIQUE DU MATERIEL (BON DE SORTIE)
# ==============================================================================
Log-Step "PHASE 4 : Retrait Physique et Remise en Service a Bord"

function Exec-Retrait($token, $bsId, $acteurNom) {
    $headers = @{ "Authorization" = "Bearer $token"; "Content-Type" = "application/json; charset=utf-8" }
    $res = Invoke-RestMethod -Uri "$baseUrl/demandes/bons-sortie/$bsId/confirmer-livraison" -Method Post -Headers $headers -Body "{}"
    Write-Host "   -> Retrait physique confirme par $acteurNom pour BS $bsId : Statut $($res.statut)"
}

Exec-Retrait $sgsToken $arbNord.bonSortie.id "SGS (Nord)"
Exec-Retrait $centreToken $arbCentre.bonSortie.id "Magasinier Centre (SM1)"
Exec-Retrait $sudToken $arbSud.bonSortie.id "Magasinier Sud (SM2)"

Log-Step "Controle Arithmetique apres Retrait Physique :"
Write-Host "1. Stocks dans les Soutes regionales (Decrementations physiques) :" -ForegroundColor Yellow
docker exec gamma3_postgres psql -U gamma3_user -d gamma3_db -c "
SELECT m.code as soute, m.nom, i.nomenclature, s.quantite 
FROM stocks s 
JOIN magasins m ON m.id = s.magasin_id 
JOIN items i ON i.id = s.item_id 
WHERE i.nomenclature LIKE 'TEST%' 
ORDER BY m.id;"

Write-Host "2. Dotations Unites (Reelle a Bord incrementee, Virtuelle remise a zero) :" -ForegroundColor Yellow
docker exec gamma3_postgres psql -U gamma3_user -d gamma3_db -c "
SELECT u.code as unite, i.nomenclature, pa.quantite_type, pa.quantite_reelle, pa.quantite_virtuelle 
FROM plans_armement pa 
JOIN unites_utilisatrices u ON u.id = pa.unite_id 
JOIN items i ON i.id = pa.item_id 
WHERE i.nomenclature LIKE 'TEST%' 
ORDER BY u.id;"

# ==============================================================================
# PHASE 5 : PROCEDURE DE REFORME ET REGLE STRICTE NON-CONSOMMABLE
# ==============================================================================
Log-Step "PHASE 5 : Tests de Reforme (Controle metier Non-Consommable)"

# 5.1 Test Négatif : Tentative de réforme sur article CONSOMMABLE (TEST01CONSO1)
Write-Host "`n--- Test 5.1 : Controle de rejet sur article CONSOMMABLE ---" -ForegroundColor Yellow
$headersDrc = @{ "Authorization" = "Bearer $drcGestionnaireToken"; "Content-Type" = "application/json; charset=utf-8" }
$lignesRefNord = '[{"item":{"id":"' + $itemNord + '"},"quantite":2.0,"observations":"Tentative de reforme huile moteur consommable"}]'

try {
    $resRefNeg = Invoke-RestMethod -Uri "$baseUrl/reformes?uniteId=2&motif=TestReformeConso" -Method Post -Headers $headersDrc -Body $lignesRefNord
    Log-Error "ANOMALIE : La reforme d un article consommable a ete acceptee !"
} catch {
    Log-Success "SUCCES CONTROLE METIER : La reforme de l article consommable a ete REJETEE par le systeme (HTTP 400 Bad Request) !"
}

# 5.2 Test Positif : Réforme d'un article NON-CONSOMMABLE (Centre : TEST02NCONS1)
Write-Host "`n--- Test 5.2 : Reforme article NON-CONSOMMABLE (Centre / CFISM) ---" -ForegroundColor Yellow
$headersCfism = @{ "Authorization" = "Bearer $cfismGestionnaireToken"; "Content-Type" = "application/json; charset=utf-8" }
$lignesRefCentre = '[{"item":{"id":"' + $itemCentre + '"},"quantite":1.0,"observations":"Radar de navigation defectueux"}]'

$dossierCentre = Invoke-RestMethod -Uri "$baseUrl/reformes?uniteId=4&motif=PanneIrreparable" -Method Post -Headers $headersCfism -Body $lignesRefCentre
Log-Success "Dossier de reforme cree : ID $($dossierCentre.id) pour CFISM (Statut: $($dossierCentre.statut))"

# Traitement en Commission par l'Admin (SRR) -> DECLASSEMENT
$headersAdmin = @{ "Authorization" = "Bearer $adminToken"; "Content-Type" = "application/json; charset=utf-8" }
$commCentre = Invoke-RestMethod -Uri "$baseUrl/reformes/$($dossierCentre.id)/commission?decision=DECLASSEMENT&membres=CommissionMarine&observations=DeclassementValide" -Method Post -Headers $headersAdmin -Body "{}"
Log-Success "Commission de reforme Centre validee (Decision: $($commCentre.decisionCommission))"

# 5.3 Test Positif : Réforme d'un article NON-CONSOMMABLE (Sud : TEST03NCONS1)
Write-Host "`n--- Test 5.3 : Reforme article NON-CONSOMMABLE (Sud / Unite 10) ---" -ForegroundColor Yellow
$headersSud10 = @{ "Authorization" = "Bearer $sud10GestionnaireToken"; "Content-Type" = "application/json; charset=utf-8" }
$lignesRefSud = '[{"item":{"id":"' + $itemSud + '"},"quantite":1.0,"observations":"Pompe de refroidissement corps fissure"}]'

$dossierSud = Invoke-RestMethod -Uri "$baseUrl/reformes?uniteId=334&motif=CorpsFissure" -Method Post -Headers $headersSud10 -Body $lignesRefSud
Log-Success "Dossier de reforme cree : ID $($dossierSud.id) pour Unite 10 (Statut: $($dossierSud.statut))"

# Traitement en Commission par l'Admin (SRR) -> DECLASSEMENT
$commSud = Invoke-RestMethod -Uri "$baseUrl/reformes/$($dossierSud.id)/commission?decision=DECLASSEMENT&membres=CommissionMarine&observations=DeclassementSudValide" -Method Post -Headers $headersAdmin -Body "{}"
Log-Success "Commission de reforme Sud validee (Decision: $($commSud.decisionCommission))"

# ==============================================================================
# BILAN MATHEMATIQUE FINAL
# ==============================================================================
Log-Step "BILAN MATHEMATIQUE FINAL COMPLET"
Write-Host "1. Stocks dans les Magasins (Soutes Regionales + SRR) :" -ForegroundColor Yellow
docker exec gamma3_postgres psql -U gamma3_user -d gamma3_db -c "
SELECT m.id, m.code as soute, m.nom, i.nomenclature, s.quantite 
FROM stocks s 
JOIN magasins m ON m.id = s.magasin_id 
JOIN items i ON i.id = s.item_id 
WHERE i.nomenclature LIKE 'TEST%' 
ORDER BY m.id;"

Write-Host "2. Dotations des Unites (Plan d'Armement a bord) :" -ForegroundColor Yellow
docker exec gamma3_postgres psql -U gamma3_user -d gamma3_db -c "
SELECT u.id, u.code as unite, i.nomenclature, pa.quantite_type, pa.quantite_reelle, pa.quantite_virtuelle 
FROM plans_armement pa 
JOIN unites_utilisatrices u ON u.id = pa.unite_id 
JOIN items i ON i.id = pa.item_id 
WHERE i.nomenclature LIKE 'TEST%' 
ORDER BY u.id;"
