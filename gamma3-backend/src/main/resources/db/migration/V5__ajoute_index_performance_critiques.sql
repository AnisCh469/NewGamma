-- =====================================================================================
-- V5 — Ajout des index de performance critiques (système militaire à fort volume).
-- =====================================================================================
--
-- CONTEXTE (2026-11-17, Étape 3 de la feuille de route / docs/DATABASE_ARCHITECTURE.md §9.4 & §10.2) :
-- Le catalogue compte plus de 63 000 articles et la table `mouvements_stock` dépasse 163 000 lignes.
-- Aucun index B-Tree explicite n'était configuré en dehors des contraintes d'unicité.
--
-- Ces index composites accélèrent de manière drastique les opérations courantes :
-- 1. `mouvements_stock` : calcul des prévisions annuelles P01 (lissage 3 ans) et historique article.
-- 2. `demandes_materiel` : tableaux de bord unités, validation Commandant, arbitrage SGS.
-- 3. `lignes_demande` : chargement relationnel des lignes et calcul du stock réservé.
-- 4. `plans_armement` : agrégat de la dotation globale par article (P01).
-- 5. `stocks` : consultation et inventaire par magasin physique (SM1/SM2).
-- 6. `users` : vérification d'identité à chaque requête du flux de double validation (unite_id, fonction_unite).
-- 7. `journal_audit` : tri chronologique et recherche par opérateur.
--
-- Sans effet sur une base neuve (tables non encore créées) ; accélère immédiatement une base existante.
-- =====================================================================================

DO $$
BEGIN
    -- 1. Mouvements de stock (163 000+ lignes)
    IF to_regclass('public.mouvements_stock') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_mouvements_stock_item_date_type 
            ON mouvements_stock (item_id, date_mouvement, type_mouvement);
        CREATE INDEX IF NOT EXISTS idx_mouvements_stock_magasin_date 
            ON mouvements_stock (magasin_id, date_mouvement);
    END IF;

    -- 2. Demandes de matériel
    IF to_regclass('public.demandes_materiel') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_demandes_materiel_unite_statut 
            ON demandes_materiel (unite_id, statut);
        CREATE INDEX IF NOT EXISTS idx_demandes_materiel_statut_date 
            ON demandes_materiel (statut, date_demande);
        CREATE INDEX IF NOT EXISTS idx_demandes_materiel_magasin 
            ON demandes_materiel (magasin_designe_id);
    END IF;

    -- 3. Lignes de demande
    IF to_regclass('public.lignes_demande') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_lignes_demande_demande_id 
            ON lignes_demande (demande_id);
        CREATE INDEX IF NOT EXISTS idx_lignes_demande_item_id 
            ON lignes_demande (item_id);
    END IF;

    -- 4. Plans d'armement (dotations)
    IF to_regclass('public.plans_armement') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_plans_armement_item_id 
            ON plans_armement (item_id);
    END IF;

    -- 5. Stocks par magasin
    IF to_regclass('public.stocks') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_stocks_magasin_id 
            ON stocks (magasin_id);
    END IF;

    -- 6. Utilisateurs (double validation Gestionnaire / Commandant)
    IF to_regclass('public.users') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_users_unite_fonction 
            ON users (unite_id, fonction_unite);
    END IF;

    -- 7. Journal d'audit (traçabilité militaire)
    IF to_regclass('public.journal_audit') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_journal_audit_date 
            ON journal_audit (date_evenement DESC);
        CREATE INDEX IF NOT EXISTS idx_journal_audit_acteur 
            ON journal_audit (acteur_matricule);
    END IF;
END $$;
