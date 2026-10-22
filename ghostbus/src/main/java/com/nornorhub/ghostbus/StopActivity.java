package com.nornorhub.ghostbus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.nornorhub.ghostbus.core.Arrivals;
import com.nornorhub.ghostbus.core.GtfsRealtime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Upcoming buses at one stop, with how much each one can actually be trusted. */
public class StopActivity extends Activity {

    static final String EXTRA_STOP_ID = "stop_id";

    private static final long REFRESH_MS = 30_000;
    private static final long HORIZON_SECONDS = 2 * 3600;

    /** An arrival plus what we know about it from the local database. */
    static final class Row {
        Arrivals.Arrival arrival;
        TransitDb.Route route;
        String headsign;
        TransitDb.Reliability reliability;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefresh = () -> load(false);
    /** First predicted time we saw for each trip, to measure lateness when NJ Transit gives no delay. */
    private final Map<String, Long> firstSeen = new HashMap<>();
    private final Set<String> logged = new HashSet<>();

    private TransitDb.Stop stop;
    private TextView alerts;
    private TextView empty;
    private TextView updated;
    private RowAdapter adapter;
    private MenuItem favoriteItem;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        stop = TransitDb.get(this).stop(getIntent().getStringExtra(EXTRA_STOP_ID));
        if (stop == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_stop);
        setTitle(stop.name);
        if (getActionBar() != null && stop.code != null && !stop.code.isEmpty()) {
            getActionBar().setSubtitle("Stop #" + stop.code);
        }
        alerts = findViewById(R.id.alerts);
        empty = findViewById(R.id.empty);
        updated = findViewById(R.id.updated);
        ListView list = findViewById(R.id.list);
        adapter = new RowAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> onRowClicked(adapter.getItem(position)));
        empty.setText("Loading live buses…");
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        load(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(autoRefresh);
    }

    private void load(boolean force) {
        handler.removeCallbacks(autoRefresh);
        TransitDb db = TransitDb.get(this);
        NjtApi api = new NjtApi(new Prefs(this));
        Ui.IO.execute(() -> {
            try {
                GtfsRealtime feed = FeedCache.get(api, force);
                long now = System.currentTimeMillis() / 1000;
                List<Arrivals.Arrival> arrivals = Arrivals.forStop(feed, stop.id, stop.lat, stop.lon, now, HORIZON_SECONDS);
                List<Row> rows = new ArrayList<>();
                Set<String> routeIds = new HashSet<>();
                for (Arrivals.Arrival a : arrivals) {
                    Row row = new Row();
                    row.arrival = a;
                    TransitDb.Trip trip = db.trip(a.tripId);
                    if (a.routeId == null && trip != null) {
                        a.routeId = trip.routeId;
                    }
                    row.headsign = trip != null ? trip.headsign : null;
                    row.route = db.route(a.routeId);
                    row.reliability = db.reliability(stop.id, a.routeId);
                    rows.add(row);
                    if (a.routeId != null) {
                        routeIds.add(a.routeId);
                    }
                }
                String alertText = alertsFor(feed, routeIds, db, now);
                runOnUiThread(() -> show(rows, alertText, null));
            } catch (NjtApi.AuthException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, SetupActivity.class));
                });
            } catch (Exception e) {
                runOnUiThread(() -> show(null, null, "Couldn't get live data from NJ Transit: " + e.getMessage()));
            }
        });
    }

    private String alertsFor(GtfsRealtime feed, Set<String> routeIds, TransitDb db, long now) {
        Set<String> texts = new LinkedHashSet<>();
        for (GtfsRealtime.Alert alert : feed.alerts) {
            if ((alert.start != 0 && alert.start > now) || (alert.end != 0 && alert.end < now)) {
                continue;
            }
            boolean relevant = alert.stopIds.contains(stop.id);
            String routeLabel = null;
            for (String r : alert.routeIds) {
                if (routeIds.contains(r)) {
                    relevant = true;
                    TransitDb.Route route = db.route(r);
                    routeLabel = route != null ? route.label() : r;
                }
            }
            if (relevant && alert.header != null) {
                texts.add("⚠️ " + (routeLabel != null ? "Route " + routeLabel + ": " : "") + alert.header);
            }
        }
        return texts.isEmpty() ? null : TextUtils.join("\n", texts);
    }

    private void show(List<Row> rows, String alertText, String error) {
        if (rows != null) {
            for (Row r : rows) {
                if (r.arrival.tripId != null && !firstSeen.containsKey(r.arrival.tripId)) {
                    firstSeen.put(r.arrival.tripId, r.arrival.time);
                }
            }
            adapter.clear();
            adapter.addAll(rows);
            empty.setText("No buses predicted at this stop in the next 2 hours.");
            empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            updated.setText("Updated " + Ui.clock(this, FeedCache.fetchedAt() / 1000)
                    + " · refreshes every 30s · tap a bus for options");
        } else {
            empty.setText(error);
            empty.setVisibility(View.VISIBLE);
            updated.setText("Retrying in 30s");
        }
        alerts.setText(alertText);
        alerts.setVisibility(alertText == null ? View.GONE : View.VISIBLE);
        if (resumed) {
            handler.postDelayed(autoRefresh, REFRESH_MS);
        }
    }

    private void onRowClicked(Row row) {
        Arrivals.Arrival a = row.arrival;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (a.vehicle != null && a.vehicle.hasPosition) {
            labels.add("🗺️ Show this bus on the map");
            actions.add(() -> startActivity(new Intent(this, MapActivity.class)
                    .putExtra(MapActivity.EXTRA_TRIP_ID, a.tripId)
                    .putExtra(MapActivity.EXTRA_VEHICLE_ID, a.vehicleId)
                    .putExtra(MapActivity.EXTRA_STOP_ID, stop.id)));
        }
        if (a.routeId != null) {
            labels.add("🚌 All buses on this route");
            actions.add(() -> startActivity(new Intent(this, MapActivity.class)
                    .putExtra(MapActivity.EXTRA_ROUTE_ID, a.routeId)
                    .putExtra(MapActivity.EXTRA_STOP_ID, stop.id)));
        }
        labels.add("✅ I caught this bus");
        actions.add(() -> logBus(row, true));
        labels.add("👻 This bus never came");
        actions.add(() -> logBus(row, false));

        String title = (row.route != null ? row.route.label() : "Bus")
                + (row.headsign != null && !row.headsign.isEmpty() ? " → " + row.headsign : "");
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void logBus(Row row, boolean caught) {
        Arrivals.Arrival a = row.arrival;
        String key = a.tripId + "|" + caught;
        if (a.tripId != null && !logged.add(key)) {
            Toast.makeText(this, "Already logged", Toast.LENGTH_SHORT).show();
            return;
        }
        Integer lateSeconds = null;
        if (caught) {
            long now = System.currentTimeMillis() / 1000;
            Long first = firstSeen.get(a.tripId);
            if (a.delay != null) {
                lateSeconds = (int) (now - (a.time - a.delay));
            } else if (first != null) {
                lateSeconds = (int) (now - first);
            }
        }
        Integer late = lateSeconds;
        TransitDb db = TransitDb.get(this);
        Ui.IO.execute(() -> {
            db.logBus(stop.id, a.routeId, a.tripId, caught, late);
            runOnUiThread(() -> {
                Toast.makeText(this, caught ? "Logged ✅ Thanks!" : "Logged 👻 Sorry about that.", Toast.LENGTH_SHORT).show();
                load(false);
            });
        });
    }

    // --- Menu --------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.stop, menu);
        favoriteItem = menu.findItem(R.id.favorite);
        updateFavoriteIcon();
        return true;
    }

    private void updateFavoriteIcon() {
        if (favoriteItem != null) {
            boolean fav = TransitDb.get(this).isFavorite(stop.id);
            favoriteItem.setTitle(fav ? "★" : "☆");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.favorite) {
            TransitDb db = TransitDb.get(this);
            boolean fav = !db.isFavorite(stop.id);
            db.setFavorite(stop.id, fav);
            updateFavoriteIcon();
            Toast.makeText(this, fav ? "Saved to favourites" : "Removed from favourites", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (item.getItemId() == R.id.refresh) {
            load(true);
            return true;
        }
        if (item.getItemId() == R.id.stop_map) {
            startActivity(new Intent(this, MapActivity.class).putExtra(MapActivity.EXTRA_STOP_ID, stop.id));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static final class RowAdapter extends ArrayAdapter<Row> {

        RowAdapter(Context context) {
            super(context, 0, new ArrayList<>());
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.item_arrival, parent, false);
            }
            Row row = getItem(position);
            Arrivals.Arrival a = row.arrival;
            Context c = getContext();

            Ui.routeBadge(view.findViewById(R.id.route), row.route, a.routeId);
            ((TextView) view.findViewById(R.id.headsign)).setText(
                    row.headsign != null && !row.headsign.isEmpty() ? row.headsign
                            : row.route != null && !row.route.longName.isEmpty() ? row.route.longName : "Bus");

            TextView minutes = view.findViewById(R.id.minutes);
            minutes.setText(Ui.minutesUntil(a.time));
            TextView clock = view.findViewById(R.id.clock);
            clock.setText(Ui.clock(c, a.time));

            TextView status = view.findViewById(R.id.status);
            String late = Ui.lateness(a.delay);
            String prefix = late != null ? late + " · " : "";
            switch (a.status) {
                case LIVE:
                    status.setText(prefix + "📍 " + Ui.miles(a.milesAway) + " · GPS " + Ui.age(a.gpsAgeSeconds));
                    status.setTextColor(Ui.OK);
                    break;
                case STALE_GPS:
                    status.setText(prefix + "⚠️ GPS lost: last seen " + Ui.age(a.gpsAgeSeconds)
                            + ", " + Ui.miles(a.milesAway));
                    status.setTextColor(Ui.WARN);
                    break;
                case NO_GPS:
                    status.setText(prefix + "👻 No live GPS for this bus. It may not show up.");
                    status.setTextColor(Ui.GHOST);
                    break;
                case CANCELED:
                    status.setText("❌ Canceled by NJ Transit");
                    status.setTextColor(0xFFC62828);
                    break;
            }
            boolean canceled = a.status == Arrivals.Status.CANCELED;
            int strike = canceled ? Paint.STRIKE_THRU_TEXT_FLAG : 0;
            minutes.setPaintFlags((minutes.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG) | strike);
            minutes.setAlpha(a.status == Arrivals.Status.LIVE ? 1f : 0.55f);

            TextView log = view.findViewById(R.id.log);
            TransitDb.Reliability r = row.reliability;
            if (r != null && r.total() > 0) {
                StringBuilder s = new StringBuilder("Your log here: ");
                s.append(r.caught).append(" caught");
                if (r.noShows > 0) {
                    s.append(", ").append(r.noShows).append(" never came (")
                            .append(Math.round(100.0 * r.noShows / r.total())).append("%)");
                }
                if (r.avgLateSeconds != null) {
                    s.append(" · usually ").append(Ui.lateness(r.avgLateSeconds));
                }
                log.setText(s);
                log.setVisibility(View.VISIBLE);
            } else {
                log.setVisibility(View.GONE);
            }
            return view;
        }
    }
}
