/*
 * Example Medley island: a canvas sparkline.
 *
 * Demonstrates the hybrid promise: hover tracking is high-frequency and runs entirely
 * client-side (zero WebSocket traffic). Only a coarse "select" commit is sent to the server
 * (-> @IslandAction), which records the selection and pushes `data-selected` back down as a
 * host-attribute patch — arriving here via onProp() with no island teardown.
 */
(function () {
  "use strict";

  function boot() {
    if (!window.medley || !window.medley.MedleyIsland) {
      console.error("[sparkline] medley runtime not present");
      return;
    }

    class Sparkline extends window.medley.MedleyIsland {
      mount() {
        this.hover = -1;
        this.canvas = document.createElement("canvas");
        this.canvas.width = 260;
        this.canvas.height = 64;
        this.canvas.style.cursor = "crosshair";
        this.host.appendChild(this.canvas);

        this.canvas.addEventListener("mousemove", (e) => {
          // High-frequency, purely local — no server round-trip.
          const next = this.nearest(e.offsetX);
          if (next !== this.hover) { this.hover = next; this.draw(); }
        });
        this.canvas.addEventListener("mouseleave", () => { this.hover = -1; this.draw(); });
        this.canvas.addEventListener("click", () => {
          if (this.hover >= 0) this.commit("select", { index: this.hover });
        });

        this.draw();
      }

      // Server pushed a prop (data-points / data-selected) — redraw, keep our canvas.
      onProp(name) {
        if (name === "data-points" || name === "data-selected") this.draw();
      }

      points() {
        return (this.prop("data-points") || "")
          .split(",").filter((s) => s.length).map(Number);
      }

      selected() {
        return parseInt(this.prop("data-selected") || "-1", 10);
      }

      nearest(x) {
        const pts = this.points();
        if (pts.length < 2) return pts.length - 1;
        const step = this.canvas.width / (pts.length - 1);
        return Math.max(0, Math.min(pts.length - 1, Math.round(x / step)));
      }

      draw() {
        const ctx = this.canvas.getContext("2d");
        const w = this.canvas.width, h = this.canvas.height;
        const pts = this.points();
        ctx.clearRect(0, 0, w, h);
        if (!pts.length) return;

        const max = Math.max.apply(null, pts), min = Math.min.apply(null, pts);
        const range = (max - min) || 1;
        const step = pts.length > 1 ? w / (pts.length - 1) : w;
        const at = (i) => [i * step, h - ((pts[i] - min) / range) * (h - 10) - 5];

        ctx.beginPath();
        pts.forEach((_, i) => { const [x, y] = at(i); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); });
        ctx.strokeStyle = "#2b6cb0";
        ctx.lineWidth = 2;
        ctx.stroke();

        const sel = this.selected();
        pts.forEach((_, i) => {
          const [x, y] = at(i);
          ctx.beginPath();
          ctx.arc(x, y, i === this.hover ? 5 : 3, 0, Math.PI * 2);
          ctx.fillStyle = (i === sel) ? "#e53e3e" : (i === this.hover ? "#2b6cb0" : "#90cdf4");
          ctx.fill();
        });
      }
    }

    window.medley.registerIsland("sparkline", Sparkline);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
