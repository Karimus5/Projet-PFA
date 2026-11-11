package ma.emsi.reclamations.claim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Rattachement d'un agent à un service, déclaré par l'administrateur.
 *
 * <p><b>Limite assumée</b> : claim-service n'appelle pas auth-service. agentId (= id du compte dans
 * auth-service) et agentName sont saisis par l'admin et stockés tels quels : rien ne garantit que
 * l'id corresponde à un compte AGENT actif, et un changement de nom dans auth-service n'est pas
 * répercuté ici.
 */
@Entity
@Table(name = "service_agents")
public class ServiceAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ProcessingService service;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ServiceAgent() {
        // requis par JPA
    }

    public ServiceAgent(ProcessingService service, Long agentId, String agentName) {
        this.service = service;
        this.agentId = agentId;
        this.agentName = agentName;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void rename(String agentName) {
        this.agentName = agentName;
    }

    public Long getId() {
        return id;
    }

    public ProcessingService getService() {
        return service;
    }

    public Long getAgentId() {
        return agentId;
    }

    public String getAgentName() {
        return agentName;
    }
}
