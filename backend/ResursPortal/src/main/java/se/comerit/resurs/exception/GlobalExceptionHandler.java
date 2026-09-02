package se.comerit.resurs.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ProblemDetail;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI PROBLEM_TYPE_DEFAULT = URI.create("about:blank");

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationError(InvalidCredentialsException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, e.getMessage());
        problemDetail.setTitle("Unauthorized");
        problemDetail.setType(PROBLEM_TYPE_DEFAULT);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setType(PROBLEM_TYPE_DEFAULT);
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleApplicationNotFound(ApplicationNotFoundException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problemDetail.setTitle("Application Not Found");
        problemDetail.setType(PROBLEM_TYPE_DEFAULT);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(EmptyFileException.class)
    public ResponseEntity<ProblemDetail> handleEmptyFile(EmptyFileException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problemDetail.setTitle("Empty File");
        problemDetail.setType(PROBLEM_TYPE_DEFAULT);
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ProblemDetail> handleFileUploadError(FileUploadException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        problemDetail.setTitle("File Upload Error");
        problemDetail.setType(PROBLEM_TYPE_DEFAULT);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }



    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralError(Exception e) {
        System.err.println("Unexpected exception " + e);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(PROBLEM_TYPE_DEFAULT);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
