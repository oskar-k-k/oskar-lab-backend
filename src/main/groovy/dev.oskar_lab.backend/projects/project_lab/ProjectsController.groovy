package dev.oskar_lab.backend.projects.project_lab

import groovy.transform.CompileStatic
import org.springframework.web.bind.annotation.RestController;
import dev.oskar_lab.backend.core.AbstractController;
import org.springframework.web.bind.annotation.RequestMapping;

@CompileStatic
@RestController
@RequestMapping("/projects")
class ProjectsController extends AbstractController<ProjectService>{

    ProjectsController(ProjectService service){
        super(service)
    }

}
