package com.workflowtest.server.api;

import com.workflowtest.server.api.ApiModels.*;
import com.workflowtest.server.application.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }
    @GetMapping public List<UserView> list() { return users.list(); }
    @PostMapping public UserView create(@Valid @RequestBody SaveUserRequest request) { return users.save(null, request); }
    @PutMapping("/{id}") public UserView update(@PathVariable String id, @Valid @RequestBody SaveUserRequest request) { return users.save(id, request); }
}
