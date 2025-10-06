package in.bushansirgur.cloudshareapi.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClerkJwtAuthFilter extends OncePerRequestFilter {

    @Value("${clerk.issuer}")
    private String clerkIssuer;

    private final ClerkJwksProvider jwksProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        log.info("ClerkJwtAuthFilter: Processing {} {} (Issuer: {})", method, path, clerkIssuer);  // Entry log

        // For webhook endpoints, skip JWT validation and continue the filter chain
        if (request.getRequestURI().contains("/webhooks") ||
                request.getRequestURI().contains("/public") ||
                request.getRequestURI().contains("/download") ||
                request.getRequestURI().contains("/health")) {
            log.info("Skipping auth for public path: {} {}", method, path);
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        log.debug("Auth header for {} {}: {}", method, path, authHeader != null ? authHeader.substring(0, Math.min(20, authHeader.length())) + "..." : "Missing");  // Header preview

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Invalid/Missing auth header for {} {} - Sending 403", method, path);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Authorization header missing/invalid");
            return;
        }

        try {
            String token = authHeader.substring(7); // remove "Bearer "
            log.debug("Extracted token length for {} {}: {}", method, path, token.length());

            String[] chunks = token.split("\\.");
            if (chunks.length < 3) {
                log.warn("Invalid JWT format (chunks: {}) for {} {} - Sending 403", chunks.length, method, path);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid JWT token format");
                return;
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(chunks[0]));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode headerNode = mapper.readTree(headerJson);
            log.debug("Parsed JWT header for {} {}", method, path);

            if (!headerNode.has("kid")) {
                log.warn("Missing 'kid' in JWT header for {} {} - Sending 403", method, path);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token header is missing kid");
                return;
            }

            String kid = headerNode.get("kid").asText();
            log.debug("JWT kid extracted: {} for {} {}", kid, method, path);

            PublicKey publicKey = jwksProvider.getPublicKey(kid);
            if (publicKey == null) {
                log.warn("PublicKey not found for kid: {} on {} {} - Sending 403", kid, method, path);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid key ID in token");
                return;
            }
            log.debug("PublicKey fetched successfully for kid: {} on {} {}", kid, method, path);

            // Verify the token
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .setAllowedClockSkewSeconds(60)
                    .requireIssuer(clerkIssuer)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String clerkId = claims.getSubject();
            log.info("Token VALID for user: {} (claims: {}) on {} {} - Proceeding", clerkId, claims.getIssuer(), method, path);  // Success log

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    clerkId, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.error("JWT Exception for {} {}: {} - {}", method, path, e.getClass().getSimpleName(), e.getMessage());  // Specific JWT errors (e.g., ExpiredJwtException, SignatureException)
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid JWT token: " + e.getMessage());
        } catch (Exception e) {
            log.error("General Exception in auth filter for {} {}: {}", method, path, e.getMessage(), e);  // Other errors
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid JWT token: " + e.getMessage());
        }
    }
}