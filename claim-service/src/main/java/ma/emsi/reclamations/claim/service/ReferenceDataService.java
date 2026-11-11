package ma.emsi.reclamations.claim.service;

import java.util.List;
import ma.emsi.reclamations.claim.domain.Category;
import ma.emsi.reclamations.claim.domain.CategoryRepository;
import ma.emsi.reclamations.claim.domain.ClaimRepository;
import ma.emsi.reclamations.claim.domain.ProcessingService;
import ma.emsi.reclamations.claim.domain.ProcessingServiceRepository;
import ma.emsi.reclamations.claim.domain.ServiceAgent;
import ma.emsi.reclamations.claim.domain.ServiceAgentRepository;
import ma.emsi.reclamations.claim.error.ApiException;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.web.dto.Dtos.CategoryRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.CategoryResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceAgentRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceAgentResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catégories, services de traitement et rattachement des agents (BF22).
 * Lecture : AGENT et ADMIN (listes déroulantes). Écriture : ADMIN uniquement.
 * Le contrôle de rôle est fait ici même si la gateway le fait aussi (défense en profondeur).
 */
@Service
@Transactional
public class ReferenceDataService {

    private final CategoryRepository categories;
    private final ProcessingServiceRepository services;
    private final ServiceAgentRepository serviceAgents;
    private final ClaimRepository claims;

    public ReferenceDataService(CategoryRepository categories, ProcessingServiceRepository services,
                                ServiceAgentRepository serviceAgents, ClaimRepository claims) {
        this.categories = categories;
        this.services = services;
        this.serviceAgents = serviceAgents;
        this.claims = claims;
    }

    // --- Lecture (AGENT, ADMIN) ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(CurrentUser user) {
        requireStaff(user);
        return categories.findAllByOrderByNameAsc().stream().map(DtoMapper::category).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> listServices(CurrentUser user) {
        requireStaff(user);
        return services.findAllByOrderByNameAsc().stream().map(DtoMapper::service).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceAgentResponse> listAgents(CurrentUser user, Long serviceId) {
        requireStaff(user);
        findService(serviceId);
        return serviceAgents.findByServiceIdOrderByAgentNameAsc(serviceId).stream().map(DtoMapper::agent).toList();
    }

    // --- Services (ADMIN) ------------------------------------------------------------------------

    public ServiceResponse createService(CurrentUser user, ServiceRequest request) {
        requireAdmin(user);
        String name = request.name().trim();
        if (services.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Un service porte déjà ce nom : " + name);
        }
        return DtoMapper.service(services.save(new ProcessingService(name, blankToNull(request.description()))));
    }

    public ServiceResponse updateService(CurrentUser user, Long id, ServiceRequest request) {
        requireAdmin(user);
        ProcessingService service = findService(id);
        String name = request.name().trim();
        if (services.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("Un service porte déjà ce nom : " + name);
        }
        service.update(name, blankToNull(request.description()));
        return DtoMapper.service(services.saveAndFlush(service));
    }

    public void deleteService(CurrentUser user, Long id) {
        requireAdmin(user);
        ProcessingService service = findService(id);
        if (categories.existsByServiceId(id)) {
            throw ApiException.conflict("Ce service traite encore des catégories : réaffectez-les d'abord");
        }
        if (claims.existsByServiceId(id)) {
            throw ApiException.conflict("Des réclamations sont rattachées à ce service : suppression impossible");
        }
        serviceAgents.deleteAll(serviceAgents.findByServiceIdOrderByAgentNameAsc(id));
        services.delete(service);
    }

    // --- Catégories (ADMIN) ----------------------------------------------------------------------

    public CategoryResponse createCategory(CurrentUser user, CategoryRequest request) {
        requireAdmin(user);
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Une catégorie porte déjà ce nom : " + name);
        }
        ProcessingService service = findServiceForBody(request.serviceId());
        return DtoMapper.category(categories.save(new Category(name, blankToNull(request.description()), service)));
    }

    public CategoryResponse updateCategory(CurrentUser user, Long id, CategoryRequest request) {
        requireAdmin(user);
        Category category = categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("Catégorie introuvable : " + id));
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("Une catégorie porte déjà ce nom : " + name);
        }
        category.update(name, blankToNull(request.description()), findServiceForBody(request.serviceId()));
        return DtoMapper.category(categories.saveAndFlush(category));
    }

    public void deleteCategory(CurrentUser user, Long id) {
        requireAdmin(user);
        Category category = categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("Catégorie introuvable : " + id));
        if (claims.existsByCategoryId(id)) {
            throw ApiException.conflict("Des réclamations utilisent cette catégorie : suppression impossible");
        }
        categories.delete(category);
    }

    // --- Agents d'un service (ADMIN) -------------------------------------------------------------

    /**
     * Rattache un agent à un service. L'id et le nom sont fournis par l'admin (id du compte dans
     * auth-service) : claim-service n'interroge pas auth-service et ne peut donc pas vérifier que ce
     * compte existe, est actif et a bien le rôle AGENT (limite documentée dans le README).
     */
    public ServiceAgentResponse addAgent(CurrentUser user, Long serviceId, ServiceAgentRequest request) {
        requireAdmin(user);
        ProcessingService service = findService(serviceId);
        if (serviceAgents.findByServiceIdAndAgentId(serviceId, request.agentId()).isPresent()) {
            throw ApiException.conflict("Cet agent est déjà rattaché au service « " + service.getName() + " »");
        }
        return DtoMapper.agent(serviceAgents.save(
                new ServiceAgent(service, request.agentId(), request.agentName().trim())));
    }

    /** Les réclamations déjà affectées à cet agent le restent (il les voit toujours). */
    public void removeAgent(CurrentUser user, Long serviceId, Long agentId) {
        requireAdmin(user);
        findService(serviceId);
        ServiceAgent link = serviceAgents.findByServiceIdAndAgentId(serviceId, agentId)
                .orElseThrow(() -> ApiException.notFound("Cet agent n'est pas rattaché à ce service"));
        serviceAgents.delete(link);
    }

    // --- Utilitaires ------------------------------------------------------------------------------

    private ProcessingService findService(Long id) {
        return services.findById(id).orElseThrow(() -> ApiException.notFound("Service introuvable : " + id));
    }

    /** Service référencé dans un corps de requête : inconnu → 400 (et non 404). */
    private ProcessingService findServiceForBody(Long id) {
        return services.findById(id).orElseThrow(() -> ApiException.badRequest("Service inconnu : " + id));
    }

    private static void requireStaff(CurrentUser user) {
        if (!user.role().isStaff()) {
            throw ApiException.forbidden("Accès refusé : droits insuffisants");
        }
    }

    private static void requireAdmin(CurrentUser user) {
        if (!user.isAdmin()) {
            throw ApiException.forbidden("Accès refusé : droits insuffisants");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
