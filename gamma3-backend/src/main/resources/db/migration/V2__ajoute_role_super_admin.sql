-- =====================================================================================
-- V2 — Ajoute SUPER_ADMIN à la contrainte CHECK de users.role.
-- =====================================================================================
--
-- CONTEXTE (2026-09-11, docs/PROPOSITION_GOUVERNANCE_ROLES.md) : introduction d'un rôle
-- Administrateur Système (SUPER_ADMIN), distinct de ADMIN (administrateur logistique/SGS).
-- Même piège que V1 (voir ce script) : `ddl-auto: update` ne met JAMAIS à jour une
-- contrainte CHECK existante quand un enum grandit — sans ce script, toute tentative
-- d'attribuer le rôle SUPER_ADMIN à un compte violerait la contrainte figée.
--
-- Sans effet sur une base neuve (la table n'existe pas encore quand Flyway passe, avant
-- Hibernate) ; répare une base existante.
-- =====================================================================================

DO $$
BEGIN
    IF to_regclass('public.users') IS NOT NULL THEN
        ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
        ALTER TABLE users ADD CONSTRAINT users_role_check
            CHECK (role::text = ANY (ARRAY[
                'SUPER_ADMIN',
                'ADMIN',
                'DA_MANAGER',
                'UNIT_USER'
            ]::text[]));
    END IF;
END $$;
