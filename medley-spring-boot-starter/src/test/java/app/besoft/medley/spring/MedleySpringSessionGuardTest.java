package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.session.Session;

/**
 * Stage 5, increment 4 — the Spring Session interop guard is <em>conditional</em>. It exists only when
 * Spring Session is on the classpath, and it only ever warns: an app that pairs Medley with a serializing
 * SessionRepository would otherwise crash with a NotSerializableException far from its cause.
 *
 * <p>Spring Session is a test-only dependency here — Medley must never depend on it. That the "absent"
 * case below passes with a {@link FilteredClassLoader} is what proves the guard costs a consumer nothing.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class MedleySpringSessionGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MedleySpringSessionGuard.class));

    @Test
    void guardPresentWhenSpringSessionIsOnTheClasspath() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(MedleySpringSessionGuard.class));
    }

    @Test
    void guardAbsentWithoutSpringSession() {
        // The common case: an app that never heard of Spring Session pays nothing and sees no warning.
        runner.withClassLoader(new FilteredClassLoader(Session.class)).run(ctx -> {
            assertThat(ctx).hasNotFailed(); // @ConditionalOnClass(name=...) skips without loading the class
            assertThat(ctx).doesNotHaveBean(MedleySpringSessionGuard.class);
        });
    }

    @Test
    void guardWarnsButNeverFailsStartup(CapturedOutput output) {
        // The decision this locks in: a WARN, not a fail-fast. Spring Session on the classpath is not proof
        // the app routes Medley through a serializing store, so refusing to boot would be a false positive.
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            // The bean existing is not the deliverable — the message a developer actually reads is. Assert
            // it names the symptom they will hit and the way out, not just that something was logged.
            assertThat(output).contains("WARN")
                    .contains("Spring Session is on the classpath")
                    .contains("NotSerializableException")
                    .contains("sticky sessions");
        });
    }

    @Test
    void guardStaysQuietWhenSpringSessionIsAbsent(CapturedOutput output) {
        runner.withClassLoader(new FilteredClassLoader(Session.class))
                .run(ctx -> assertThat(output).doesNotContain("Spring Session is on the classpath"));
    }

    /**
     * Locks in the decision this whole increment rests on, the way 5.3 locked in compression: the guard
     * warns about a contract, so the contract itself needs a test or the docs rot silently.
     *
     * <p>If you are here because this test failed, you are about to make {@code MedleySession}
     * {@code Serializable}. Read MEDLEY_DESIGN.md §10.12 first: the session is an identity map of live
     * components, so serializing it is not merely hard — a store that returns a <em>copy</em> breaks child
     * {@code @State} (4b.2), the cascade (4b.3a) and callbacks even if serialization "succeeds". Making
     * this compile would make §9.1 quietly false.</p>
     */
    @Test
    void medleySessionIsDeliberatelyNotSerializable() {
        assertThat(java.io.Serializable.class.isAssignableFrom(MedleySession.class))
                .as("MedleySession must NOT be Serializable — it is an identity map of live components "
                        + "(see MEDLEY_DESIGN.md §10.12 and this test's javadoc)")
                .isFalse();
    }
}
