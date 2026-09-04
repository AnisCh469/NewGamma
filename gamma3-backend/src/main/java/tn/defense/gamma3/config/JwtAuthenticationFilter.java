package tn.defense.gamma3.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.defense.gamma3.auth.service.JwtService;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        // SÉCURITÉ (correctif 2026-09-04) : seuls "/login" et "/verify-2fa" sont les
        // véritables étapes PRÉ-authentification du protocole (l'appelant ne possède pas
        // encore de jeton JWT à ce stade) — l'ancienne condition `path.contains("/api/v1/auth")`
        // désactivait à tort l'analyse du jeton JWT pour TOUT le préfixe "/api/v1/auth/**",
        // y compris "/register" et "/2fa/setup|enable|disable" : ces derniers, bien que
        // désormais protégés par `.anyRequest().authenticated()` dans SecurityConfig,
        // n'auraient jamais reçu d'utilisateur authentifié (le principal serait resté
        // `null` même avec un jeton JWT valide), les rendant inutilisables pour de vrais
        // administrateurs. Seuls les deux chemins réellement publics sont exemptés ici.
        if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/verify-2fa")
                || path.contains("/api/v1/items/photos") || path.contains("/api/v1/items/documents")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userMatricule;
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        jwt = authHeader.substring(7);
        userMatricule = jwtService.extractUsername(jwt);
        
        if (userMatricule != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userMatricule);
            
            // SÉCURITÉ (correctif 2026-09-04) : un jeton JWT signé reste valide jusqu'à son
            // expiration naturelle même après qu'un ADMIN ait bloqué le compte (voir
            // User.enabled / UserAdminController) — sans cette vérification, un compte
            // bloqué continuerait à accéder à l'API avec un jeton déjà émis. isEnabled()
            // n'est vérifié par Spring Security que lors de la CONNEXION initiale
            // (DaoAuthenticationProvider), jamais sur les requêtes suivantes authentifiées
            // par ce filtre JWT stateless — il faut donc le revérifier explicitement ici,
            // à chaque requête, pour une révocation réellement immédiate.
            if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled()) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}
