package dev.oskar_lab.backend.projects.project_lab

import groovy.transform.CompileStatic
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@CompileStatic
@Repository
interface ProjectRepository extends JpaRepository<ProjectEntity, Long>{
}
