package app.besoft.medley.demo;

import app.besoft.medley.spring.IslandAction;
import app.besoft.medley.spring.MedleyIsland;

/**
 * Server-side handler for the {@code sparkline} island. Stateless: it persists the coarse
 * selection by mutating the owning {@link ChartComponent}'s {@code @State}, whose re-render then
 * pushes the updated {@code :data-selected} prop back down to the island.
 */
@MedleyIsland("sparkline")
public class SparklineIsland {

    @IslandAction
    void select(ChartComponent owner, int index) {
        owner.selectPoint(index);
    }
}
