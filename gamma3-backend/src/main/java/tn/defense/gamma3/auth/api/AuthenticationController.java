package tn.defense.gamma3.auth.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.api.dto.AuthDto;
import tn.defense.gamma3.auth.service.AuthenticationService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(
            @RequestBody AuthDto.RegisterRequest request
    ) {
        return ResponseEntity.ok(service.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> authenticate(
            @RequestBody AuthDto.LoginRequest request
    ) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthDto.AuthResponse> verify2Fa(
            @RequestBody AuthDto.Verify2FaRequest request
    ) {
        try {
            return ResponseEntity.ok(service.verify2Fa(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<java.util.Map<String, String>> setup2Fa(@RequestParam String matricule) {
        String secret = service.setup2Fa(matricule);
        return ResponseEntity.ok(java.util.Map.of("secret", secret));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<Void> enable2Fa(@RequestParam String matricule, @RequestParam String code) {
        boolean enabled = service.enable2Fa(matricule, code);
        return enabled ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> disable2Fa(@RequestParam String matricule) {
        service.disable2Fa(matricule);
        return ResponseEntity.ok().build();
    }
}
