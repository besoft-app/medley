package app.besoft.medley.spring.fixtures;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for starter tests. Component-scans only this {@code fixtures}
 * package (routed components {@link TestCounterComponent} /counter, {@link IslandHostComponent}
 * /island, {@link TableHostComponent} /table, {@link SearchComponent} /search,
 * {@link SignupComponent} /signup, {@link NestedHostComponent} /nested with its
 * {@link CounterBadgeComponent} child, {@link CardHostComponent} /cards with its stateful
 * {@link CounterCardComponent} child, {@link ParamCascadeHostComponent} /cascade with its
 * {@link EchoParamComponent} child, {@link ToggleChildHostComponent} /toggle-child (eviction),
 * {@link CardListHostComponent} /card-list (keyed *for of components),
 * plus the {@link TickerIsland} handler) and pulls in
 * {@code MedleyAutoConfiguration} via {@code @EnableAutoConfiguration}.
 */
@SpringBootApplication
public class TestApplication {
}
