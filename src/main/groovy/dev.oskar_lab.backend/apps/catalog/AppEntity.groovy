package dev.oskar_lab.backend.apps.catalog;

import dev.oskar_lab.backend.core.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "apps")
class AppEntity extends AbstractEntity{

    @Column(nullable = false, length = 64)
    String path;
    
    @Column(nullable = true, length = 128)
    String title;
    
    @Column(nullable = true, length = 2048)
    String description;

}
