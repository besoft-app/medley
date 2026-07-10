package app.besoft.medley.core.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Merges a parent component's patches with the patches cascaded from nested children whose
 * {@code @Param}s changed during the same render (Stage 4, increment 4b.3a).
 *
 * <p>An opaque {@code <medley-component>} boundary keeps the parent diff from ever emitting a child's
 * subtree, so the child's own patches can simply be appended. The one exception is <em>structural
 * (re)creation</em>: if the parent diff already emits an {@link Patch.Insert}/{@link Patch.Replace}
 * that covers the child's host (a boundary newly appearing via {@code *if} placeholder→host, or a new
 * keyed list item), the child's fresh subtree is <em>already</em> baked into that pre-rendered HTML.
 * Re-applying the cascaded child patches on top would double-apply against a node the Insert/Replace
 * just created. This merge therefore drops any cascaded patch addressed at (or beneath) such a
 * structural target id.</p>
 */
public final class PatchMerge {

    private PatchMerge() {}

    /**
     * @param parentPatches the owner's own diff (order preserved, returned first)
     * @param cascade       patches produced by re-rendered children this pass
     * @return {@code parentPatches} followed by the cascade patches that are not already covered by a
     *         structural (Insert/Replace) patch in {@code parentPatches}
     */
    public static List<Patch> mergeWithSuppression(List<Patch> parentPatches, List<Patch> cascade) {
        if (cascade == null || cascade.isEmpty()) {
            return parentPatches;
        }
        List<String> structuralIds = new ArrayList<>();
        for (Patch p : parentPatches) {
            if (p instanceof Patch.Insert || p instanceof Patch.Replace) {
                structuralIds.add(p.id());
            }
        }
        List<Patch> merged = new ArrayList<>(parentPatches);
        for (Patch c : cascade) {
            if (!coveredByStructural(structuralIds, c.id())) {
                merged.add(c);
            }
        }
        return merged;
    }

    private static boolean coveredByStructural(List<String> structuralIds, String cascadeId) {
        for (String structuralId : structuralIds) {
            if (IdPaths.isSelfOrDescendant(structuralId, cascadeId)) {
                return true;
            }
        }
        return false;
    }
}
