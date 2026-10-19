package dev.oskar_lab.backend.apps.catalog

import groovy.transform.CompileStatic
import org.springframework.web.bind.annotation.RestController;
import dev.oskar_lab.backend.core.AbstractController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.http.ResponseEntity
import org.springframework.data.domain.Page
import jakarta.validation.Valid
import dev.oskar_lab.backend.core.ListRequest

@CompileStatic
@RestController
@RequestMapping(["/apps", "/projects"])
class AppsController extends AbstractController<AppService>{

    AppsController(AppService service){
        super(service)
    }

    /** Lists catalog entries with a concrete response type for OpenAPI clients. */
    @Override
    @PostMapping
    ResponseEntity<Page<AppEntity>> list(@Valid @RequestBody ListRequest request) {
        return ResponseEntity.ok(service.getAll(request))
    }

    /** Returns one catalog entry, or 404 when it does not exist. */
    @Override
    @GetMapping("/{id}")
    ResponseEntity<AppEntity> get(@PathVariable("id") Long id) {
        AppEntity app = service.get(id)
        return app == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(app)
    }

}
