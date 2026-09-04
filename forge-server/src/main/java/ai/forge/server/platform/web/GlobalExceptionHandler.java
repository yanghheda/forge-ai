package ai.forge.server.platform.web;

import ai.forge.server.auth.domain.InstanceAlreadyInitializedException;
import ai.forge.server.auth.domain.InvalidCredentialsException;
import ai.forge.server.auth.domain.LoginRateLimitedException;
import ai.forge.server.auth.domain.UnauthenticatedException;
import ai.forge.server.auth.domain.WeakPasswordException;
import ai.forge.server.agent.domain.AgentRunIdempotencyConflictException;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.project.domain.ProjectKeyConflictException;
import ai.forge.server.workspace.domain.MemberEmailConflictException;
import ai.forge.server.workitem.domain.IdempotencyConflictException;
import ai.forge.server.workitem.domain.InvalidTransitionException;
import ai.forge.server.workitem.domain.RelationConflictException;
import ai.forge.server.workitem.domain.WorkflowGuardFailedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /* 仅在服务端记录未知异常堆栈，避免把内部实现泄露给调用方。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "Resource not found",
                Map.of(),
                request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleScopedResourceNotFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Resource not found", Map.of(), request);
    }

    @ExceptionHandler(ProjectKeyConflictException.class)
    public ResponseEntity<ApiError> handleProjectKeyConflict(
            ProjectKeyConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.PROJECT_KEY_CONFLICT,
                "Project key already exists in this workspace", Map.of(), request);
    }

    @ExceptionHandler(MemberEmailConflictException.class)
    public ResponseEntity<ApiError> handleMemberEmailConflict(
            MemberEmailConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.MEMBER_EMAIL_CONFLICT,
                "A user account already exists for this email", Map.of(), request);
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ApiError> handleVersionConflict(
            VersionConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT,
                "Resource version conflict", Map.of(), request);
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(
            InvalidTransitionException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.INVALID_TRANSITION,
                "Workflow action is not available from the current state", Map.of(), request);
    }

    @ExceptionHandler(WorkflowGuardFailedException.class)
    public ResponseEntity<ApiError> handleWorkflowGuardFailed(
            WorkflowGuardFailedException exception, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.WORKFLOW_GUARD_FAILED,
                "Workflow requirements are not satisfied", Map.of("missing", exception.missing()), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(
            IdempotencyConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency key was already used for another action", Map.of(), request);
    }

    @ExceptionHandler(AgentRunIdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleAgentRunIdempotencyConflict(
            AgentRunIdempotencyConflictException exception, HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                ErrorCode.AGENT_RUN_IDEMPOTENCY_CONFLICT,
                "Agent Run clientRequestId was reused with different input",
                Map.of(),
                request);
    }

    @ExceptionHandler(RelationConflictException.class)
    public ResponseEntity<ApiError> handleRelationConflict(
            RelationConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.RELATION_CONFLICT,
                "Work item relation already exists", Map.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                details,
                request);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> handleMethodValidation(Exception exception, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                Map.of(),
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                Map.of(),
                request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                Map.of(),
                request);
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ApiError> handleWeakPassword(
            WeakPasswordException exception, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                Map.of("password", "must contain letters and digits, use 12 or more characters, and fit within 72 UTF-8 bytes"),
                request);
    }

    @ExceptionHandler(InstanceAlreadyInitializedException.class)
    public ResponseEntity<ApiError> handleAlreadyInitialized(
            InstanceAlreadyInitializedException exception, HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                ErrorCode.INSTANCE_ALREADY_INITIALIZED,
                "Instance has already been initialized",
                Map.of(),
                request);
    }

    @ExceptionHandler({InvalidCredentialsException.class, UnauthenticatedException.class})
    public ResponseEntity<ApiError> handleUnauthenticated(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                exception instanceof InvalidCredentialsException ? "Invalid email or password" : "Authentication required",
                Map.of(), request);
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ApiError> handleLoginRateLimited(
            LoginRateLimitedException exception, HttpServletRequest request) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.LOGIN_RATE_LIMITED,
                "Too many login attempts", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled request failure", exception);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Unexpected server error",
                Map.of(),
                request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            ErrorCode code,
            String message,
            Map<String, Object> details,
            HttpServletRequest request) {
        Object requestIdAttribute = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        String requestId = requestIdAttribute instanceof String value ? value : "req_unavailable";
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(code, message, requestId, details));
    }
}
