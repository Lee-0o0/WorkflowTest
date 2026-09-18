package com.workflowtest.server.api;

import com.workflowtest.server.api.ApiModels.*;
import com.workflowtest.server.application.UserService;
import com.workflowtest.server.security.SecuritySupport;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    public AuthController(UserService users) { this.users = users; }
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest request) { return users.login(request); }
    @GetMapping("/me") public Object me() { return SecuritySupport.current(); }
}
