package ma.emsi.reclamations.auth.web;

import jakarta.validation.Valid;
import ma.emsi.reclamations.auth.service.AuthService;
import ma.emsi.reclamations.auth.service.UserService;
import ma.emsi.reclamations.auth.web.dto.Dtos.LoginRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.RegisterRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.TokenResponse;
import ma.emsi.reclamations.auth.web.dto.Dtos.UpdateProfileRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inscription, connexion et profil. Chemins /api/auth/** (route de l'API Gateway). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(CurrentUser.id(jwt));
    }

    @PutMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(CurrentUser.id(jwt), request);
    }
}
