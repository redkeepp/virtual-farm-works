package com.virtualfarmworks.sim;

/**
 * A group of identical virtual plots (same plant, same soil) inside one machine. The Starter Farm Matrix has a single
 * group (one seed slot); higher tiers may have several, all sharing the machine's single {@link GrowthCycle}.
 *
 * <p>The ONLY per-group state is two counters, never per-plot data (performance rule: no individual progress, no
 * per-plot objects):
 * <ul>
 *   <li>{@code active} — plots that were planted before the current cycle started; they produce at the next
 *       harvest;</li>
 *   <li>{@code pending} — plots added while a cycle was already running; they produce nothing at the next harvest,
 *       they only become active then. This is what stops "insert seeds at 90%, harvest at 100%" exploits without
 *       per-plot timers.</li>
 * </ul>
 *
 * <p>Mutators are package-private: only {@link GrowthCycle} changes groups, so the machine-wide rules (empty machine,
 * progress reset) cannot be bypassed. Everyone else gets read-only access.
 */
public final class PlotGroup {
    private int active;
    private int pending;

    PlotGroup() {
    }

    public int active() {
        return active;
    }

    public int pending() {
        return pending;
    }

    public int total() {
        return active + pending;
    }

    void addActive(int count) {
        active += count;
    }

    void addPending(int count) {
        pending += count;
    }

    /**
     * Removes {@code count} plots, PENDING first, then ACTIVE. Removing the not-yet-eligible plots first means a player
     * who takes seeds out never loses progress they already earned, and can never turn pending plots into active ones
     * by juggling items. {@code count} larger than {@link #total()} just empties the group.
     */
    void remove(int count) {
        int fromPending = Math.min(count, pending);
        pending -= fromPending;
        active -= Math.min(count - fromPending, active);
    }

    /** End of a growth cycle: every pending plot becomes eligible for the next harvest. */
    void promotePending() {
        active += pending;
        pending = 0;
    }

    void clear() {
        active = 0;
        pending = 0;
    }

    /** Restores saved counters; negative values (corrupted data) become 0. */
    void set(int active, int pending) {
        this.active = Math.max(0, active);
        this.pending = Math.max(0, pending);
    }

    @Override
    public String toString() {
        return "PlotGroup[active=" + active + ", pending=" + pending + "]";
    }
}
