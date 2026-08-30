package com.jjetta.task_queue.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private record FieldErrorDetail(String field, String message) {}

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handleTaskNotFoundException(TaskNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        problem.setTitle("Task Not Found");
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    @ExceptionHandler({
            NullPointerException.class,
            IllegalArgumentException.class
    })
    public ProblemDetail handleBadRequestException(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );

        problem.setTitle("Bad Request");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("exceptionType", ex.getClass().getSimpleName());

        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getFieldErrors();
        List<FieldErrorDetail> errorDetails = fieldErrors.stream()
                .map((FieldError fe) -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Method argument(s) invalid"
        );

        problem.setTitle("Bad Request");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errors", errorDetails);

        return problem;
    }

    @ExceptionHandler(InvalidTaskClaimTokenException.class)
    public ProblemDetail handleInvalidTaskReportTokenException(InvalidTaskClaimTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );

        problem.setTitle("Task Claim Token Invalid");
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    @ExceptionHandler(TaskNotRunningException.class)
    public ProblemDetail handleTaskNotRunningException(TaskNotRunningException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );

        problem.setTitle("Task Not Running");
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }
}
