package ma.emsi.reclamations.auth.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Paramètres JWT (préfixe security.jwt). secret et issuer sont définis dans la configuration
 * PARTAGÉE (config-repo/application.yml) pour que la gateway utilise exactement les mêmes.
 *
 * <p>Clé HS256 = octets UTF-8 du secret. Le service refuse de démarrer si JWT_SECRET est absent
 * ou trop court (HS256 exige une clé d'au moins 256 bits).
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET doit être défini (fichier .env ou variable d'environnement)")
        @Size(min = 32, message = "JWT_SECRET doit contenir au moins 32 caractères (HS256)")
        String secret,
        @NotBlank String issuer,
        @NotNull Duration expiration) {

    public static final String ROLE_CLAIM = "role";
    public static final String EMAIL_CLAIM = "email";

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Override
    public String toString() {
        // Ne jamais afficher le secret dans les logs.
        return "JwtProperties[issuer=" + issuer + ", expiration=" + expiration + "]";
    }
}
