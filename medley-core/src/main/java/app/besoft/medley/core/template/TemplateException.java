package app.besoft.medley.core.template;

/** Thrown when a template cannot be parsed or an expression cannot be evaluated. */
public class TemplateException extends RuntimeException {
    public TemplateException(String message) { super(message); }
    public TemplateException(String message, Throwable cause) { super(message, cause); }
}
