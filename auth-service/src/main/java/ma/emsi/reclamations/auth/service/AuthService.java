package ma.emsi.reclamations.auth.service;

import ma.emsi.reclamations.auth.error.ApiException;
import ma.emsi.reclamations.auth.security.TokenService;
import ma.emsi.reclamations.auth.user.EmailNormalizer;
import ma.emsi.reclamations.auth.user.Role;
import ma.emsi.reclamations.auth.user.User;
import ma.emsi.reclamations.auth.user.UserRepository;
import ma.emsi.reclamations.auth.web.dto.Dtos.LoginRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.RegisterRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.TokenResponse;
import ma.emsi.reclamations.auth.web.dto.Dtos.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inscription des clients (BF1) et connexion (BF2). */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Hachage factice comparé quand l'email est inconnu : la réponse prend alors autant de temps
     * qu'avec un mauvais mot de passe, ce qui empêche de deviner les emails inscrits.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("mot-de-passe-factice-pour-le-timing");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.emailAlreadyUsed();
        }
        // Rôle imposé côté serveur : un visiteur ne peut s'inscrire que comme CLIENT.
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), Role.CLIENT);
        return UserResponse.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        User user = users.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw ApiException.invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.invalidCredentials();
        }
        // Vérifié APRÈS le mot de passe : seul le titulaire du compte apprend qu'il est désactivé.
        if (!user.isActive()) {
            throw ApiException.accountDisabled();
        }

        TokenService.IssuedToken token = tokenService.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }
}
