package dev.oskar_lab.backend.workout;

import dev.oskar_lab.backend.auth.AuthFailure;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static dev.oskar_lab.backend.workout.WorkoutDtos.*;

/** Trusted platform bridge; account IDs are accepted only from authenticated app servers. */
@RestController
@RequestMapping("/internal/workout")
public class WorkoutController {
    private final WorkoutService service;
    private final String secret;
    private final TrackingService tracking;
    public WorkoutController(WorkoutService service, TrackingService tracking, @Value("${auth.bridge-secret:}") String secret) {
        this.service = service; this.tracking = tracking; this.secret = secret;
    }

    @ModelAttribute
    void authorize(@RequestHeader(value = "X-Auth-Bridge", defaultValue = "") String key) {
        if (secret.length() < 32 || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new AuthFailure(403, "unauthorized");
    }

    @GetMapping("/exercises")
    public List<ExerciseDto> exercises() { return service.catalog(); }
    @GetMapping("/templates")
    public List<Plan> templates() { return service.templates(); }
    @GetMapping("/plans")
    public PlanPage list(@RequestHeader("X-User-Id") UUID owner, @RequestParam(defaultValue = "0") int page) {
        service.requireAccount(owner);
        if (page < 0 || page > 10000) throw new AuthFailure(400, "invalidPlan");
        return service.list(owner, page);
    }
    @GetMapping("/plans/{id}")
    public Plan get(@RequestHeader("X-User-Id") UUID owner, @PathVariable UUID id) {
        service.requireAccount(owner); return service.get(owner, id);
    }
    @PostMapping("/plans")
    public ResponseEntity<Plan> create(@RequestHeader("X-User-Id") UUID owner, @Valid @RequestBody SavePlan input) {
        service.requireAccount(owner); return ResponseEntity.status(201).body(service.save(owner, input, true));
    }
    @PutMapping("/plans/{id}")
    public Plan update(@RequestHeader("X-User-Id") UUID owner, @PathVariable UUID id, @Valid @RequestBody SavePlan input) {
        service.requireAccount(owner);
        if (!id.equals(input.id())) throw new AuthFailure(400, "invalidPlan");
        return service.save(owner, input, false);
    }
    /** Appends actual sets to the authenticated user's exercise history. */
    @PostMapping("/logs")
    public ResponseEntity<TrackingDtos.Log> record(@RequestHeader("X-User-Id") UUID owner, @Valid @RequestBody TrackingDtos.SaveLog input) {
        service.requireAccount(owner);
        return ResponseEntity.status(201).body(tracking.save(owner, input));
    }
    /** Reads a bounded page of personal history across all plans using this exercise. */
    @GetMapping("/history/{exerciseId}")
    public TrackingDtos.History history(@RequestHeader("X-User-Id") UUID owner, @PathVariable UUID exerciseId,
            @RequestParam(defaultValue = "0") int page) {
        service.requireAccount(owner);
        if (page < 0 || page > 10000) throw new AuthFailure(400, "invalidTracking");
        return tracking.history(owner, exerciseId, page);
    }
    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<Map<String, String>> failure(AuthFailure error) {
        return ResponseEntity.status(error.status).body(Map.of("code", error.getMessage()));
    }
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<Map<String, String>> conflict() { return ResponseEntity.status(409).body(Map.of("code", "conflict")); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid() { return ResponseEntity.badRequest().body(Map.of("code", "invalidPlan")); }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class})
    ResponseEntity<Map<String, String>> malformed() { return ResponseEntity.badRequest().body(Map.of("code", "invalidPlan")); }
}
