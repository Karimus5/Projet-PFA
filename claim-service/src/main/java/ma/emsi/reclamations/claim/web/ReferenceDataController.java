package ma.emsi.reclamations.claim.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.service.ReferenceDataService;
import ma.emsi.reclamations.claim.web.dto.Dtos.CategoryRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.CategoryResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceAgentRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceAgentResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Référentiels (BF22).
 * <ul>
 *   <li>Lecture, AGENT et ADMIN (listes déroulantes) : /api/claims/categories, /api/claims/services,
 *       /api/claims/services/{id}/agents ;</li>
 *   <li>Écriture, ADMIN : /api/claims/admin/services, /api/claims/admin/categories,
 *       /api/claims/admin/services/{id}/agents.</li>
 * </ul>
 */
@RestController
public class ReferenceDataController {

    private final ReferenceDataService referenceData;

    public ReferenceDataController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    // --- Lecture (AGENT, ADMIN) ------------------------------------------------------------------

    @GetMapping("/api/claims/categories")
    List<CategoryResponse> categories(CurrentUser user) {
        return referenceData.listCategories(user);
    }

    @GetMapping("/api/claims/services")
    List<ServiceResponse> services(CurrentUser user) {
        return referenceData.listServices(user);
    }

    @GetMapping("/api/claims/services/{id:\\d+}/agents")
    List<ServiceAgentResponse> agents(CurrentUser user, @PathVariable Long id) {
        return referenceData.listAgents(user, id);
    }

    // --- Services (ADMIN) ------------------------------------------------------------------------

    @PostMapping("/api/claims/admin/services")
    @ResponseStatus(HttpStatus.CREATED)
    ServiceResponse createService(CurrentUser user, @Valid @RequestBody ServiceRequest request) {
        return referenceData.createService(user, request);
    }

    @PutMapping("/api/claims/admin/services/{id:\\d+}")
    ServiceResponse updateService(CurrentUser user, @PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return referenceData.updateService(user, id, request);
    }

    @DeleteMapping("/api/claims/admin/services/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteService(CurrentUser user, @PathVariable Long id) {
        referenceData.deleteService(user, id);
    }

    // --- Catégories (ADMIN) ----------------------------------------------------------------------

    @PostMapping("/api/claims/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse createCategory(CurrentUser user, @Valid @RequestBody CategoryRequest request) {
        return referenceData.createCategory(user, request);
    }

    @PutMapping("/api/claims/admin/categories/{id:\\d+}")
    CategoryResponse updateCategory(CurrentUser user, @PathVariable Long id,
                                    @Valid @RequestBody CategoryRequest request) {
        return referenceData.updateCategory(user, id, request);
    }

    @DeleteMapping("/api/claims/admin/categories/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(CurrentUser user, @PathVariable Long id) {
        referenceData.deleteCategory(user, id);
    }

    // --- Agents d'un service (ADMIN) -------------------------------------------------------------

    @PostMapping("/api/claims/admin/services/{id:\\d+}/agents")
    @ResponseStatus(HttpStatus.CREATED)
    ServiceAgentResponse addAgent(CurrentUser user, @PathVariable Long id,
                                  @Valid @RequestBody ServiceAgentRequest request) {
        return referenceData.addAgent(user, id, request);
    }

    @DeleteMapping("/api/claims/admin/services/{id:\\d+}/agents/{agentId:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeAgent(CurrentUser user, @PathVariable Long id, @PathVariable Long agentId) {
        referenceData.removeAgent(user, id, agentId);
    }
}
