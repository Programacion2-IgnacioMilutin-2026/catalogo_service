package catalogo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Da formato problem+json a los errores del servicio y pone los códigos generales.
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more parameters are invalid");
        body.setProperty("code", "VALIDATION_ERROR");
        body.setProperty("fieldErrors", fieldErrors(ex.getBindingResult().getFieldErrors()));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorItem> errors = parameterErrors(ex.getParameterValidationResults());
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more parameters are invalid");
        body.setProperty("code", "VALIDATION_ERROR");
        body.setProperty("fieldErrors", errors);
        logInvalidParameters(request, errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        body.setProperty("code", "INTERNAL_ERROR");
        return body;
    }

    private static List<FieldErrorItem> fieldErrors(List<FieldError> errors) {
        Map<String, String> byField = new LinkedHashMap<>();
        for (FieldError error : errors) {
            byField.putIfAbsent(error.getField().replaceAll("\\[\\d+]$", ""), messageOf(error));
        }
        return byField.entrySet().stream()
                .map(entry -> new FieldErrorItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static String messageOf(FieldError error) {
        return error.isBindingFailure() ? "invalid value" : error.getDefaultMessage();
    }

    private static List<FieldErrorItem> parameterErrors(List<ParameterValidationResult> results) {
        Map<String, String> byParameter = new LinkedHashMap<>();
        for (ParameterValidationResult result : results) {
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                byParameter.putIfAbsent(result.getMethodParameter().getParameterName(), error.getDefaultMessage());
            }
        }
        return byParameter.entrySet().stream()
                .map(entry -> new FieldErrorItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void logInvalidParameters(WebRequest request, List<FieldErrorItem> errors) {
        Principal user = request.getUserPrincipal();
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        String method = request instanceof ServletWebRequest servletRequest ? servletRequest.getHttpMethod().name() : "?";
        log.info("Invalid parameters for {} {} requested by user {}: {}", method, route,
                user == null ? "anonymous" : user.getName(),
                errors.stream().map(FieldErrorItem::field).collect(Collectors.joining(", ")));
    }

    record FieldErrorItem(String field, String message) {
    }
}
