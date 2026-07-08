package app.besoft.medley.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * Marks a server-side handler for a client island. The {@code value} is the island name that a
 * template references via {@code <medley-island name="...">} and that the client registers with
 * {@code window.medley.registerIsland(name, Class)}.
 *
 * <p>An island handler is <em>stateless</em> — the interactive state lives in the browser island.
 * Its {@code @IslandAction} methods receive the owning {@code Component} (server state bounded to
 * the session) and the committed payload, and typically persist a coarse-grained result by
 * mutating the owner's {@code @State}. It is therefore a plain singleton bean (unlike
 * {@code @MedleyRoute} components, which are prototype).</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface MedleyIsland {
    String value();
}
