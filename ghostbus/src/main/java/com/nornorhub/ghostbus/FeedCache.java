package com.nornorhub.ghostbus;

import com.nornorhub.ghostbus.core.GtfsRealtime;
import java.io.IOException;

/** Shares one copy of the live feeds between screens so NJ Transit isn't called more than needed. */
final class FeedCache {

    private static final long MAX_AGE_MS = 15_000;
    private static final long ALERTS_MAX_AGE_MS = 5 * 60_000;

    private static GtfsRealtime live;
    private static long liveFetchedAt;
    private static GtfsRealtime alerts;
    private static long alertsFetchedAt;

    private FeedCache() {}

    /** Trip updates, vehicle positions and alerts merged into one feed. Call off the UI thread. */
    static synchronized GtfsRealtime get(NjtApi api, boolean force) throws IOException {
        long now = System.currentTimeMillis();
        if (force || live == null || now - liveFetchedAt > MAX_AGE_MS) {
            GtfsRealtime feed = GtfsRealtime.parse(api.fetchFeed("getTripUpdates"));
            feed.merge(GtfsRealtime.parse(api.fetchFeed("getVehiclePositions")));
            live = feed;
            liveFetchedAt = now;
        }
        if (alerts == null || now - alertsFetchedAt > ALERTS_MAX_AGE_MS) {
            try {
                alerts = GtfsRealtime.parse(api.fetchFeed("getAlerts"));
            } catch (IOException e) {
                alerts = new GtfsRealtime();
            }
            alertsFetchedAt = now;
        }
        GtfsRealtime combined = new GtfsRealtime();
        combined.merge(live);
        combined.merge(alerts);
        return combined;
    }

    /** When the live data was last fetched (epoch millis). */
    static synchronized long fetchedAt() {
        return liveFetchedAt;
    }
}
