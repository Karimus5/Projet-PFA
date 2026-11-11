package ma.emsi.reclamations.auth.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.emsi.reclamations.auth.service.UserService;
import ma.emsi.reclamations.auth.web.dto.Dtos.ChangeRoleRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.ChangeStatusRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.CreateUserRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des utilisateurs (BF21). Réservé au rôle ADMIN : la règle est posée dans SecurityConfig
 * sur /api/auth/admin/** (un autre rôle reçoit 403).
 */
@RestController
@RequestMapping("/api/auth/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.listUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createStaffUser(request);
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody ChangeRoleRequest request) {
        return userService.changeRole(CurrentUser.id(jwt), id, request.role());
    }

    @PatchMapping("/{id}/status")
    public UserResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @Valid @RequestBody ChangeStatusRequest request) {
        return userService.setActive(CurrentUser.id(jwt), id, request.active());
    }
}
