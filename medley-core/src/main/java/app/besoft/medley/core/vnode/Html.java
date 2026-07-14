package app.besoft.medley.core.vnode;

import java.util.Set;

/**
 * HTML facts the renderer and the serializer must agree on. Kept in one place because they encode the
 * same invariant from two sides: which text can be given an addressable host element, and which cannot.
 */
public final class Html {

    /** The element that hosts dynamic text so a patch can address it (see {@code HtmlSerializer}). */
    public static final String TEXT_MARKER_TAG = "medley-text";

    /**
     * Elements whose content model is raw text: an element child — such as the text marker — is illegal
     * inside them and a browser would not parse it as markup. Their dynamic text is therefore merged into
     * a single text node carrying the <em>element's own</em> id, so a text patch addresses the element and
     * the client sets its {@code textContent}.
     */
    public static final Set<String> RAW_TEXT_TAGS =
            Set.of("textarea", "title", "option", "script", "style");

    private Html() {}

    /** Null-safe: the root of a serialized fragment has no parent tag. */
    public static boolean isRawText(String tag) {
        return tag != null && RAW_TEXT_TAGS.contains(tag);
    }
}
