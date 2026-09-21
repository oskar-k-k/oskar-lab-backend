package dev.oskar_lab.backend.apps.catalog

import groovy.transform.CompileStatic
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@CompileStatic
@Repository
interface AppRepository extends JpaRepository<AppEntity, Long>{
}
