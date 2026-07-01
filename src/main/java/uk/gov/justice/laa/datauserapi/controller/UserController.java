package uk.gov.justice.laa.datauserapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.dto.UserDto;
import uk.gov.justice.laa.datauserapi.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Endpoints for managing and querying users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get users", description = "Get a paginated list of users based on search criteria. All filter parameters support multiple values.")
    public ResponseEntity<Page<UserDto>> getUsers(
            @Parameter(description = "SiLAS ID(s) to filter by") @RequestParam(value = "id", required = false) List<String> ids,
            @Parameter(description = "Entra OID(s) to filter by") @RequestParam(value = "oid", required = false) List<String> oids,
            @Parameter(description = "Email(s) to filter by") @RequestParam(value = "email", required = false) List<String> emails,
            @Parameter(description = "Profile ID(s) to filter by") @RequestParam(value = "profile-id", required = false) List<String> profileIds,
            @Parameter(description = "Role name(s) to filter by") @RequestParam(value = "role-name", required = false) List<String> roleNames,
            @Parameter(description = "App name(s) to filter by") @RequestParam(value = "app-name", required = false) List<String> appNames,
            @Parameter(description = "Office ID(s) to filter by") @RequestParam(value = "office", required = false) List<String> offices,
            @Parameter(description = "Firm ID(s) to filter by") @RequestParam(value = "firm", required = false) List<String> firms,
            @ParameterObject Pageable pageable) {

        UserSearchCriteria criteria = new UserSearchCriteria(
                ids, oids, emails, profileIds, roleNames, appNames, offices, firms
        );

        Page<UserDto> users = userService.findUsers(criteria, pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user details", description = "Get detailed information about a user profile including their roles, offices, and firm.")
    public ResponseEntity<uk.gov.justice.laa.datauserapi.dto.UserDetailDto> getUserById(
            @Parameter(description = "The UUID of the User Profile") @org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
