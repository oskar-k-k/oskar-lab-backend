package dev.oskar_lab.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Private server-to-server boundary; browsers never receive the bridge key or session token. */
@RestController
@RequestMapping("/internal/auth")
public class AuthController {
    private final AccountService accounts;
    private final String bridgeSecret;
    private final Map<String, AttemptWindow> attempts = new HashMap<>();
    private record AttemptWindow(long until, int count) {}

    public AuthController(AccountService accounts, @Value("${auth.bridge-secret:}") String bridgeSecret) {
        this.accounts = accounts;
        this.bridgeSecret = bridgeSecret;
    }

    @PostMapping("/{action}")
    public Map<String, Object> invoke(@PathVariable String action,
            @RequestHeader(value = "X-Auth-Bridge", defaultValue = "") String key,
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
            @RequestBody Map<String, Object> input) {
        if (bridgeSecret.length() < 32 || !MessageDigest.isEqual(bridgeSecret.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) throw new AuthFailure(403, "forbidden");
        if (Set.of("register", "login", "google", "link").contains(action)) {
            limit("global", 120, 60_000);
            String identifier = String.valueOf(input.getOrDefault("identifier", input.getOrDefault("email", ""))).strip().toLowerCase(Locale.ROOT);
            limit(action + ":" + identifier, 10, 900_000);
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        return switch (action) {
            case "register" -> accounts.register(input);
            case "login" -> accounts.login(input);
            case "google" -> accounts.google(input);
            case "link" -> accounts.linkGoogle(input);
            case "complete" -> accounts.complete(token, input);
            case "current" -> accounts.current(token);
            case "logout" -> { accounts.logout(token); yield Map.of("ok", true); }
            default -> throw new AuthFailure(404, "notFound");
        };
    }

    private synchronized void limit(String key, int maximum, long duration) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> entry.getValue().until < now);
        AttemptWindow window = attempts.getOrDefault(key, new AttemptWindow(now + duration, 0));
        if (window.count >= maximum || attempts.size() >= 10_000 && !attempts.containsKey(key)) throw new AuthFailure(429, "rateLimited");
        attempts.put(key, new AttemptWindow(window.until, window.count + 1));
    }

    @ExceptionHandler(AuthFailure.class)
    public ResponseEntity<Map<String, String>> failure(AuthFailure error) {
        return ResponseEntity.status(error.status).body(Map.of("code", error.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(409).body(Map.of("code", "accountExists"));
    }
}
