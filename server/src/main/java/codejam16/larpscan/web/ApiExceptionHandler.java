package codejam16.larpscan.web;


import codejam16.larpscan.service.BuildingNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates request validation and deserialization failures into {@code 400 Bad Request} responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  /**
   * Handles Bean Validation failures on request DTOs.
   *
   * @param ex validation exception
   * @return {@code 400 Bad Request} problem detail with per-field errors
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidationError(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Request validation failed"
    );
    problem.setProperty("errors", fieldErrors);
    return problem;
  }

  /**
   * Handles malformed JSON or unparseable scalar types (such as invalid UUIDs or timestamps).
   *
   * @param ex message readability exception
   * @return {@code 400 Bad Request} problem detail
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Malformed JSON request body"
    );
  }

  /**
   * Handles requests referencing an unknown Building identifier.
   *
   * @param ex building-not-found exception
   * @return {@code 404 Not Found} problem detail
   */
  @ExceptionHandler(BuildingNotFoundException.class)
  public ProblemDetail handleBuildingNotFound(
      codejam16.larpscan.service.BuildingNotFoundException ex
  ) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

}
