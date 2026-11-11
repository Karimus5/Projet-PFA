package ma.emsi.reclamations.auth.web;

import org.springframework.security.oauth2.jwt.Jwt;

/** L'identifiant de l'utilisateur connecté est le « subject » (sub) du JWT. */
final class CurrentUser {

    private CurrentUser() {
    }

    static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
