package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Test fixture: a component whose {@code @State} is deliberately hostile to a JSON snapshot (Stage 5,
 * increment 2). {@code @State} holds whatever the application put there, so the dev-tools inspector must
 * degrade each awkward field to a label instead of failing the request:
 *
 * <ul>
 *   <li>{@link #selfReferencing} — a list containing itself. Jackson recurses until the stack blows: a
 *       {@code StackOverflowError} (an {@link Error}, not a {@code RuntimeException}), which is exactly
 *       the case a naive {@code catch (RuntimeException)} would let through as a 500;</li>
 *   <li>{@link #exploding} — a value whose getter throws while being serialized;</li>
 *   <li>{@link #huge} — a collection far past the inline limit, which must be elided <em>without</em>
 *       being serialized first;</li>
 *   <li>{@link #ok} — an ordinary field, which must still come through as a real JSON value.</li>
 * </ul>
 *
 * <p>Not routed: a test mounts it directly. Template: {@code templates/medley/devtools-probe.html}.</p>
 */
@MedleyComponent("devtools-probe")
public class DevToolsProbeComponent extends Component {

    /** A value that blows up during serialization (its getter throws). */
    public static class Exploding {
        public String getBoom() {
            throw new IllegalStateException("this getter always throws");
        }
    }

    @State List<Object> selfReferencing = new ArrayList<>();
    @State Exploding exploding = new Exploding();
    @State List<Integer> huge = new ArrayList<>(IntStream.range(0, 500).boxed().toList());
    @State int ok = 42;

    public DevToolsProbeComponent() {
        selfReferencing.add(selfReferencing);
    }
}
