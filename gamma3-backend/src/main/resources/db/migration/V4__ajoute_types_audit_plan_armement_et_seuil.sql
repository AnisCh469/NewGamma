-- =====================================================================================
-- V4 — Ajoute PLAN_ARMEMENT_CREE / _MODIFIE / _SUPPRIME et SEUIL_SECURITE_DEROGATION à la
-- contrainte CHECK de journal_audit.action.
-- =====================================================================================
--
-- CONTEXTE (2026-09-11, audit de conformité CCF — docs/AUDIT_CONFORMITE_CCF_2026-09-11.md
-- §2.1 et §2.2) : PlanArmementController n'avait AUCUN contrôle de rôle sur POST/PUT/DELETE
-- (corrigé au même moment que l'ajout de cette traçabilité) ; le seuil de sécurité d'un
-- article (RG16/17/68/102) n'était jamais appliqué pour bloquer une distribution — corrigé
-- dans DistributionService.confirmerSortiePhysique avec un mécanisme de dérogation réservé
-- ADMIN/SUPER_ADMIN, désormais tracé. Même piège que V1/V2/V3 : `ddl-auto: update` ne met
-- jamais à jour une contrainte CHECK existante quand un enum grandit.
--
-- Sans effet sur une base neuve ; répare une base existante.
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
                'SAUVEGARDE_ECHEC',
                'PLAN_ARMEMENT_CREE',
                'PLAN_ARMEMENT_MODIFIE',
                'PLAN_ARMEMENT_SUPPRIME',
                'SEUIL_SECURITE_DEROGATION'
            ]::text[]));
    END IF;
END $$;
