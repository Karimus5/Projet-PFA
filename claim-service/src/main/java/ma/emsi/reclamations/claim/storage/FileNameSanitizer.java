package ma.emsi.reclamations.claim.storage;

import java.text.Normalizer;

/**
 * Assainit un nom de fichier fourni par l'utilisateur, pour l'AFFICHAGE et l'en-tête
 * Content-Disposition uniquement (il ne sert jamais de chemin sur le disque) :
 * pas de dossier (../, C:\), pas de caractère de contrôle ni spécial, longueur bornée.
 */
public final class FileNameSanitizer {

    private static final int MAX_LENGTH = 100;
    private static final String FALLBACK = "fichier";

    private FileNameSanitizer() {
    }

    public static String sanitize(String original) {
        if (original == null) {
            return FALLBACK;
        }
        // Garde uniquement le dernier segment (certains navigateurs envoient le chemin complet).
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFKC);
        // Lettres (accents compris), chiffres, espace, point, tiret, souligné ; le reste devient « _ ».
        name = name.replaceAll("[^\\p{L}\\p{N} ._-]", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("_{2,}", "_")
                .replaceAll("\\.{2,}", ".")
                .strip();
        // Pas de point ni d'espace en tête ou en fin (fichiers cachés, pièges Windows).
        name = name.replaceAll("^[. ]+", "").replaceAll("[. ]+$", "");
        if (name.length() > MAX_LENGTH) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, MAX_LENGTH - ext.length()).strip() + ext;
        }
        return name.isEmpty() ? FALLBACK : name;
    }
}
