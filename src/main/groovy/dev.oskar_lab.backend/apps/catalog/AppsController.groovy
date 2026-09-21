package dev.oskar_lab.backend.apps.catalog

import groovy.transform.CompileStatic
import org.springframework.web.bind.annotation.RestController;
import dev.oskar_lab.backend.core.AbstractController;
import org.springframework.web.bind.annotation.RequestMapping;

@CompileStatic
@RestController
@RequestMapping(["/apps", "/projects"])
class AppsController extends AbstractController<AppService>{

    AppsController(AppService service){
        super(service)
    }

}
