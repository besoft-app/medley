package app.besoft.medley.core.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Separator-aware id containment used by the 4b.3a cascade merge to suppress double-applied patches. */
class IdPathsTest {

    @Test
    void exactMatchIsSelf() {
        assertTrue(IdPaths.isSelfOrDescendant("root.1", "root.1"));
    }

    @Test
    void positionalChildIsDescendant() {
        assertTrue(IdPaths.isSelfOrDescendant("root.1", "root.1.2"));
    }

    @Test
    void componentBoundaryIsDescendant() {
        assertTrue(IdPaths.isSelfOrDescendant("root.1", "root.1::counter-card"));
        assertTrue(IdPaths.isSelfOrDescendant("root.1", "root.1::counter-card.0.1"));
    }

    @Test
    void keyedLoopItemIsDescendant() {
        assertTrue(IdPaths.isSelfOrDescendant("root.1", "root.1[k]"));
        assertTrue(IdPaths.isSelfOrDescendant("root.1[k]", "root.1[k]::counter-card.0"));
    }

    @Test
    void numericPrefixNeighbourIsNotDescendant() {
        // The trap a bare startsWith would fall into.
        assertFalse(IdPaths.isSelfOrDescendant("root.1", "root.10"));
        assertFalse(IdPaths.isSelfOrDescendant("root.1", "root.12.3"));
    }

    @Test
    void unrelatedIdIsNotDescendant() {
        assertFalse(IdPaths.isSelfOrDescendant("root.1", "root.2"));
        assertFalse(IdPaths.isSelfOrDescendant("root.1", "root"));
    }

    @Test
    void nullsAreNotDescendant() {
        assertFalse(IdPaths.isSelfOrDescendant(null, "root.1"));
        assertFalse(IdPaths.isSelfOrDescendant("root.1", null));
    }

    // --- ownerComponentId: recover the owning component instance id from a nested child id
    //     (childId = hostSlot + "::" + name). Used to route a child→parent callback to its owner.

    @Test
    void ownerOfRootChildIsRoot() {
        assertEquals("root", IdPaths.ownerComponentId("root.0::counter"));
        assertEquals("root", IdPaths.ownerComponentId("root.1.2::editor"));
    }

    @Test
    void ownerOfKeyedRootChildIsRoot() {
        assertEquals("root", IdPaths.ownerComponentId("root.0[1]::counter"));
    }

    @Test
    void ownerOfBoundaryAtOwnerRootSlotIsThatOwner() {
        assertEquals("root", IdPaths.ownerComponentId("root::editor"));
    }

    @Test
    void ownerOfNestedChildStripsTheInnerSlot() {
        assertEquals("root.2::editor", IdPaths.ownerComponentId("root.2::editor.1::inner"));
        assertEquals("root.0[1]::card", IdPaths.ownerComponentId("root.0[1]::card.3::badge"));
    }

    @Test
    void ownerOfNonBoundaryIdIsNull() {
        assertNull(IdPaths.ownerComponentId("root"));
        assertNull(IdPaths.ownerComponentId("root.1.2"));
        assertNull(IdPaths.ownerComponentId(null));
    }
}
