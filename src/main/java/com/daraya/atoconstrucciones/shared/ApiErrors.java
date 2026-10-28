package com.daraya.atoconstrucciones.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import java.net.URI;
import java.util.UUID;

@RestControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class ApiErrors {
    public static ProblemDetail problem(int status, String code, String detail, String path) {
        var problem = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(status), detail);
        problem.setType(URI.create("urn:ato:problem:" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(status >= 500 ? "Error del servicio" : "Solicitud no completada");
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        problem.setProperty("traceId", UUID.randomUUID().toString());
        return problem;
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> domain(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(problem(exception.status(), exception.code(), exception.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(HttpServletRequest request) {
        return ResponseEntity.unprocessableContent().body(problem(422, "VALIDATION_FAILED", "Revise los campos de la solicitud.", request.getRequestURI()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingRequestHeaderException.class})
    ResponseEntity<ProblemDetail> malformed(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(problem(400, "MALFORMED_REQUEST", "Formato de solicitud inválido.", request.getRequestURI()));
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> missing(HttpServletRequest request) {
        return ResponseEntity.status(404).body(problem(404, "NOT_FOUND", "Recurso no disponible.", request.getRequestURI()));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(HttpServletRequest request) {
        return ResponseEntity.status(409).body(problem(409, "DATA_CONFLICT", "El registro entra en conflicto con otro dato existente.", request.getRequestURI()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(HttpServletRequest request) {
        // Sin mensajes JDBC, tokens ni payloads en la respuesta.
        return ResponseEntity.internalServerError().body(problem(500, "INTERNAL_ERROR", "El servicio no pudo completar la solicitud.", request.getRequestURI()));
    }
}
