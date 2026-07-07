package app.besoft.medley.core.diff;

/**
 * A single DOM mutation produced by the differ and applied by {@code medley.js}.
 *
 * <p>Patches are addressed by the target node's stable {@code medley-id}. The set is kept
 * intentionally small; richer operations can be composed from these.</p>
 */
public sealed interface Patch
        permits Patch.SetText, Patch.SetAttr, Patch.RemoveAttr,
                Patch.SetEvent, Patch.RemoveEvent, Patch.Replace,
                Patch.Insert, Patch.Remove {

    String id();

    /** Change the text content of a text node. */
    record SetText(String id, String value) implements Patch {}

    /** Set (or add) an attribute on an element. */
    record SetAttr(String id, String name, String value) implements Patch {}

    /** Remove an attribute from an element. */
    record RemoveAttr(String id, String name) implements Patch {}

    /** Wire (or rewire) a DOM event to a server action. */
    record SetEvent(String id, String event, String action) implements Patch {}

    /** Unwire a DOM event. */
    record RemoveEvent(String id, String event) implements Patch {}

    /** Replace a node entirely (tag changed, or text<->element). Carries pre-rendered HTML. */
    record Replace(String id, String html) implements Patch {}

    /** Insert a new child under {@code parentId} at {@code index}. Carries pre-rendered HTML. */
    record Insert(String id, String parentId, int index, String html) implements Patch {}

    /** Remove a node from the DOM. */
    record Remove(String id) implements Patch {}
}
