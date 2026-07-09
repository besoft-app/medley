package app.besoft.medley.spring.fixtures;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for starter tests. Component-scans only this {@code fixtures}
 * package (routed components {@link TestCounterComponent} /counter, {@link IslandHostComponent}
 * /island, {@link TableHostComponent} /table, plus the {@link TickerIsland} handler) and pulls in
 * {@code MedleyAutoConfiguration} via {@code @EnableAutoConfiguration}.
 */
@SpringBootApplication
public class TestApplication {
}
