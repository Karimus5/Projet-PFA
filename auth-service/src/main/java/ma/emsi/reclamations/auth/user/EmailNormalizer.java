package ma.emsi.reclamations.auth.user;

import java.util.Locale;

/** Les emails sont comparés sans tenir compte de la casse : on les stocke en minuscules. */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
