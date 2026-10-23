package com.nornorhub.fedfinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Saved searches with their new-job counts. */
public class SavedSearchesActivity extends Activity {

    private SearchAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setTitle("🔔 Saved searches");
        empty = findViewById(R.id.empty);
        ListView list = findViewById(R.id.list);
        adapter = new SearchAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                startActivity(new Intent(this, ResultsActivity.class)
                        .putExtra(ResultsActivity.EXTRA_SAVED_SEARCH_ID, adapter.getItem(position).id)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showOptions(adapter.getItem(position));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<JobsDb.SavedSearch> searches = JobsDb.get(this).searches();
        adapter.clear();
        adapter.addAll(searches);
        empty.setText("No saved searches yet. Run a search, then tap 🔔 Save search at the top to get notified about new jobs.");
        empty.setVisibility(searches.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showOptions(JobsDb.SavedSearch s) {
        String toggle = s.notify ? "🔕 Turn off notifications" : "🔔 Turn on notifications";
        new AlertDialog.Builder(this)
                .setTitle(s.name)
                .setItems(new String[] {toggle, "🗑️ Delete"}, (d, which) -> {
                    JobsDb db = JobsDb.get(this);
                    if (which == 0) {
                        db.setNotify(s.id, !s.notify);
                    } else {
                        db.deleteSearch(s.id);
                    }
                    NewJobsChecker.schedule(this);
                    refresh();
                })
                .show();
    }

    private static final class SearchAdapter extends ArrayAdapter<JobsDb.SavedSearch> {

        SearchAdapter(Context context) {
            super(context, 0, new ArrayList<>());
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.item_saved_search, parent, false);
            }
            JobsDb.SavedSearch s = getItem(position);
            ((TextView) view.findViewById(R.id.name)).setText((s.notify ? "🔔 " : "🔕 ") + s.name);
            ((TextView) view.findViewById(R.id.detail)).setText(s.query.describe());
            TextView badge = view.findViewById(R.id.badge);
            badge.setText(s.newCount + " new");
            badge.setVisibility(s.newCount > 0 ? View.VISIBLE : View.GONE);
            return view;
        }
    }
}
