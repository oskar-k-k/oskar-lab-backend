package dev.oskar_lab.backend.core

class PaginatedResponse<T> {
    Integer page
    Integer maxPage
    List<AbstractEntity> items
}
