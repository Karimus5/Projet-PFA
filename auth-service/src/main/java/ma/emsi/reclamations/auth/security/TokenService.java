package ma.emsi.reclamations.auth.security;

import java.time.Instant;
import ma.emsi.reclamations.auth.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Émet les jetons d'accès JWT signés en HS256. */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.expiration()))
                .subject(String.valueOf(user.getId()))
                .claim(JwtProperties.EMAIL_CLAIM, user.getEmail())
                .claim(JwtProperties.ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, properties.expiration().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
