package app.besoft.medley.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A method on a {@link MedleyIsland} handler that the client can invoke by committing state over
 * the WebSocket ({@code island.commit(action, payload)}).
 *
 * <p>Parameters are bound at dispatch: any parameter assignable to
 * {@code app.besoft.medley.core.component.Component} receives the owning component; the remaining
 * parameters are bound from the JSON payload by name (requires {@code -parameters}), or — when
 * there is a single non-owner parameter — the whole payload object is bound to it.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IslandAction {
    /** Action name as sent by the client; defaults to the method name. */
    String value() default "";
}
