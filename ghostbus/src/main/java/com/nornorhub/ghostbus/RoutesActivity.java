package com.nornorhub.ghostbus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every NJ Transit bus route; tapping one shows all of its buses live on the map. */
public class RoutesActivity extends Activity {

    private final List<TransitDb.Route> all = new ArrayList<>();
    private RouteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routes);
        setTitle("All routes");
        ListView list = findViewById(R.id.list);
        adapter = new RouteAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                startActivity(new Intent(this, MapActivity.class)
                        .putExtra(MapActivity.EXTRA_ROUTE_ID, adapter.getItem(position).id)));

        EditText filter = findViewById(R.id.filter);
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applyFilter(s.toString());
            }
        });

        TransitDb db = TransitDb.get(this);
        Ui.IO.execute(() -> {
            List<TransitDb.Route> routes = db.routes();
            runOnUiThread(() -> {
                all.addAll(routes);
                applyFilter(filter.getText().toString());
            });
        });
    }

    private void applyFilter(String text) {
        String q = text.trim().toLowerCase(Locale.US);
        adapter.clear();
        for (TransitDb.Route r : all) {
            if (q.isEmpty() || r.label().toLowerCase(Locale.US).startsWith(q)
                    || r.longName.toLowerCase(Locale.US).contains(q)) {
                adapter.add(r);
            }
        }
    }

    private static final class RouteAdapter extends ArrayAdapter<TransitDb.Route> {

        RouteAdapter(Context context) {
            super(context, 0, new ArrayList<>());
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.item_route, parent, false);
            }
            TransitDb.Route r = getItem(position);
            Ui.routeBadge(view.findViewById(R.id.route), r, r.id);
            ((TextView) view.findViewById(R.id.name)).setText(r.longName);
            return view;
        }
    }
}
