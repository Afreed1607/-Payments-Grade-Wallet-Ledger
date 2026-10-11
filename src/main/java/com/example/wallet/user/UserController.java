package com.example.wallet.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final JdbcTemplate jdbc;

    public UserController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    public Map<String, Object> create(@RequestHeader("Idempotency-Key") UUID idempotencyKey, @Valid @RequestBody CreateUser request) {
        Long id = jdbc.queryForObject("INSERT INTO users(name, email, phone) VALUES (?, ?, ?) RETURNING id", Long.class, request.name(), request.email().toLowerCase(), request.phone());
        return Map.of("id", id, "name", request.name(), "email", request.email().toLowerCase(), "phone", request.phone());
    }

    public record CreateUser(@NotBlank String name, @NotBlank @Email String email, @NotBlank String phone) {}
}
