package dev.oskar_lab.backend.core

import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody;

abstract class AbstractController<T> {

    protected final T service

    AbstractController(T service){
        this.service = service
    }

    @PostMapping
    ResponseEntity<?> list(@RequestBody ListRequest listRequest) {
        return ResponseEntity.ok(service.getAll(listRequest))
    }

    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.get(id))
    }
}
