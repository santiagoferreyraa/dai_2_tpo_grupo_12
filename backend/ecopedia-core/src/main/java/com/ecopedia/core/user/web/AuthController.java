package com.ecopedia.core.user.web;

import com.ecopedia.core.user.domain.AuthToken;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import com.ecopedia.core.user.web.dto.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserProfileAssembler profiles;

    public AuthController(UserService userService, UserProfileAssembler profiles) {
        this.userService = userService;
        this.profiles = profiles;
    }

    /** RF01 / ECO-24: Registro de usuarios. */
    @PostMapping("/register")
    @ApiResponse(responseCode = "201", description = "Creado.")
    public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        User registered = userService.register(request.toDomainData());
        return ResponseEntity.status(HttpStatus.CREATED).body(profiles.toResponse(registered));
    }

    /** RF01 / ECO-24: Autenticación y obtención de token JWT. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthToken authToken = userService.authenticate(request.toDomainCredentials());
        return ResponseEntity.ok(AuthResponse.fromDomain(authToken));
    }
}
