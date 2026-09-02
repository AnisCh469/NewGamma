package tn.defense.gamma3.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tn.defense.gamma3.auth.api.dto.AuthDto;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

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
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getMatricule(),
                        request.getPassword()
                )
        );
        var user = repository.findByMatricule(request.getMatricule())
                .orElseThrow();

        if (user.isTwoFactorEnabled()) {
            return AuthDto.AuthResponse.builder()
                    .requires2fa(true)
                    .matricule(user.getMatricule())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .build();
        }

        var jwtToken = jwtService.generateToken(user);
        return AuthDto.AuthResponse.builder()
                .token(jwtToken)
                .matricule(user.getMatricule())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
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
        return AuthDto.AuthResponse.builder()
                .token(jwtToken)
                .matricule(user.getMatricule())
                .fullName(user.getFullName())
                .role(user.getRole())
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
