/*
 * PlotGroup — the ACTIVE/PENDING counters of one group of identical virtual plots (the only per-group state), plus how
 * many ACTIVE plots were already harvested when a harvest goes in batches. Part of the Minecraft-free simulation core
 * (package sim); mutated only by GrowthCycle.
 */
package com.virtualfarmworks.sim;

/**
 * A group of identical virtual plots (same plant, same soil) inside one machine. The Starter Farm Matrix has a single
 * group (one seed slot); higher tiers may have several, all sharing the machine's single {@link GrowthCycle}.
 *
 * <p>The ONLY per-group state is three counters, never per-plot data (performance rule: no individual progress, no
 * per-plot objects):
 * <ul>
 *   <li>{@code active} — plots that were planted before the current cycle started; they produce at the next
 *       harvest;</li>
 *   <li>{@code pending} — plots added while a cycle was already running; they produce nothing at the next harvest,
 *       they only become active then. This is what stops "insert seeds at 90%, harvest at 100%" exploits without
 *       per-plot timers;</li>
 *   <li>{@code harvested} — while a harvest is due: how many ACTIVE plots were already harvested. A harvest that does
 *       not fit the output all at once goes in batches (owner design, step 8); the other ripe plots wait "on the
 *       plant", so their items do not exist yet. Always {@code 0 <= harvested <= active}; back to 0 when the cycle
 *       completes.</li>
 * </ul>
 *
 * <p>Mutators are package-private: only {@link GrowthCycle} changes groups, so the machine-wide rules (empty machine,
 * progress reset) cannot be bypassed. Everyone else gets read-only access.
 */
public final class PlotGroup {
    private int active;
    private int pending;
    private int harvested;

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

    /** ACTIVE plots already harvested in the current due harvest. */
    public int harvested() {
        return harvested;
    }

    /** ACTIVE plots not harvested yet in the current cycle (while a harvest is due: the ripe plots still waiting). */
    public int unharvested() {
        return active - harvested;
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
     *
     * <p>ACTIVE plots already harvested in a batched harvest go before the ripe ones still waiting, so taking seeds out
     * mid-harvest keeps the crops not harvested yet. No exploit: a plot is still harvested at most once per cycle.
     */
    void remove(int count) {
        int fromPending = Math.min(count, pending);
        pending -= fromPending;
        int fromActive = Math.min(count - fromPending, active);
        active -= fromActive;
        harvested = Math.max(0, harvested - fromActive);
    }

    /** Records that {@code count} more ACTIVE plots were harvested (never more than {@link #unharvested()}). */
    void harvest(int count) {
        harvested += Math.clamp(count, 0, unharvested());
    }

    /** End of a growth cycle: every pending plot becomes eligible for the next harvest, nothing is harvested yet. */
    void promotePending() {
        active += pending;
        pending = 0;
        harvested = 0;
    }

    void clear() {
        active = 0;
        pending = 0;
        harvested = 0;
    }

    /** Restores saved counters; negative values (corrupted data) become 0, harvested never exceeds active. */
    void set(int active, int pending, int harvested) {
        this.active = Math.max(0, active);
        this.pending = Math.max(0, pending);
        this.harvested = Math.clamp(harvested, 0, this.active);
    }

    @Override
    public String toString() {
        return "PlotGroup[active=" + active + ", pending=" + pending + ", harvested=" + harvested + "]";
    }
}
