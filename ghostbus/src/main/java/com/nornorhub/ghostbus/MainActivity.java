package com.nornorhub.ghostbus;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Home: favourite stops, stop search, stops near me, and the list of all routes. */
public class MainActivity extends Activity {

    private static final int LOCATION_REQUEST = 1;

    private EditText search;
    private TextView heading;
    private TextView empty;
    private StopAdapter adapter;
    private int queryGeneration;
    private boolean showingNearby;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!new Prefs(this).hasLogin() || !TransitDb.get(this).hasStops()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        search = findViewById(R.id.search);
        heading = findViewById(R.id.heading);
        empty = findViewById(R.id.empty);
        ListView list = findViewById(R.id.list);
        adapter = new StopAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openStop(adapter.getItem(position)));

        findViewById(R.id.near_me).setOnClickListener(v -> findNearby());
        findViewById(R.id.all_routes).setOnClickListener(v ->
                startActivity(new Intent(this, RoutesActivity.class)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                showingNearby = false;
                refresh();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && !showingNearby) {
            refresh();
        }
    }

    private void refresh() {
        String query = search.getText().toString();
        int generation = ++queryGeneration;
        TransitDb db = TransitDb.get(this);
        Ui.IO.execute(() -> {
            List<TransitDb.Stop> stops = query.trim().isEmpty() ? db.favorites() : db.searchStops(query);
            runOnUiThread(() -> {
                if (generation != queryGeneration) {
                    return;
                }
                if (query.trim().isEmpty()) {
                    show("⭐ Favourite stops", stops,
                            "No favourites yet. Search for a stop or tap 📍 Near me, then tap ☆ on a stop to save it here.");
                } else {
                    show("Stops matching \"" + query.trim() + "\"", stops, "No stops found.");
                }
            });
        });
    }

    private void show(String title, List<TransitDb.Stop> stops, String emptyText) {
        heading.setText(title);
        adapter.clear();
        adapter.addAll(stops);
        empty.setText(emptyText);
        empty.setVisibility(stops.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openStop(TransitDb.Stop stop) {
        startActivity(new Intent(this, StopActivity.class).putExtra(StopActivity.EXTRA_STOP_ID, stop.id));
    }

    // --- Stops near me -----------------------------------------------------------------------

    private void findNearby() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_REQUEST);
            return;
        }
        showingNearby = true;
        heading.setText("📍 Finding your location…");
        adapter.clear();
        empty.setVisibility(View.GONE);

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            } catch (SecurityException ignored) {
                // Permission was only granted for a coarser provider.
            }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 2 * 60_000) {
            showNearby(best);
            return;
        }
        String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
        if (!lm.isProviderEnabled(provider)) {
            if (best != null) {
                showNearby(best);
            } else {
                heading.setText("📍 Stops near me");
                empty.setText("Turn on Location in your phone's settings, then try again.");
                empty.setVisibility(View.VISIBLE);
            }
            return;
        }
        Location fallback = best;
        try {
            lm.requestLocationUpdates(provider, 0, 0, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    lm.removeUpdates(this);
                    if (showingNearby) {
                        showNearby(location);
                    }
                }

                @Override public void onStatusChanged(String p, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            if (fallback != null) {
                showNearby(fallback);
            }
        }
    }

    private void showNearby(Location location) {
        TransitDb db = TransitDb.get(this);
        int generation = ++queryGeneration;
        Ui.IO.execute(() -> {
            List<TransitDb.Stop> stops = db.nearbyStops(location.getLatitude(), location.getLongitude(), 40);
            runOnUiThread(() -> {
                if (generation == queryGeneration) {
                    show("📍 Stops near me", stops, "No NJ Transit bus stops within about 10 miles.");
                }
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != LOCATION_REQUEST) {
            return;
        }
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                findNearby();
                return;
            }
        }
        heading.setText("📍 Stops near me");
        empty.setText("Location permission is needed to find stops near you. You can still search by name or stop number.");
        empty.setVisibility(View.VISIBLE);
    }

    // --- Menu --------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.update_data) {
            startActivity(new Intent(this, SetupActivity.class).putExtra(SetupActivity.EXTRA_REDOWNLOAD, true));
            return true;
        }
        if (item.getItemId() == R.id.sign_out) {
            new AlertDialog.Builder(this)
                    .setMessage("Remove your NJ Transit login from this phone? Your favourites and bus log are kept.")
                    .setPositiveButton("Sign out", (d, w) -> {
                        new Prefs(this).signOut();
                        startActivity(new Intent(this, SetupActivity.class));
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static final class StopAdapter extends ArrayAdapter<TransitDb.Stop> {

        StopAdapter(Context context) {
            super(context, 0, new ArrayList<>());
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.item_stop, parent, false);
            }
            TransitDb.Stop stop = getItem(position);
            ((TextView) view.findViewById(R.id.name)).setText(stop.name);
            StringBuilder detail = new StringBuilder();
            if (stop.code != null && !stop.code.isEmpty()) {
                detail.append("Stop #").append(stop.code);
            }
            if (!Double.isNaN(stop.milesAway)) {
                if (detail.length() > 0) {
                    detail.append(" · ");
                }
                detail.append(Ui.miles(stop.milesAway).replace(" away", ""));
            }
            ((TextView) view.findViewById(R.id.detail)).setText(detail);
            return view;
        }
    }
}
