package dev.oskar_lab.backend.core

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@RestControllerAdvice
class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler)

    @ExceptionHandler(MethodArgumentNotValidException)
    ResponseEntity<ApiExceptionResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.bindingResult.fieldErrors.find()?.defaultMessage ?: "Invalid request"
        def response = createResponse(HttpStatus.BAD_REQUEST, message, request.requestURI)
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(Exception)
    ResponseEntity<ApiExceptionResponse> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception for {}", request.requestURI, ex)
        def response = createResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request.requestURI)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }

    private static ApiExceptionResponse createResponse(HttpStatus status, String message, String path) {
        new ApiExceptionResponse(
                status: status.value(),
                error: status.reasonPhrase,
                message: message,
                path: path
        )
    }
}
