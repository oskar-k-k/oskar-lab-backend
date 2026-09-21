package dev.oskar_lab.backend.apps.catalog

import dev.oskar_lab.backend.core.AbstractService
import org.springframework.stereotype.Service;

@Service
class AppService extends AbstractService<AppEntity> {

    AppService(AppRepository repo){
        super(repo)
    }

}
