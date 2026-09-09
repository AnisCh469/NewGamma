-- =====================================================================================
-- V1 — Réaligne la contrainte CHECK du statut des demandes de matériel sur l'enum Java.
-- =====================================================================================
--
-- CONTEXTE (audit 2026-09-09) :
--   La contrainte `demandes_materiel_statut_check` a été générée par Hibernate à la
--   création initiale de la table et n'énumérait que les 7 valeurs de `StatutDemande`
--   de l'époque. La session du 2026-09-04 a ajouté deux valeurs à l'enum
--   (EN_ATTENTE_VALIDATION_COMMANDANT, REFUSE_COMMANDANT) pour le workflow de double
--   validation Gestionnaire/Commandant — mais `ddl-auto: update` ne met JAMAIS à jour
--   une contrainte CHECK existante. Toute demande créée avec le nouveau statut initial
--   violait donc la contrainte figée → HTTP 500, workflow entièrement bloqué sur toute
--   base pré-existante (une base neuve n'a pas le problème, Hibernate la recréant
--   complète à partir de zéro).
--
-- Ce script est le premier d'une série de migrations versionnées (introduction de Flyway).
-- Il est ÉCRIT POUR ÊTRE SANS EFFET sur une base neuve (la table n'existe pas encore quand
-- Flyway passe, avant Hibernate) et RÉPARATEUR sur une base existante.
-- =====================================================================================

DO $$
BEGIN
    IF to_regclass('public.demandes_materiel') IS NOT NULL THEN
        ALTER TABLE demandes_materiel DROP CONSTRAINT IF EXISTS demandes_materiel_statut_check;
        ALTER TABLE demandes_materiel ADD CONSTRAINT demandes_materiel_statut_check
            CHECK (statut::text = ANY (ARRAY[
                'EN_ATTENTE_VALIDATION_COMMANDANT',
                'REFUSE_COMMANDANT',
                'SOUMIS',
                'EN_COURS_ARBITRAGE',
                'ESCALADE_SGS',
                'APPROUVE_PARTIEL',
                'APPROUVE_TOTAL',
                'REFUSE',
                'LIVRE'
            ]::text[]));
    END IF;
END $$;
