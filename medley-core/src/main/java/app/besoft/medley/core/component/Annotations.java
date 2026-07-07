package app.besoft.medley.core.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Annotations that describe a Medley component. */
public final class Annotations {

    private Annotations() {}

    /**
     * Marks a class as a Medley component and gives it a name. The name is used both as the
     * default template file name ({@code templates/medley/<name>.html}) and as the custom
     * element/registry key.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface MedleyComponent {
        String value();
    }

    /** A piece of reactive state. Mutating it marks the component dirty for re-render. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface State {}

    /** An input passed from a parent component. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Param {}

    /** A method callable from the template via {@code @event="methodName"}. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Action {}
}
