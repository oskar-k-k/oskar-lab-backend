package dev.oskar_lab.backend.core

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(Exception)
    ResponseEntity<ApiExceptionResponse> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        def response = new ApiExceptionResponse(
                status: HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error: HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
                message: ex.message,
                path: request.requestURI
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}