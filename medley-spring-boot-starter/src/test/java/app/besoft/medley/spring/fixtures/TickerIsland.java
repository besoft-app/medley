package app.besoft.medley.spring.fixtures;

import app.besoft.medley.spring.IslandAction;
import app.besoft.medley.spring.MedleyIsland;

/** Test island handler: mutates the owning {@link IslandHostComponent} on commit. */
@MedleyIsland("ticker")
public class TickerIsland {

    /** Scalar payload field bound by parameter name ("value"). */
    @IslandAction
    void set(IslandHostComponent owner, int value) {
        owner.setValue(value);
    }

    /** Whole payload object bound to the single non-owner parameter (DTO path). */
    @IslandAction
    void adjust(IslandHostComponent owner, Delta delta) {
        owner.setValue(owner.getValue() + delta.by());
    }

    public record Delta(int by) {}
}
