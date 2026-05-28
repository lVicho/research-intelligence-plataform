package com.researchintelligence.platform.auth.api;

import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        return toResponse(authentication);
    }

    @PostMapping("/logout")
    public void logout() {
        // Stateless HTTP Basic authentication: the client discards its stored credentials.
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        return toResponse(authentication);
    }

    private AuthUserResponse toResponse(Authentication authentication) {
        PlatformUserPrincipal principal = (PlatformUserPrincipal) authentication.getPrincipal();
        return new AuthUserResponse(
            principal.id(),
            principal.email(),
            principal.displayName(),
            principal.roles(),
            principal.researcherId()
        );
    }
}
