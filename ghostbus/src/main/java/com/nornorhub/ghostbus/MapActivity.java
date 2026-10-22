package com.nornorhub.ghostbus;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import com.nornorhub.ghostbus.core.Arrivals;
import com.nornorhub.ghostbus.core.GtfsRealtime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

/**
 * Where the buses really are. Shows either one bus (and its stop), every bus on a route,
 * or just a stop.
 */
public class MapActivity extends Activity {

    static final String EXTRA_ROUTE_ID = "route_id";
    static final String EXTRA_TRIP_ID = "trip_id";
    static final String EXTRA_VEHICLE_ID = "vehicle_id";
    static final String EXTRA_STOP_ID = "stop_id";

    private static final long REFRESH_MS = 20_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefresh = this::load;
    private final List<Marker> busMarkers = new ArrayList<>();

    private MapView map;
    private TextView info;
    private String routeId;
    private String tripId;
    private String vehicleId;
    private TransitDb.Stop stop;
    private TransitDb.Route route;
    private boolean zoomedToBuses;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().setOsmdroidBasePath(getCacheDir());
        Configuration.getInstance().setOsmdroidTileCache(getCacheDir());
        setContentView(R.layout.activity_map);

        routeId = getIntent().getStringExtra(EXTRA_ROUTE_ID);
        tripId = getIntent().getStringExtra(EXTRA_TRIP_ID);
        vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        String stopId = getIntent().getStringExtra(EXTRA_STOP_ID);
        TransitDb db = TransitDb.get(this);
        stop = stopId != null ? db.stop(stopId) : null;
        route = db.route(routeId);

        if (tripId != null) {
            setTitle("Your bus");
        } else if (routeId != null) {
            setTitle("Route " + (route != null ? route.label() : routeId));
            if (getActionBar() != null && route != null && !route.longName.isEmpty()) {
                getActionBar().setSubtitle(route.longName);
            }
        } else if (stop != null) {
            setTitle(stop.name);
        }

        map = findViewById(R.id.map);
        info = findViewById(R.id.info);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(stop != null ? 15.0 : 11.0);
        map.getController().setCenter(stop != null ? new GeoPoint(stop.lat, stop.lon) : new GeoPoint(40.735, -74.17));

        if (stop != null) {
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(stop.lat, stop.lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            m.setIcon(Ui.dot(this, 0xFFC62828, 16, true));
            m.setTitle("🚏 " + stop.name);
            m.setSnippet(stop.code != null && !stop.code.isEmpty() ? "Stop #" + stop.code : null);
            map.getOverlays().add(m);
        }
        if (tripId == null && routeId == null) {
            info.setText("🚏 " + (stop != null ? stop.name : ""));
        } else {
            info.setText("Loading live bus positions…");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        resumed = true;
        if (tripId != null || routeId != null) {
            load();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        resumed = false;
        handler.removeCallbacks(autoRefresh);
    }

    private static final class Bus {
        GtfsRealtime.VehiclePosition vp;
        String headsign;
        long ageSeconds;
    }

    private void load() {
        handler.removeCallbacks(autoRefresh);
        TransitDb db = TransitDb.get(this);
        NjtApi api = new NjtApi(new Prefs(this));
        Ui.IO.execute(() -> {
            try {
                GtfsRealtime feed = FeedCache.get(api, false);
                long now = System.currentTimeMillis() / 1000;
                List<Bus> buses = new ArrayList<>();
                Set<String> reportingTrips = new HashSet<>();
                Map<String, String> routeOfTrip = new HashMap<>();
                for (GtfsRealtime.VehiclePosition vp : feed.vehicles) {
                    if (!vp.hasPosition || !matches(vp, db, routeOfTrip)) {
                        continue;
                    }
                    Bus b = new Bus();
                    b.vp = vp;
                    TransitDb.Trip trip = db.trip(vp.tripId);
                    b.headsign = trip != null ? trip.headsign : null;
                    b.ageSeconds = Math.max(0, now - (vp.timestamp != 0 ? vp.timestamp : feed.timestamp));
                    buses.add(b);
                    if (vp.tripId != null) {
                        reportingTrips.add(vp.tripId);
                    }
                }
                int ghosts = 0;
                if (routeId != null && tripId == null) {
                    // Trips NJ Transit is predicting on this route that have no bus reporting GPS.
                    for (GtfsRealtime.TripUpdate tu : feed.tripUpdates) {
                        String r = tu.routeId != null ? tu.routeId : routeOf(tu.tripId, db, routeOfTrip);
                        if (routeId.equals(r) && !tu.canceled && tu.tripId != null
                                && !reportingTrips.contains(tu.tripId) && hasUpcomingStop(tu, now)) {
                            ghosts++;
                        }
                    }
                }
                int ghostCount = ghosts;
                runOnUiThread(() -> show(buses, ghostCount));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    info.setText("Couldn't get live data from NJ Transit: " + e.getMessage());
                    scheduleRefresh();
                });
            }
        });
    }

    private boolean matches(GtfsRealtime.VehiclePosition vp, TransitDb db, Map<String, String> routeOfTrip) {
        if (tripId != null) {
            return tripId.equals(vp.tripId) || (vehicleId != null && vehicleId.equals(vp.vehicleId));
        }
        String r = vp.routeId != null ? vp.routeId : routeOf(vp.tripId, db, routeOfTrip);
        return routeId.equals(r);
    }

    private static String routeOf(String tripId, TransitDb db, Map<String, String> cache) {
        if (tripId == null) {
            return null;
        }
        if (!cache.containsKey(tripId)) {
            TransitDb.Trip trip = db.trip(tripId);
            cache.put(tripId, trip != null ? trip.routeId : null);
        }
        return cache.get(tripId);
    }

    private static boolean hasUpcomingStop(GtfsRealtime.TripUpdate tu, long now) {
        for (GtfsRealtime.StopTimeUpdate stu : tu.stops) {
            long t = stu.time();
            if (t != 0 && t > now && t < now + 3600) {
                return true;
            }
        }
        return false;
    }

    private void show(List<Bus> buses, int ghosts) {
        map.getOverlays().removeAll(busMarkers);
        busMarkers.clear();
        int stale = 0;
        List<GeoPoint> points = new ArrayList<>();
        for (Bus b : buses) {
            boolean isStale = b.ageSeconds > Arrivals.STALE_GPS_SECONDS;
            if (isStale) {
                stale++;
            }
            Marker m = new Marker(map);
            GeoPoint p = new GeoPoint(b.vp.lat, b.vp.lon);
            points.add(p);
            m.setPosition(p);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            Drawable icon = Ui.dot(this, isStale ? 0xFF9E9E9E : Ui.NJT_BLUE, 22, false);
            m.setIcon(icon);
            String label = b.vp.vehicleLabel != null ? b.vp.vehicleLabel
                    : b.vp.vehicleId != null ? "Bus " + b.vp.vehicleId : "Bus";
            m.setTitle("🚌 " + label + (b.headsign != null && !b.headsign.isEmpty() ? " → " + b.headsign : ""));
            String snippet = (isStale ? "⚠️ GPS lost, last seen " : "GPS ") + Ui.age(b.ageSeconds);
            if (stop != null) {
                snippet += " · " + Ui.miles(com.nornorhub.ghostbus.core.Geo.milesBetween(
                        b.vp.lat, b.vp.lon, stop.lat, stop.lon)) + " from your stop";
            }
            m.setSnippet(snippet);
            busMarkers.add(m);
            map.getOverlays().add(m);
        }
        map.invalidate();

        if (!zoomedToBuses && !points.isEmpty()) {
            zoomedToBuses = true;
            if (stop != null) {
                points.add(new GeoPoint(stop.lat, stop.lon));
            }
            if (points.size() == 1) {
                map.getController().setZoom(16.0);
                map.getController().setCenter(points.get(0));
            } else {
                BoundingBox box = BoundingBox.fromGeoPoints(points).increaseByScale(1.3f);
                map.post(() -> map.zoomToBoundingBox(box, false, (int) Ui.dp(this, 32)));
            }
        }

        StringBuilder s = new StringBuilder();
        if (tripId != null) {
            if (buses.isEmpty()) {
                s.append("👻 This bus isn't reporting GPS right now.");
            } else {
                Bus b = buses.get(0);
                s.append(b.ageSeconds > Arrivals.STALE_GPS_SECONDS ? "⚠️ GPS lost, last seen " : "📍 GPS updated ")
                        .append(Ui.age(b.ageSeconds));
            }
        } else {
            s.append("🚌 ").append(buses.size()).append(buses.size() == 1 ? " bus" : " buses").append(" reporting GPS");
            if (stale > 0) {
                s.append(" (").append(stale).append(" stale)");
            }
            if (ghosts > 0) {
                s.append("\n👻 ").append(ghosts).append(ghosts == 1 ? " predicted trip has" : " predicted trips have")
                        .append(" no bus reporting GPS");
            }
        }
        s.append("\nUpdated ").append(Ui.clock(this, FeedCache.fetchedAt() / 1000)).append(" · tap a bus for details");
        info.setText(s);
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (resumed) {
            handler.postDelayed(autoRefresh, REFRESH_MS);
        }
    }
}
