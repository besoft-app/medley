package app.besoft.medley.spring.fixtures;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for starter tests. Component-scans only this {@code fixtures}
 * package (so exactly one routed component, {@link TestCounterComponent}, is discovered) and
 * pulls in {@code MedleyAutoConfiguration} via {@code @EnableAutoConfiguration}.
 */
@SpringBootApplication
public class TestApplication {
}
