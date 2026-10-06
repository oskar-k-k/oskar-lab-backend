package dev.oskar_lab.backend.tierlist;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.util.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
@RequestMapping("/tierlist")
public class TierlistController {
    private final TierlistService service;
    public TierlistController(TierlistService service) { this.service = service; }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failure(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", Objects.requireNonNullElse(error.getReason(), "Ungültige Anfrage.")));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> malformed() {
        return ResponseEntity.badRequest().body(Map.of("message", "Ungültige JSON-Anfrage."));
    }
    public record Create(String name, String category, List<String> rows, List<String> extraWords, Integer rounds) {}
    public record Join(String name) {}
    public record Action(String action, Integer round, Integer row) {}
    @PostMapping("/rooms")
    public Map<String, Object> create(@RequestBody Create body) { return service.create(body.name(), body.category(), body.rows(), body.extraWords(), body.rounds()); }
    @PostMapping("/rooms/{code}/join")
    public Map<String, Object> join(@PathVariable String code, @RequestBody Join body) { return service.join(code, body.name()); }
    @GetMapping("/rooms/{code}")
    public ResponseEntity<Map<String, Object>> view(@PathVariable String code, @RequestHeader(value = "X-Tierlist-Token", defaultValue = "") String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.view(code, token));
    }
    @PostMapping("/rooms/{code}/actions")
    public Map<String, Boolean> action(@PathVariable String code, @RequestHeader(value = "X-Tierlist-Token", defaultValue = "") String token, @RequestBody Action body) {
        service.action(code, token, body.action(), body.round(), body.row()); return Map.of("ok", true);
    }
}
