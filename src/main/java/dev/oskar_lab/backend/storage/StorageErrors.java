package dev.oskar_lab.backend.storage;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/** Preserves safe storage status codes without forwarding cloud errors or credentials to clients. */
@RestControllerAdvice
@org.springframework.core.annotation.Order(-10)
public class StorageErrors {
    @ExceptionHandler(StorageFailure.class)
    public ResponseEntity<Map<String, String>> failure(StorageFailure error) {
        return ResponseEntity.status(error.status).header("Cache-Control", "private, no-store").body(Map.of("code", error.getMessage()));
    }
}
