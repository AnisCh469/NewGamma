package tn.defense.gamma3.auth.util;

import tn.defense.gamma3.auth.domain.FonctionDA;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;

/**
 * RÔLE : centralise la logique d'autorisation "par section DA" — Phase B de
 * docs/PROPOSITION_ROLES_SECTIONS_DA.md (2026-09-14), premier cloisonnement réel après les
 * fondations de la Phase A (`User.fonctionDA`).
 *
 * PRINCIPE (opt-in, cohérent avec la Phase A) : un compte ADMIN qui n'a jamais été rattaché à
 * une section DA (`fonctionDA == null`), ou explicitement tagué `GEST` (généraliste), garde
 * TOUS ses pouvoirs — comportement historique inchangé, zéro régression sur les comptes déjà
 * créés. Seul un compte ADMIN dont la `fonctionDA` est renseignée ET différente de la (ou des)
 * section(s) autorisée(s) pour cet écran se voit refuser l'accès — c'est un cloisonnement que
 * l'administrateur système choisit d'activer AU CAS PAR CAS, en taguant un compte, jamais un
 * changement de comportement par défaut.
 *
 * `SUPER_ADMIN` reste toujours autorisé (superset système, inchangé depuis la Phase 1 de
 * gouvernance des rôles). `DA_MANAGER` suit le paramètre `daManagerAutorise` propre à chaque
 * appel, pour ne pas présumer qu'un magasinier a accès à un écran qui ne le concerne pas.
 */
public final class AccesDAUtil {

    private AccesDAUtil() {
    }

    /**
     * @param u                  l'utilisateur authentifié (peut être {@code null})
     * @param daManagerAutorise  si {@code true}, un DA_MANAGER est autorisé (inchangé par
     *                           rapport à avant l'introduction de FonctionDA)
     * @param sectionsAutorisees les sections DA dont un compte ADMIN restreint doit faire
     *                           partie pour être autorisé (ignoré si le compte n'est pas
     *                           restreint, voir le principe ci-dessus)
     * @return {@code true} si l'utilisateur peut accéder à l'écran/action concerné(e)
     */
    public static boolean estAutorise(User u, boolean daManagerAutorise, FonctionDA... sectionsAutorisees) {
        if (u == null) return false;
        if (u.getRole() == Role.SUPER_ADMIN) return true;
        if (u.getRole() == Role.DA_MANAGER) return daManagerAutorise;
        if (u.getRole() == Role.ADMIN) {
            FonctionDA f = u.getFonctionDA();
            if (f == null || f == FonctionDA.GEST) return true; // généraliste, comportement historique
            for (FonctionDA section : sectionsAutorisees) {
                if (f == section) return true;
            }
            return false;
        }
        return false;
    }
}
