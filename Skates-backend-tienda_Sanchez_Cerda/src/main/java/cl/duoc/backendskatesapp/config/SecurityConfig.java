package cl.duoc.backendskatesapp.config;

import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    JwtDecoder cognitoJwtDecoder(
            @Value("${app.cognito.issuer-uri:}") String issuerUri,
            @Value("${app.cognito.client-ids:}") String clientIdsValue) {
        Set<String> clientIds = Arrays.stream(clientIdsValue.split(","))
                .map(clientId -> clientId == null ? "" : clientId.trim())
                .filter(clientId -> !clientId.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (issuerUri.isBlank() || clientIds.isEmpty()) {
            return token -> {
                throw new JwtException("Configure COGNITO_ISSUER_URI and COGNITO_CLIENT_IDS to enable authentication");
            };
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(issuerUri + "/.well-known/jwks.json")
                .build();
        OAuth2TokenValidator<Jwt> cognitoValidator = jwt -> {
            if (!"access".equals(jwt.getClaimAsString("token_use"))) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Cognito access token required", null));
            }
            if (!clientIds.contains(jwt.getClaimAsString("client_id"))) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token was not issued for this app client", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                cognitoValidator));
        return decoder;
    }

    @Bean
    WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers("/h2-console", "/h2-console/**");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/payments/webpay/return", "/api/pago/return").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/h2-console").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAuthority("SCOPE_skate-api/read")
                        .requestMatchers(HttpMethod.POST, "/api/**")
                        .hasAuthority("SCOPE_skate-api/write")
                        .requestMatchers(HttpMethod.PUT, "/api/**")
                        .hasAuthority("SCOPE_skate-api/write")
                        .requestMatchers(HttpMethod.PATCH, "/api/**")
                        .hasAuthority("SCOPE_skate-api/write")
                        .requestMatchers(HttpMethod.DELETE, "/api/**")
                        .hasAuthority("SCOPE_skate-api/write")
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> groups = jwt.getClaimAsStringList("cognito:groups");
            var scopeAuthorities = new JwtGrantedAuthoritiesConverter().convert(jwt);
            List<GrantedAuthority> authorities = scopeAuthorities == null
                    ? new java.util.ArrayList<>()
                    : new java.util.ArrayList<>(scopeAuthorities);
            if (groups != null) {
                authorities.addAll(groups.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList());
            }
            return authorities;
        });
        return converter;
    }
}