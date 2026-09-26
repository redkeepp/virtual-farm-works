/*
 * SmoothProgress — client-side smoothing of the Farm Matrix progress: the server sends the progress only every 5 ticks
 * (owner rule: throttled GUI sync), and this animates from the previous value to the new one, so the bar and the
 * "Growth: X%" line move through every step (10, 11, 12, 13, 14%) instead of jumping. Pure Java, unit-tested.
 */
package com.virtualfarmworks.client;

/**
 * Interpolates, never predicts: the shown value moves from where it is toward the last value the server sent, over
 * about one sync interval, so it never shows a progress the machine has not reached (it runs at most one interval
 * behind). The duration follows the real time between syncs, so a lagging server gives a slower, still continuous
 * bar. Going back (a harvest restarted the cycle, the plots were reset) is shown at once, never animated backwards.
 */
public final class SmoothProgress {
    /** Nominal time between two progress syncs: FarmMatrixMenu syncs every 5 ticks = 250 ms at 20 TPS. */
    static final long NOMINAL_INTERVAL_MS = 250;
    /** A longer gap (machine paused, then resumed) animates at the nominal speed instead of crawling. */
    static final long MAX_INTERVAL_MS = 1_000;

    private boolean started;
    private double from;
    private double target;
    private long targetSinceMs;
    private long durationMs = NOMINAL_INTERVAL_MS;

    /**
     * Call once per frame.
     *
     * @param synced the latest progress from the server, {@code 0..1}
     * @param nowMs  a monotonic clock in milliseconds
     * @return the progress to draw this frame
     */
    public double update(double synced, long nowMs) {
        if (!started) {
            started = true;
            from = synced;
            target = synced;
            targetSinceMs = nowMs;
            return synced;
        }
        if (synced != target) {
            if (synced < target) {
                from = synced; // went back: show it at once
            } else {
                long gap = nowMs - targetSinceMs;
                durationMs = gap <= MAX_INTERVAL_MS ? Math.max(gap, NOMINAL_INTERVAL_MS) : NOMINAL_INTERVAL_MS;
                from = shown(nowMs); // continue from where the bar is, even mid-animation
            }
            target = synced;
            targetSinceMs = nowMs;
        }
        return shown(nowMs);
    }

    private double shown(long nowMs) {
        double t = Math.clamp((nowMs - targetSinceMs) / (double) durationMs, 0.0, 1.0);
        return from + (target - from) * t;
    }
}
