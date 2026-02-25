package com.sk.skillsgraph.middleware;

import com.sk.skillsgraph.util.AppExceptions.AppException;
import com.sk.skillsgraph.util.AppExceptions.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception exception, HttpServletRequest request) {
        return buildResponse("Not Found", HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class,
            ConstraintViolationException.class, ValidationException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception, HttpServletRequest request) {
        return buildResponse(exception.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(AppException exception, HttpServletRequest request) {
        return buildResponse(exception.getMessage(), HttpStatus.valueOf(exception.statusCode()), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled application error", exception);
        return buildResponse("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(String message,
                                                              HttpStatus status,
                                                              HttpServletRequest request) {
        String requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) == null
                ? ""
                : request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE).toString();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        body.put("status", status.value());
        body.put("request_id", requestId);
        return ResponseEntity.status(status).body(body);
    }
}
