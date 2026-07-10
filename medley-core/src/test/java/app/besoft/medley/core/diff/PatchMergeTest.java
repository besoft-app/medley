package app.besoft.medley.core.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The 4b.3a cascade merge: append child patches, but suppress ones already covered by a
 *  structural (Insert/Replace) parent patch so they are not double-applied. */
class PatchMergeTest {

    @Test
    void emptyCascadeReturnsParentUnchanged() {
        List<Patch> parent = List.of(new Patch.SetText("root.0.1", "5"));
        List<Patch> merged = PatchMerge.mergeWithSuppression(parent, List.of());
        assertSame(parent, merged, "no cascade -> return the same parent list untouched");
    }

    @Test
    void noStructuralParentPatchAppendsCascadeUntouched() {
        List<Patch> parent = List.of(new Patch.SetText("root.0.1", "5"));
        List<Patch> cascade = List.of(new Patch.SetText("root.1::echo-param.0", "b"));
        List<Patch> merged = PatchMerge.mergeWithSuppression(parent, cascade);
        assertEquals(2, merged.size());
        assertEquals("root.0.1", merged.get(0).id());
        assertEquals("root.1::echo-param.0", merged.get(1).id());
    }

    @Test
    void parentReplaceSuppressesCascadeUnderThatBoundary() {
        // Host newly materialized (placeholder -> host) via Replace: the child subtree is already in
        // the Replace HTML, so the cascaded child patch must be dropped.
        List<Patch> parent = List.of(new Patch.Replace("root.1", "<medley-component ...>"));
        List<Patch> cascade = List.of(new Patch.SetText("root.1::echo-param.0", "b"));
        List<Patch> merged = PatchMerge.mergeWithSuppression(parent, cascade);
        assertEquals(1, merged.size());
        assertTrue(merged.get(0) instanceof Patch.Replace);
    }

    @Test
    void parentInsertSuppressesCascadeUnderNewKeyedItem() {
        List<Patch> parent = List.of(new Patch.Insert("root.1[k]", "root.1", 0, "<medley-component ...>"));
        List<Patch> cascade = List.of(new Patch.SetText("root.1[k]::counter-card.0", "1"));
        List<Patch> merged = PatchMerge.mergeWithSuppression(parent, cascade);
        assertEquals(1, merged.size());
        assertTrue(merged.get(0) instanceof Patch.Insert);
    }

    @Test
    void unrelatedStructuralSiblingDoesNotSuppress() {
        List<Patch> parent = List.of(new Patch.Replace("root.2", "<div>...</div>"));
        List<Patch> cascade = List.of(new Patch.SetText("root.1::echo-param.0", "b"));
        List<Patch> merged = PatchMerge.mergeWithSuppression(parent, cascade);
        assertEquals(2, merged.size());
        assertEquals("root.1::echo-param.0", merged.get(1).id());
    }

    @Test
    void numericPrefixCollisionDoesNotSuppress() {
        // Replace("root.1") must NOT suppress a cascade under "root.10::..." (bare startsWith trap).
        List<Patch> parent = List.of(new Patch.Replace("root.1", "<div>...</div>"));
        List<Patch> cascade = List.of(new Patch.SetText("root.10::echo-param.0", "b"));
        List<Patch> merged = PatchMerge.mergeWithSuppression(parent, cascade);
        assertEquals(2, merged.size());
    }
}
