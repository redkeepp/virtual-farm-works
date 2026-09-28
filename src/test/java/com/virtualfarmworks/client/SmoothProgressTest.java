/*
 * SmoothProgressTest — JUnit tests of client/SmoothProgress: the progress bar animates between the 5-tick syncs
 * instead of jumping, runs to the end before a new cycle, never runs ahead of the server and never animates a reset.
 * Run with `gradlew test`.
 */
package com.virtualfarmworks.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmoothProgressTest {
    private static final double EPS = 1e-9;

    @Test
    void theFirstValueIsShownAtOnce() {
        // The GUI opens with the machine at 70%: no race from 0 (owner report).
        SmoothProgress smooth = new SmoothProgress();
        assertEquals(0.70, smooth.update(0.70, 5, 1_000), EPS);
    }

    @Test
    void aNewValueIsReachedStepByStepOverOneSyncInterval() {
        // The owner's example: Growth goes from 10% to 14% in one sync; the bar passes 11, 12 and 13% on the way.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.10, 0, 0);
        assertEquals(0.10, smooth.update(0.14, 0, 250), EPS, "the animation starts where the bar was");
        assertEquals(0.11, smooth.update(0.14, 0, 312), 0.001, "a quarter of the way after a quarter of the interval");
        assertEquals(0.12, smooth.update(0.14, 0, 375), EPS, "halfway after half the interval");
        assertEquals(0.14, smooth.update(0.14, 0, 500), EPS, "the target is reached after one interval");
        assertEquals(0.14, smooth.update(0.14, 0, 900), EPS, "and never passed");
    }

    @Test
    void aSyncArrivingMidAnimationContinuesFromTheCurrentPosition() {
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.10, 0, 0);
        smooth.update(0.14, 0, 250);                      // animates 0.10 -> 0.14 over 250 ms
        double halfway = smooth.update(0.14, 0, 375);     // 0.12
        assertEquals(halfway, smooth.update(0.20, 0, 375), EPS, "no jump when the next value arrives early");
    }

    @Test
    void aHarvestRunsTheBarToTheEndBeforeItRestarts() {
        // Owner report: 100% -> 1% was not smooth. Now the bar finishes the cycle, restarts empty and grows.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.90, 0, 0);
        smooth.update(0.95, 0, 250);                      // 0.90 -> 0.95
        assertEquals(0.95, smooth.update(0.03, 1, 500), EPS, "starts where the bar was");
        // Unwrapped 0.95 -> 1.03 over 250 ms.
        assertEquals(0.982, smooth.update(0.03, 1, 600), EPS, "still running to the end");
        assertEquals(0.014, smooth.update(0.03, 1, 700), EPS, "restarted and growing");
        assertEquals(0.03, smooth.update(0.03, 1, 750), EPS, "then the new value");
    }

    @Test
    void aFullBarWaitingForSpaceStaysFullThenRestarts() {
        // OUTPUT FULL: the bar sits at 100% for a while, then the harvest is stored and a new cycle begins.
        SmoothProgress smooth = new SmoothProgress();
        assertEquals(1.0, smooth.update(1.0, 0, 0), EPS);
        assertEquals(1.0, smooth.update(1.0, 0, 5_000), EPS, "full while waiting");
        assertEquals(0.0, smooth.update(0.01, 1, 5_250), EPS, "the harvest empties it");
        assertEquals(0.01, smooth.update(0.01, 1, 5_250 + SmoothProgress.NOMINAL_INTERVAL_MS), EPS);
    }

    @Test
    void goingBackWithoutAHarvestIsShownAtOnce() {
        // Seeds removed: progress resets to 0. Animating it would pretend a harvest happened.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.50, 0, 0);
        smooth.update(0.55, 0, 250);
        assertEquals(0.0, smooth.update(0.0, 0, 300), EPS);
    }

    @Test
    void severalHarvestsBetweenSyncsAnimateOneWrap() {
        // A very fast machine: the counter jumps by 3 in one sync; the bar makes one continuous wrap.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.90, 0, 0);
        smooth.update(0.95, 0, 250);
        smooth.update(0.50, 3, 500);                      // unwrapped 0.95 -> 1.50 over 250 ms
        assertEquals(0.225, smooth.update(0.50, 3, 625), EPS);
        assertEquals(0.50, smooth.update(0.50, 3, 750), EPS);
    }

    @Test
    void theDurationFollowsTheRealTimeBetweenSyncs() {
        // A lagging server (10 TPS) sends every 500 ms: the bar moves continuously over 500 ms instead of 250 + pause.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.10, 0, 0);
        smooth.update(0.12, 0, 500);
        assertEquals(0.11, smooth.update(0.12, 0, 750), EPS);
    }

    @Test
    void aLongPauseDoesNotMakeTheNextStepCrawl() {
        // Machine switched off for 10 s, then back on: the first step animates at the nominal speed.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.50, 0, 0);
        smooth.update(0.52, 0, 10_000);
        assertEquals(0.52, smooth.update(0.52, 0, 10_000 + SmoothProgress.NOMINAL_INTERVAL_MS), EPS);
    }
}
