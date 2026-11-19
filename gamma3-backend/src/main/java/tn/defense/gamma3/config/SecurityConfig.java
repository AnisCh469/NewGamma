package tn.defense.gamma3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.AuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

/**
 * CORRECTIF SÉCURITÉ CRITIQUE (2026-09-11, découvert lors de la vérification de la
 * refonte des rôles SUPER_ADMIN/ADMIN) : {@code @EnableMethodSecurity} était absent de
 * toute la configuration Spring. Conséquence : TOUTES les annotations
 * {@code @PreAuthorize(...)} du projet (MigrationController, LegacyAuditController)
 * étaient silencieusement ignorées par Spring Security — n'importe quel compte
 * authentifié, quel que soit son rôle, pouvait donc appeler ces endpoints
 * "réservés" à un rôle précis. Ce n'est PAS un effet de bord de la refonte des rôles :
 * c'était déjà le cas avant (avec {@code hasRole('ADMIN')}), simplement jamais détecté
 * faute d'un test qui vérifie explicitement un refus (403) sur ces endpoints précis.
 * Ajouter cette annotation active enfin l'évaluation de {@code @PreAuthorize} sur
 * toutes les méthodes de tous les beans Spring (contrôleurs y compris).
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    /**
     * Origines autorisées à appeler l'API en CORS, séparées par des virgules.
     * Pourquoi externalisée : cette valeur était auparavant dupliquée en dur
     * ("http://localhost:4200") dans SecurityConfig ET dans 13 contrôleurs
     * via @CrossOrigin — deux mécanismes concurrents pour la même règle.
     * Seule cette configuration globale fait désormais foi ; les annotations
     * @CrossOrigin redondantes ont été retirées des contrôleurs. En
     * production, le frontend est servi par Nginx en reverse-proxy sur la
     * même origine que l'API (voir gamma3-frontend/nginx.conf), donc CORS
     * n'intervient qu'en développement local ou pour un futur client tiers
     * (ex: application mobile) — surchargeable via CORS_ALLOWED_ORIGINS.
     */
    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:4200}")
    private String allowedOrigins;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .authorizeHttpRequests(auth -> auth
                // SÉCURITÉ (correctif 2026-09-04) : seules "/login" et "/verify-2fa" sont
                // accessibles sans jeton JWT — ce sont les deux seules étapes du protocole
                // où l'appelant n'en possède pas encore un. "/register" (création de compte,
                // réservée aux ADMIN) et "/2fa/setup|enable|disable" (2FA du compte CONNECTÉ)
                // exigeaient auparavant AUCUNE authentification, une faille critique
                // permettant à quiconque de créer un compte ADMIN ou de désactiver la 2FA
                // d'un tiers (voir AuthenticationController) — ils tombent désormais sous la
                // règle .anyRequest().authenticated() ci-dessous.
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/verify-2fa").permitAll()
                .requestMatchers("/api/v1/items/photos/**").permitAll()
                .requestMatchers("/api/v1/items/documents/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers("/api/v1/error/**", "/error").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll() // Si on a Swagger
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
