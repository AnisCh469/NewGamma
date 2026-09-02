package tn.defense.gamma3.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import tn.defense.gamma3.stock.domain.Magasin;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String matricule; // Identifiant unique (ex: matricule militaire)

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // ─── Liaison Magasin ──────────────────────────────────────────────────────
    // RÔLE : Lie chaque DA_MANAGER à SON magasin physique (SM1 ou SM2).
    //        Pour l'ADMIN (SGS), ce champ est NULL : il supervise tous les magasins.
    // POURQUOI : Permet de filtrer les demandes selon la soute du gestionnaire connecté,
    //            et d'éviter qu'un DA_MANAGER intère dans un magasin qui ne lui appartient pas.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "magasin_id", nullable = true)
    private Magasin magasin;

    @Builder.Default
    @Column(name = "two_factor_enabled", nullable = false, columnDefinition = "boolean default false")
    private boolean twoFactorEnabled = false;

    @Column(name = "secret_2fa", length = 32)
    private String secret2fa;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return matricule;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
