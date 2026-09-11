-- =====================================================================================
-- V3 — Ajoute SAUVEGARDE_DECLENCHEE / SAUVEGARDE_ECHEC à la contrainte CHECK de
-- journal_audit.action.
-- =====================================================================================
--
-- CONTEXTE (2026-09-11, Phase 2 de docs/PROPOSITION_GOUVERNANCE_ROLES.md) : nouveau module
-- de sauvegarde automatisée (pg_dump), dont chaque déclenchement (succès ou échec) est
-- journalisé. Même piège que V1/V2 : `ddl-auto: update` ne met JAMAIS à jour une contrainte
-- CHECK existante quand un enum grandit — sans ce script, toute tentative de journaliser un
-- de ces deux nouveaux types d'action violerait la contrainte figée.
--
-- Sans effet sur une base neuve (la table n'existe pas encore quand Flyway passe, avant
-- Hibernate) ; répare une base existante.
-- =====================================================================================

DO $$
BEGIN
    IF to_regclass('public.journal_audit') IS NOT NULL THEN
        ALTER TABLE journal_audit DROP CONSTRAINT IF EXISTS journal_audit_action_check;
        ALTER TABLE journal_audit ADD CONSTRAINT journal_audit_action_check
            CHECK (action::text = ANY (ARRAY[
                'CONNEXION',
                'CONNEXION_ECHEC',
                'DECONNEXION_MANUELLE',
                'DECONNEXION_INACTIVITE',
                'DECONNEXION_FERMETURE',
                'PARAMETRE_MODIFIE',
                'COMPTE_CREE',
                'COMPTE_MODIFIE',
                'COMPTE_BLOQUE',
                'COMPTE_DEBLOQUE',
                'MOT_DE_PASSE_REINITIALISE',
                'SAUVEGARDE_DECLENCHEE',
                'SAUVEGARDE_ECHEC'
            ]::text[]));
    END IF;
END $$;
