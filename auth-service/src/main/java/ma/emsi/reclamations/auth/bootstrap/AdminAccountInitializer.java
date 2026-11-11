package ma.emsi.reclamations.auth.bootstrap;

import ma.emsi.reclamations.auth.user.EmailNormalizer;
import ma.emsi.reclamations.auth.user.Role;
import ma.emsi.reclamations.auth.user.User;
import ma.emsi.reclamations.auth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Au démarrage, crée le compte ADMIN initial (ADMIN_EMAIL / ADMIN_PASSWORD du .env) s'il n'existe
 * encore AUCUN administrateur. Ne fait rien ensuite : changer ces variables ne modifie pas un admin
 * existant. Le mot de passe n'est jamais écrit dans les logs.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties admin;

    public AdminAccountInitializer(UserRepository users, PasswordEncoder passwordEncoder, AdminProperties admin) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.admin = admin;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            log.info("Compte ADMIN déjà présent : aucun compte initial créé.");
            return;
        }
        if (!StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            log.warn("Aucun compte ADMIN en base et ADMIN_EMAIL / ADMIN_PASSWORD non définis dans .env : "
                    + "aucun administrateur ne pourra se connecter.");
            return;
        }
        if (admin.password().length() < 8) {
            log.warn("ADMIN_PASSWORD doit contenir au moins 8 caractères : compte ADMIN initial non créé.");
            return;
        }
        String email = EmailNormalizer.normalize(admin.email());
        if (users.existsByEmail(email)) {
            log.warn("ADMIN_EMAIL ({}) correspond déjà à un compte non administrateur : compte initial non créé.", email);
            return;
        }
        String name = StringUtils.hasText(admin.name()) ? admin.name().trim() : "Administrateur";
        users.save(new User(name, email, passwordEncoder.encode(admin.password()), Role.ADMIN));
        log.info("Compte ADMIN initial créé : {}", email);
    }

    @ConfigurationProperties(prefix = "bootstrap.admin")
    public record AdminProperties(String email, String password, String name) {

        @Override
        public String toString() {
            return "AdminProperties[email=" + email + ", name=" + name + "]";
        }
    }
}
