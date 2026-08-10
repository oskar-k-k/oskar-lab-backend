package dev.oskar_lab.backend.core

import groovy.transform.CompileStatic;
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional


@CompileStatic
@Transactional(readOnly = true)
abstract class AbstractService<T> {

    protected final JpaRepository<T, Long> repository

    protected AbstractService(JpaRepository<T, Long> repository) {
        this.repository = repository
    }

    T get(Long id) {
        repository.findById(id).orElse(null)
    }

    List<T> getAll() {
        repository.findAll()
    }

    Page<T> getAll(ListRequest listRequest) {
        repository.findAll(PageRequest.of(listRequest.page, listRequest.pageSize))
    }

    boolean exists(Long id) {
        repository.existsById(id)
    }

    long count() {
        repository.count()
    }

    @Transactional
    T save(T entity) {
        repository.save(entity)
    }

    @Transactional
    List<T> saveAll(Collection<T> entities) {
        repository.saveAll(entities)
    }

    @Transactional
    void delete(T entity) {
        repository.delete(entity)
    }

    @Transactional
    void delete(Long id) {
        repository.deleteById(id)
    }

}
