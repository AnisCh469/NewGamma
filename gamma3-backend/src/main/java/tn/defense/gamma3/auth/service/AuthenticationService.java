package tn.defense.gamma3.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.service.JournalAuditService;
import tn.defense.gamma3.auth.api.dto.AuthDto;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.repository.UserRepository;

/**
 * NOTE TRAÇABILITÉ (2026-09-11) : chaque tentative de connexion (réussie ou non) et chaque
 * connexion finalisée après 2FA sont consignées dans le journal d'audit ({@link JournalAuditService}).
 * {@code httpServletRequest} est injecté directement dans ce service (singleton) : Spring
 * fournit automatiquement un proxy à portée requête qui délègue, à chaque appel, à la requête
 * HTTP réellement en cours — c'est le mécanisme standard pour accéder à la requête depuis un
 * bean qui n'est pas un contrôleur, ici uniquement pour en extraire l'adresse IP de l'appelant.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final JournalAuditService journalAuditService;
    private final jakarta.servlet.http.HttpServletRequest httpServletRequest;

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        var user = User.builder()
                .matricule(request.getMatricule())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();
        repository.save(user);
        var jwtToken = jwtService.generateToken(user);
        return AuthDto.AuthResponse.builder()
                .token(jwtToken)
                .matricule(user.getMatricule())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    public AuthDto.AuthResponse authenticate(AuthDto.LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getMatricule(),
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            journalAuditService.enregistrer(request.getMatricule(), null, TypeActionAudit.CONNEXION_ECHEC,
                    "Échec d'authentification (identifiants invalides ou compte bloqué).", ipAppelant());
            throw e;
        }
        var user = repository.findByMatricule(request.getMatricule())
                .orElseThrow();

        if (user.isTwoFactorEnabled()) {
            // Pas encore une connexion complète (2ème facteur en attente) : pas de CONNEXION
            // consignée ici, voir verify2Fa() qui finalise réellement la session.
            return AuthDto.AuthResponse.builder()
                    .requires2fa(true)
                    .matricule(user.getMatricule())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .uniteId(user.getUnite() != null ? user.getUnite().getId() : null)
                    .uniteCode(user.getUnite() != null ? user.getUnite().getCode() : null)
                    .fonctionUnite(user.getFonctionUnite())
                    .fonctionDA(user.getFonctionDA())
                    .build();
        }

        var jwtToken = jwtService.generateToken(user);
        journalAuditService.enregistrer(user.getMatricule(), user.getFullName(), TypeActionAudit.CONNEXION,
                "Connexion réussie.", ipAppelant());
        return AuthDto.AuthResponse.builder()
                .token(jwtToken)
                .matricule(user.getMatricule())
                .fullName(user.getFullName())
                .role(user.getRole())
                .uniteId(user.getUnite() != null ? user.getUnite().getId() : null)
                .uniteCode(user.getUnite() != null ? user.getUnite().getCode() : null)
                .fonctionUnite(user.getFonctionUnite())
                .fonctionDA(user.getFonctionDA())
                .build();
    }

    private String ipAppelant() {
        try {
            return httpServletRequest.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    public AuthDto.AuthResponse verify2Fa(AuthDto.Verify2FaRequest request) {
        var user = repository.findByMatricule(request.getMatricule()).orElseThrow();
        int codeInt;
        try {
            codeInt = Integer.parseInt(request.getCode());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Code invalide");
        }

        if (!TotpUtils.verifyCode(user.getSecret2fa(), codeInt)) {
            throw new IllegalArgumentException("Code de sécurité incorrect");
        }

        var jwtToken = jwtService.generateToken(user);
        journalAuditService.enregistrer(user.getMatricule(), user.getFullName(), TypeActionAudit.CONNEXION,
                "Connexion réussie (après validation du second facteur).", ipAppelant());
        return AuthDto.AuthResponse.builder()
                .token(jwtToken)
                .matricule(user.getMatricule())
                .fullName(user.getFullName())
                .role(user.getRole())
                .uniteId(user.getUnite() != null ? user.getUnite().getId() : null)
                .uniteCode(user.getUnite() != null ? user.getUnite().getCode() : null)
                .fonctionUnite(user.getFonctionUnite())
                .fonctionDA(user.getFonctionDA())
                .build();
    }

    public String setup2Fa(String matricule) {
        var user = repository.findByMatricule(matricule).orElseThrow();
        String secret = TotpUtils.generateSecretKey();
        user.setSecret2fa(secret);
        repository.save(user);
        return secret;
    }

    public boolean enable2Fa(String matricule, String code) {
        var user = repository.findByMatricule(matricule).orElseThrow();
        int codeInt;
        try {
            codeInt = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return false;
        }

        if (TotpUtils.verifyCode(user.getSecret2fa(), codeInt)) {
            user.setTwoFactorEnabled(true);
            repository.save(user);
            return true;
        }
        return false;
    }

    public void disable2Fa(String matricule) {
        var user = repository.findByMatricule(matricule).orElseThrow();
        user.setTwoFactorEnabled(false);
        user.setSecret2fa(null);
        repository.save(user);
    }
}
