package tn.defense.gamma3.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * RÔLE SÉCURITAIRE (Hardening & Homologation Militaire - OWASP A05 & CWE-209) :
 * Intercepteur centralisé des exceptions applicatives et d'infrastructure.
 *
 * Élimine toute fuite d'informations sensibles (noms de tables SQL, stack traces,
 * structure interne des classes Java) dans les réponses HTTP transmises au client.
 * Enregistre les erreurs complètes dans les journaux serveurs avec SLF4J, mais
 * retourne un format d'erreur JSON assaini et standardisé.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, String> details
    ) {}

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Échec d'authentification sur l'URI {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse res = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Authentification échouée",
                "Matricule ou mot de passe incorrect.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Tentative d'accès non autorisée sur l'URI {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse res = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Accès refusé",
                "Vous n'avez pas les habilitations requises pour cette opération.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(res);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        ErrorResponse res = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Données invalides",
                "La requête contient des paramètres invalides ou manquants.",
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.badRequest().body(res);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Violation d'intégrité référentielle sur {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse res = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflit d'intégrité",
                "L'opération viole une contrainte d'intégrité ou d'unicité métier.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(res);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleIllegalArguments(RuntimeException ex, HttpServletRequest request) {
        log.warn("Requête métier invalide sur {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse res = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Paramètres invalides",
                ex.getMessage() != null ? ex.getMessage() : "Requête incompatible avec l'état métier actuel.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.badRequest().body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Erreur non interceptée sur l'URI {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse res = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erreur système interne",
                "Une erreur inattendue est survenue. L'incident a été consigné pour analyse technique.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
    }
}
