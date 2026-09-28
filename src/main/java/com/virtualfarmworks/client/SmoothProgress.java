/*
 * SmoothProgress — client-side smoothing of the Farm Matrix progress: the server sends the progress only every 5 ticks
 * (owner rule: throttled GUI sync), and this animates between the values it receives, so the bar and the
 * "Growth: X%" line move through every step (10, 11, 12, 13, 14%) and a completed cycle runs the bar to the end before
 * it restarts, instead of jumping. Pure Java, unit-tested.
 */
package com.virtualfarmworks.client;

/**
 * Interpolates, never predicts: the shown value moves from where it is toward the last value the server sent, over
 * about one sync interval, so it never shows a progress the machine has not reached (it runs at most one interval
 * behind). The duration follows the real time between syncs, so a lagging server gives a slower, still continuous
 * bar.
 *
 * <h2>Cycles</h2>
 * Positions are kept "unwrapped": cycle number + progress (2.3 = 30% into the third cycle seen). The server also sends
 * a harvest counter; when it changes, a cycle was completed since the last sync, so the target moves to the next cycle
 * and the bar runs to the end, restarts empty and grows to the new value in one continuous motion. At most one wrap
 * is animated even if several cycles passed (a very fast machine). If the progress goes back WITHOUT a harvest (the
 * plots were reset), it is shown at once: animating it would pretend a harvest happened.
 *
 * <p>Start it only once the server has sent real values: an unsynced menu reads 0 everywhere, and starting from that 0
 * made the bar race from 0 to the real progress every time the GUI opened (owner report).
 */
public final class SmoothProgress {
    /** Nominal time between two progress syncs: FarmMatrixMenu syncs every 5 ticks = 250 ms at 20 TPS. */
    static final long NOMINAL_INTERVAL_MS = 250;
    /** A longer gap (machine paused, then resumed) animates at the nominal speed instead of crawling. */
    static final long MAX_INTERVAL_MS = 1_000;

    private boolean started;
    private int lastHarvests;
    /** Cycle the target is in: the unwrapped target is {@code cycle + target}. */
    private long cycle;
    private double target;
    /** Unwrapped position the current animation started from. */
    private double from;
    private long targetSinceMs;
    private long durationMs = NOMINAL_INTERVAL_MS;

    /**
     * Call once per frame.
     *
     * @param synced   the latest progress from the server, {@code 0..1}
     * @param harvests the server's harvest counter (only its changes matter)
     * @param nowMs    a monotonic clock in milliseconds
     * @return the progress to draw this frame, {@code 0..1}
     */
    public double update(double synced, int harvests, long nowMs) {
        if (!started) {
            started = true;
            lastHarvests = harvests;
            target = synced;
            from = synced;
            targetSinceMs = nowMs;
            return synced;
        }
        boolean harvested = harvests != lastHarvests;
        lastHarvests = harvests;
        if (harvested || synced != target) {
            double shown = position(nowMs);
            if (harvested) {
                cycle++;
                from = Math.max(shown, cycle - 1.0); // animate one wrap at most
                durationMs = duration(nowMs);
            } else if (synced < target) {
                from = cycle + synced; // went back without a harvest (plots reset): show it at once
            } else {
                from = shown;          // continue from where the bar is, even mid-animation
                durationMs = duration(nowMs);
            }
            target = synced;
            targetSinceMs = nowMs;
        }
        return display(position(nowMs));
    }

    /** Animation length: the real time since the last change, within reasonable bounds. */
    private long duration(long nowMs) {
        long gap = nowMs - targetSinceMs;
        return gap <= MAX_INTERVAL_MS ? Math.max(gap, NOMINAL_INTERVAL_MS) : NOMINAL_INTERVAL_MS;
    }

    /** Current unwrapped position. */
    private double position(long nowMs) {
        double t = Math.clamp((nowMs - targetSinceMs) / (double) durationMs, 0.0, 1.0);
        return from + (cycle + target - from) * t;
    }

    /**
     * What the bar shows: at rest, the target itself (so 100% stays a full bar while a harvest waits); in motion, the
     * part of the cycle the position is in (after a wrap the bar restarts from empty).
     */
    private double display(double position) {
        if (position >= cycle + target) {
            return target;
        }
        return position - Math.floor(position);
    }
}
