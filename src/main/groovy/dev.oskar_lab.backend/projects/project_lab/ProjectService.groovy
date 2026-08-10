package dev.oskar_lab.backend.projects.project_lab

import dev.oskar_lab.backend.core.AbstractService
import org.springframework.stereotype.Service;

@Service
class ProjectService extends AbstractService<ProjectEntity> {

    ProjectService(ProjectRepository repo){
        super(repo)
    }

}
