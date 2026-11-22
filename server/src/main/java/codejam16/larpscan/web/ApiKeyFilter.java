package codejam16.larpscan.web;

import codejam16.larpscan.config.LarpProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces the shared {@code X-API-Key} header on {@code POST /api/batches} (RQ-11) while leaving
 * public {@code GET} endpoints and {@code OPTIONS} preflight requests unauthenticated.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

  public static final String API_KEY_HEADER = "X-API-Key";
  private static final String BATCHES_PATH = "/api/batches";

  private final LarpProperties properties;
  private final ObjectMapper objectMapper;

  /**
   * Creates an API-key filter backed by configured LARP properties and JSON serializer.
   *
   * @param properties validated {@code larp.*} configuration properties
   * @param objectMapper Jackson serializer used to render {@link ProblemDetail} responses
   */
  public ApiKeyFilter(LarpProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    if (!isProtectedBatchPost(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    if (!hasValidApiKey(request.getHeader(API_KEY_HEADER))) {
      writeUnauthorizedResponse(request, response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean isProtectedBatchPost(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String path = resolveApplicationPath(request);
    return BATCHES_PATH.equals(path) || (BATCHES_PATH + "/").equals(path);
  }

  private String resolveApplicationPath(HttpServletRequest request) {
    String contextPath = request.getContextPath();
    String requestUri = request.getRequestURI();
    if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
      return requestUri.substring(contextPath.length());
    }
    return requestUri;
  }

  private boolean hasValidApiKey(String providedKey) {
    String expectedKey = properties.apiKey();
    if (providedKey == null || providedKey.isBlank()
        || expectedKey == null || expectedKey.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        providedKey.getBytes(StandardCharsets.UTF_8),
        expectedKey.getBytes(StandardCharsets.UTF_8)
    );
  }

  private void writeUnauthorizedResponse(
      HttpServletRequest request,
      HttpServletResponse response
  ) throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.UNAUTHORIZED,
        "Missing or invalid API key"
    );
    problem.setInstance(URI.create(request.getRequestURI()));

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), problem);
  }
}