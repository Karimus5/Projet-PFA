package ma.emsi.reclamations.gateway.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres JWT PARTAGÉS avec auth-service (config-repo/application.yml, préfixe security.jwt).
 * Clé HS256 = octets UTF-8 de JWT_SECRET, exactement comme dans auth-service.
 * La passerelle refuse de démarrer si le secret est absent ou trop court.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, String issuer) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.jwt.secret est vide : définir JWT_SECRET dans .env (le même que pour auth-service)");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET doit contenir au moins 32 caractères (HS256)");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("security.jwt.issuer est vide");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Override
    public String toString() {
        // Ne jamais afficher le secret.
        return "JwtProperties[issuer=" + issuer + "]";
    }
}
