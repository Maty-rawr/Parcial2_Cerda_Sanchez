package cl.duoc.backendskatesapp.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("username");
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }
        String email = jwt.getClaimAsString("email");
        return email == null
                ? Map.of("username", username)
                : Map.of("username", username, "email", email);
    }
}