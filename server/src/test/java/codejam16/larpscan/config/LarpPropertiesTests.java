package codejam16.larpscan.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Checks the cross-field strata constraints on {@link LarpProperties}.
 */
class LarpPropertiesTests {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsDefaults() {
    assertTrue(violations(3.0, -65, 8.0, -80, 0.8).isEmpty());
  }

  @Test
  void rejectsFarDiscNotBeyondCloseDisc() {
    assertEquals(Set.of("farDiscBeyondCloseDisc"), violations(8.0, -65, 8.0, -80, 0.8));
  }

  @Test
  void rejectsEmptyFarBand() {
    assertEquals(Set.of("closeRssiAboveFarRssi"), violations(3.0, -80, 8.0, -80, 0.8));
  }

  @Test
  void rejectsDownscaleFactorOutsideZeroToOne() {
    assertEquals(Set.of("farDownscaleFactor"), violations(3.0, -65, 8.0, -80, 1.5));
    assertEquals(Set.of("farDownscaleFactor"), violations(3.0, -65, 8.0, -80, 0));
  }

  private Set<String> violations(
      double closeD, int closeRssi, double farD, int farRssi, double factor) {
    LarpProperties props = new LarpProperties("key", new LarpProperties.Cors("http://x"),
        120, 2.5, 60000, 5, closeD, closeRssi, farD, farRssi, factor, -40, 3.0);
    return validator.validate(props).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }
}
