package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.besoft.medley.spring.fixtures.IslandHostComponent;
import app.besoft.medley.spring.fixtures.TickerIsland;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Scanning + dispatch + payload binding for {@code @MedleyIsland}/{@code @IslandAction}. */
class IslandRegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private IslandRegistry registryWithTicker(AnnotationConfigApplicationContext ctx) {
        ctx.register(TickerIsland.class);
        ctx.refresh();
        return new IslandRegistry(ctx.getBeanFactory(), mapper);
    }

    @Test
    void injectsOwnerAndBindsScalarPayloadByParameterName() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            IslandRegistry registry = registryWithTicker(ctx);
            assertThat(registry.contains("ticker")).isTrue();

            IslandHostComponent owner = new IslandHostComponent();
            registry.invoke("ticker", "set", owner, mapper.createObjectNode().put("value", 42));

            assertThat(owner.getValue()).isEqualTo(42);
        }
    }

    @Test
    void bindsWholePayloadObjectToSingleNonOwnerParam() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            IslandRegistry registry = registryWithTicker(ctx);

            IslandHostComponent owner = new IslandHostComponent(); // starts at 0
            registry.invoke("ticker", "adjust", owner, mapper.createObjectNode().put("by", 3));

            assertThat(owner.getValue()).isEqualTo(3);
        }
    }

    @Test
    void unknownIslandOrActionThrows() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            IslandRegistry registry = registryWithTicker(ctx);
            IslandHostComponent owner = new IslandHostComponent();

            assertThatThrownBy(() -> registry.invoke("nope", "set", owner, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nope");
            assertThatThrownBy(() -> registry.invoke("ticker", "nope", owner, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nope");
        }
    }
}
