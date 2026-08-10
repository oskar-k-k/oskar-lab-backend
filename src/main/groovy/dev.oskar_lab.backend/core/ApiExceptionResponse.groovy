package dev.oskar_lab.backend.core

import java.time.LocalDateTime

class ApiExceptionResponse {
    Integer status
    String error
    String message
    String path
    LocalDateTime timestamp = LocalDateTime.now()
}