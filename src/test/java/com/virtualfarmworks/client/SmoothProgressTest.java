/*
 * SmoothProgressTest — JUnit tests of client/SmoothProgress: the progress bar animates between the 5-tick syncs
 * instead of jumping, never runs ahead of the server and never animates backwards. Run with `gradlew test`.
 */
package com.virtualfarmworks.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmoothProgressTest {
    private static final double EPS = 1e-9;

    @Test
    void theFirstValueIsShownAtOnce() {
        SmoothProgress smooth = new SmoothProgress();
        assertEquals(0.10, smooth.update(0.10, 1_000), EPS);
    }

    @Test
    void aNewValueIsReachedStepByStepOverOneSyncInterval() {
        // The owner's example: Growth goes from 10% to 14% in one sync; the bar passes 11, 12 and 13% on the way.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.10, 0);
        assertEquals(0.10, smooth.update(0.14, 250), EPS, "the animation starts where the bar was");
        assertEquals(0.11, smooth.update(0.14, 312), 0.001, "a quarter of the way after a quarter of the interval");
        assertEquals(0.12, smooth.update(0.14, 375), EPS, "halfway after half the interval");
        assertEquals(0.14, smooth.update(0.14, 500), EPS, "the target is reached after one interval");
        assertEquals(0.14, smooth.update(0.14, 900), EPS, "and never passed");
    }

    @Test
    void aSyncArrivingMidAnimationContinuesFromTheCurrentPosition() {
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.10, 0);
        smooth.update(0.14, 250);                      // animates 0.10 -> 0.14 over 250 ms
        double halfway = smooth.update(0.14, 375);     // 0.12
        assertEquals(halfway, smooth.update(0.20, 375), EPS, "no jump when the next value arrives early");
    }

    @Test
    void goingBackIsShownAtOnce() {
        // A harvest restarts the cycle: 97% -> 2% must not slide backwards across the bar.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.90, 0);
        smooth.update(0.97, 250);
        assertEquals(0.02, smooth.update(0.02, 300), EPS);
    }

    @Test
    void theDurationFollowsTheRealTimeBetweenSyncs() {
        // A lagging server (10 TPS) sends every 500 ms: the bar moves continuously over 500 ms instead of 250 + pause.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.10, 0);
        smooth.update(0.12, 500);
        assertEquals(0.11, smooth.update(0.12, 750), EPS);
    }

    @Test
    void aLongPauseDoesNotMakeTheNextStepCrawl() {
        // Machine switched off for 10 s, then back on: the first step animates at the nominal speed.
        SmoothProgress smooth = new SmoothProgress();
        smooth.update(0.50, 0);
        smooth.update(0.52, 10_000);
        assertEquals(0.52, smooth.update(0.52, 10_000 + SmoothProgress.NOMINAL_INTERVAL_MS), EPS);
    }
}
