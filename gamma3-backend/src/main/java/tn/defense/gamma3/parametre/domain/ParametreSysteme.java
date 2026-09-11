package tn.defense.gamma3.parametre.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.auth.domain.User;

import java.time.LocalDateTime;

/**
 * Paramètre système modifiable par l'ADMIN (clé/valeur générique, plutôt qu'une colonne par
 * réglage — le nombre de réglages système est amené à grandir). Premier usage : le délai
 * d'inactivité avant déconnexion automatique (voir {@code ParametreSystemeService}).
 */
@Entity
@Table(name = "parametres_systeme")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametreSysteme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String cle;

    @Column(nullable = false, length = 255)
    private String valeur;

    @Column(length = 255)
    private String description;

    /** Null tant que la valeur par défaut n'a jamais été modifiée par un administrateur. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modifie_par")
    private User modifiePar;

    @Column(name = "modifie_le")
    private LocalDateTime modifieLe;
}
