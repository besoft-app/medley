package app.besoft.medley.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Interop guard for Spring Session (Stage 5, increment 4). Warns when Spring Session is on the
 * classpath, because a Medley session cannot survive a serializing {@code SessionRepository}.
 *
 * <p>{@link MedleyController} puts a <em>live</em> {@link MedleySession} into the {@code HttpSession}.
 * That object is an identity map of live Spring-managed components: {@code Map<String, Method>} action
 * tables, {@code Consumer} sinks closing over the session, and — via the template registry — the bean
 * factory itself. Nothing in it is {@code Serializable}, and making it so would not help: reusing the
 * <em>same</em> child instance is what keeps a child's {@code @State} alive across a parent re-render
 * (increment 4b.2), so a store that hands back a copy breaks the model even if it serializes cleanly.
 * See MEDLEY_DESIGN.md §10.12 and §9.1.</p>
 *
 * <p>Scale out with <b>sticky sessions</b> instead — the WebSocket pins a user to one instance for the
 * life of the connection regardless, so affinity is the lever, not a shared store.</p>
 *
 * <p><b>Why a warning and not a fail-fast</b> (unlike {@link RouteRegistry}'s singleton check): the
 * presence of the dependency is not proof the application routes Medley through it — Spring Session
 * with an in-memory {@code MapSessionRepository} never serializes, and an app may use Spring Session
 * for something else entirely. A false fail-fast would refuse to boot a working app. Left unwarned,
 * though, the failure surfaces as a {@code NotSerializableException} at the first page load, far from
 * its cause — so the warning is what turns a puzzling crash into a named, actionable one.</p>
 *
 * <p><b>Best-effort, not a complete net.</b> This detects the Spring Session flavour only. A container
 * can serialize session attributes with no Spring Session anywhere — Tomcat's {@code PersistentManager},
 * {@code DeltaManager} clustering, or {@code server.servlet.session.persistent=true} — and that goes
 * unwarned. The rule the guard hints at is the general one: <em>do not put a Medley session in any
 * store that serializes or replicates it.</em></p>
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.session.Session")
public class MedleySpringSessionGuard {

    private static final Logger log = LoggerFactory.getLogger(MedleySpringSessionGuard.class);

    public MedleySpringSessionGuard() {
        log.warn("Spring Session is on the classpath. Medley stores a live, NON-SERIALIZABLE session "
                + "(live component instances, reflective action tables, callback sinks) in the HTTP "
                + "session, so if Medley's HTTP session is backed by a serializing SessionRepository "
                + "(Redis/JDBC/Hazelcast), the first page load will fail with NotSerializableException. "
                + "An in-memory MapSessionRepository is unaffected — ignore this warning in that case. "
                + "A Medley session is server-local by design: scale out with sticky sessions, and let "
                + "reconnect (resync/reload) + onInit() rebuild state from the domain. "
                + "See MEDLEY_DESIGN.md 9.1 and 10.12.");
    }
}
