package com.nornorhub.fedfinder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.nornorhub.fedfinder.core.Job;
import com.nornorhub.fedfinder.core.SearchQuery;
import com.nornorhub.fedfinder.core.SearchResponse;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Search results, loading more pages as you scroll. */
public class ResultsActivity extends Activity {

    static final String EXTRA_QUERY = "query";
    static final String EXTRA_SAVED_SEARCH_ID = "saved_search_id";

    private SearchQuery query;
    private long savedSearchId = -1;
    private Ui.JobAdapter adapter;
    private TextView summary;
    private TextView footer;
    private final Set<String> shownIds = new HashSet<>();
    private int total = -1;
    private int nextPage = 1;
    private boolean loading;
    /** Set after a failed load so scrolling doesn't retry in a loop; tapping the footer retries. */
    private boolean failed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);
        summary = findViewById(R.id.summary);
        ListView list = findViewById(R.id.list);

        savedSearchId = getIntent().getLongExtra(EXTRA_SAVED_SEARCH_ID, -1);
        if (savedSearchId >= 0) {
            JobsDb.SavedSearch s = JobsDb.get(this).search(savedSearchId);
            if (s == null) {
                finish();
                return;
            }
            query = s.query;
            setTitle(s.name);
            JobsDb.get(this).clearNewCount(savedSearchId);
        } else {
            try {
                query = SearchQuery.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_QUERY)));
            } catch (JSONException | NullPointerException e) {
                finish();
                return;
            }
            setTitle("Results");
        }
        summary.setText(query.describe());

        footer = new TextView(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        footer.setPadding(pad, pad, pad, pad);
        footer.setTextColor(Ui.MUTED);
        footer.setLayoutParams(new AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));
        footer.setOnClickListener(v -> {
            failed = false;
            loadMore();
        });
        list.addFooterView(footer, null, false);

        adapter = new Ui.JobAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < adapter.getCount()) {
                JobActivity.open(this, adapter.getItem(position));
            }
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {}

            @Override
            public void onScroll(AbsListView view, int first, int visible, int totalItems) {
                if (first + visible >= totalItems - 3) {
                    loadMore();
                }
            }
        });
        loadMore();
    }

    private boolean hasMore() {
        return total < 0 || adapter.getCount() < total;
    }

    private void loadMore() {
        if (loading || failed || !hasMore()) {
            return;
        }
        loading = true;
        footer.setText("Loading jobs…");
        SearchQuery q = query.copy();
        q.page = nextPage;
        UsaJobsApi api = new UsaJobsApi(new Prefs(this));
        Ui.IO.execute(() -> {
            try {
                SearchResponse r = api.search(q);
                runOnUiThread(() -> {
                    loading = false;
                    total = r.totalCount;
                    nextPage++;
                    for (Job j : r.jobs) {
                        if (shownIds.add(j.id)) {
                            adapter.add(j);
                        }
                    }
                    if (r.jobs.isEmpty()) {
                        // USAJOBS ran out of pages before the reported total.
                        total = adapter.getCount();
                    }
                    summary.setText(String.format(Locale.US, "%,d %s · %s", total, total == 1 ? "job" : "jobs",
                            query.describe()));
                    if (total == 0) {
                        footer.setText(query.internships || query.recentGraduates
                                ? "No jobs match. Internships open in waves (often fall and spring), so try fewer filters or save this search to get notified."
                                : "No jobs match. Try fewer filters or a wider area.");
                    } else {
                        footer.setText(hasMore() ? "Loading more…" : "That's all of them.");
                    }
                });
            } catch (UsaJobsApi.AuthException e) {
                runOnUiThread(() -> {
                    loading = false;
                    failed = true;
                    footer.setText("Fix your USAJOBS API key, then tap to try again.");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, SetupActivity.class));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    failed = true;
                    footer.setText("Couldn't reach USAJOBS: " + e.getMessage() + "\nTap to try again.");
                });
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (savedSearchId < 0) {
            getMenuInflater().inflate(R.menu.results, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.save_search) {
            askToSaveSearch();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void askToSaveSearch() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setText(query.keyword.trim().isEmpty() ? (query.internships ? "Internships" : "My search") : query.keyword.trim());
        name.setSelectAllOnFocus(true);
        box.addView(name);
        CheckBox notify = new CheckBox(this);
        notify.setText("🔔 Notify me when new jobs are posted");
        notify.setChecked(true);
        box.addView(notify);

        new AlertDialog.Builder(this)
                .setTitle("Save this search")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    saveSearch(n.isEmpty() ? "My search" : n, notify.isChecked());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveSearch(String name, boolean notify) {
        try {
            long id = JobsDb.get(this).addSearch(name, query, notify);
            Toast.makeText(this, "Saved. Find it under 🔔 Saved searches.", Toast.LENGTH_SHORT).show();
            if (notify) {
                if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
                NewJobsChecker.schedule(this);
                // Remember what's there now, so only jobs posted after this count as new.
                UsaJobsApi api = new UsaJobsApi(new Prefs(this));
                Ui.IO.execute(() -> {
                    try {
                        JobsDb.SavedSearch s = JobsDb.get(this).search(id);
                        if (s != null) {
                            NewJobsChecker.check(getApplicationContext(), api, s);
                        }
                    } catch (Exception ignored) {
                        // The scheduled check will seed it instead.
                    }
                });
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Couldn't save: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Notifications are off, but you'll still see new-job counts under Saved searches.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            // Deadlines are relative to today.
            adapter.notifyDataSetChanged();
        }
    }
}
