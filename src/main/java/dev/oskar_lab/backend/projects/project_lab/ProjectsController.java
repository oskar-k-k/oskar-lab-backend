package dev.oskar_lab.backend.projects.project_lab;

import org.springframework.web.bind.annotation.RestController;
import dev.oskar_lab.backend.core.AbstractController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;



@RestController
@RequestMapping("/projects")
public class ProjectsController extends AbstractController{
    
    @GetMapping
    public String list(@RequestParam String param) {
        return new String();
    }
    
}
