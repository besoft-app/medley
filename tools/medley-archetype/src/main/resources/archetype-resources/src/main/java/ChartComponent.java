package ${package};

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A component hosting a client island (see {@code chart.html} and {@code sparkline-island.js}).
 * The sparkline draws points and handles hover entirely client-side (zero round-trips); only a
 * coarse "select" commit reaches the server, which records it in {@code @State selected} and pushes
 * {@code data-selected} back down as a host-attribute patch.
 */
@MedleyRoute("/chart")
@MedleyComponent("chart")
public class ChartComponent extends Component {

    private static final int[] POINTS = {3, 7, 4, 9, 2, 6, 8};

    @State int selected = -1;

    @Action void clearSelection() { selected = -1; }

    /** Set by the sparkline island's commit (via {@link SparklineIsland}). */
    void selectPoint(int index) {
        if (index >= 0 && index < POINTS.length) {
            selected = index;
        }
    }

    public String getPointsCsv() {
        return Arrays.stream(POINTS).mapToObj(Integer::toString).collect(Collectors.joining(","));
    }

    public int getSelected() { return selected; }

    public boolean isHasSelection() { return selected >= 0; }

    public String getSelectedLabel() {
        return selected < 0 ? "(none — hover and click a point)"
                : "point #" + selected + " = " + POINTS[selected];
    }
}
