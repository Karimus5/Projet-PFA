package ma.emsi.reclamations.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Fabrique des jetons comme le fait auth-service (HS256, mêmes claims), pour tester la passerelle
 * sans démarrer auth-service. Le secret correspond à src/test/resources/application.yml.
 */
final class TestTokens {

    static final String SECRET = "secret-de-test-gateway-uniquement-0123456789";
    static final String ISSUER = "reclamations-auth-service";

    private TestTokens() {
    }

    static String valid(long userId, String email, String role) {
        Instant now = Instant.now();
        return sign(SECRET, ISSUER, userId, email, role, now, now.plus(Duration.ofMinutes(15)));
    }

    /** Expiré depuis 5 minutes (au-delà de la tolérance d'horloge de 60 s). */
    static String expired(long userId, String email, String role) {
        Instant now = Instant.now();
        return sign(SECRET, ISSUER, userId, email, role,
                now.minus(Duration.ofHours(1)), now.minus(Duration.ofMinutes(5)));
    }

    /** Signé avec un AUTRE secret : signature invalide pour la passerelle. */
    static String forged(long userId, String email, String role) {
        Instant now = Instant.now();
        return sign("un-autre-secret-que-celui-de-la-gateway-9876", ISSUER, userId, email, role,
                now, now.plus(Duration.ofMinutes(15)));
    }

    static String wrongIssuer(long userId, String email, String role) {
        Instant now = Instant.now();
        return sign(SECRET, "emetteur-inconnu", userId, email, role, now, now.plus(Duration.ofMinutes(15)));
    }

    private static String sign(String secret, String issuer, long userId, String email, String role,
                               Instant issuedAt, Instant expiresAt) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
