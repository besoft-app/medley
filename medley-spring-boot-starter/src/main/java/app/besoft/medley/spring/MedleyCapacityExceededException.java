package app.besoft.medley.spring;

/**
 * Thrown when mounting a component would exceed the per-session component cap
 * ({@code medley.session.max-components}) — a runaway/DoS guard (Stage 4, increment 4b.3b).
 *
 * <p>Raised from {@code MedleySession} during a render; the WebSocket handler catches it like any
 * failed action, logs it, and sends a generic error while keeping the socket open.</p>
 */
public class MedleyCapacityExceededException extends RuntimeException {

    public MedleyCapacityExceededException(String id, int limit) {
        super("Session component cap exceeded (medley.session.max-components=" + limit
                + ") while mounting '" + id + "'");
    }
}
