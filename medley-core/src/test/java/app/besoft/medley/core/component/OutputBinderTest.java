package app.besoft.medley.core.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import app.besoft.medley.core.component.Annotations.Output;
import app.besoft.medley.core.template.TemplateException;
import org.junit.jupiter.api.Test;

/**
 * The child→parent callback core mechanics: {@link OutputBinder} ensures an {@link EventEmitter} on
 * every {@code @Output} field, binds a supplied sink where one exists, and leaves an unbound output a
 * no-op. Mirrors {@code ParamBinder} in the event-up direction.
 */
class OutputBinderTest {

    static class Child {
        @Output EventEmitter save;
        @Output EventEmitter cancel; // deliberately left unbound
    }

    @Test
    void injectCreatesEmittersAndBindsSink() {
        Child c = new Child();
        List<Object> received = new ArrayList<>();
        OutputBinder.inject(c, Map.of("save", received::add));

        assertNotNull(c.save, "an emitter is created for every @Output field");
        assertNotNull(c.cancel, "including outputs the parent did not bind");
        c.save.emit("hello");
        assertEquals(List.of("hello"), received);
    }

    @Test
    void unboundOutputIsNoOp() {
        Child c = new Child();
        OutputBinder.inject(c, Map.of()); // nothing bound
        assertDoesNotThrow(() -> c.save.emit("x"), "an unbound @Output must be a safe no-op, not an NPE");
    }

    @Test
    void sinkForUnknownOutputNameIsIgnored() {
        Child c = new Child();
        assertDoesNotThrow(() -> OutputBinder.inject(c, Map.of("nope", o -> {})));
        assertNotNull(c.save);
    }

    @Test
    void emitWithoutPayloadPassesNull() {
        Child c = new Child();
        boolean[] fired = {false};
        OutputBinder.inject(c, Map.of("save", p -> {
            fired[0] = true;
            assertNull(p, "emit() with no argument passes null to the sink");
        }));
        c.save.emit();
        assertTrue(fired[0]);
    }

    @Test
    void preExistingEmitterInstanceIsReused() {
        Child c = new Child();
        EventEmitter pre = new EventEmitter();
        c.save = pre;
        OutputBinder.inject(c, Map.of());
        assertSame(pre, c.save, "an already-set emitter field is not replaced");
    }

    static class WrongType {
        @Output String notAnEmitter;
    }

    @Test
    void outputOnWrongTypeThrows() {
        assertThrows(TemplateException.class, () -> OutputBinder.inject(new WrongType(), Map.of()));
    }
}
