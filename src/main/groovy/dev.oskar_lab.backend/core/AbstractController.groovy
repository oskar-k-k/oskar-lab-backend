package dev.oskar_lab.backend.core

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody;

abstract class AbstractController<S> {

    protected final S service

    AbstractController(S service){
        this.service = service
    }

    @PostMapping
    ResponseEntity<?> list(@Valid @RequestBody ListRequest listRequest) {
        return ResponseEntity.ok(service.getAll(listRequest))
    }

    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable("id") Long id) {
        def entity = service.get(id)
        return entity == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(entity)
    }
}
