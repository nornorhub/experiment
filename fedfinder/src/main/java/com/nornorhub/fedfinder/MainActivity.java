package com.nornorhub.fedfinder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import com.nornorhub.fedfinder.core.SearchQuery;
import org.json.JSONException;

/** The search form. */
public class MainActivity extends Activity {

    private static final int[] RADII = {0, 10, 25, 50, 100};
    private static final int[] SCHEDULES = {
        SearchQuery.SCHEDULE_ANY, SearchQuery.SCHEDULE_FULL_TIME, SearchQuery.SCHEDULE_PART_TIME
    };

    private EditText keyword;
    private EditText location;
    private Spinner radius;
    private CheckBox remote;
    private CheckBox internships;
    private CheckBox recentGrads;
    private Spinner whoMayApply;
    private Spinner gradeLow;
    private Spinner gradeHigh;
    private EditText minSalary;
    private Spinner schedule;
    private Spinner sort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!new Prefs(this).hasKey()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        keyword = findViewById(R.id.keyword);
        location = findViewById(R.id.location);
        radius = findViewById(R.id.radius);
        remote = findViewById(R.id.remote);
        internships = findViewById(R.id.internships);
        recentGrads = findViewById(R.id.recent_grads);
        whoMayApply = findViewById(R.id.who_may_apply);
        gradeLow = findViewById(R.id.grade_low);
        gradeHigh = findViewById(R.id.grade_high);
        minSalary = findViewById(R.id.min_salary);
        schedule = findViewById(R.id.schedule);
        sort = findViewById(R.id.sort);

        setOptions(radius, "Any distance", "Within 10 mi", "Within 25 mi", "Within 50 mi", "Within 100 mi");
        String[] who = new String[SearchQuery.WhoMayApply.values().length];
        for (int i = 0; i < who.length; i++) {
            who[i] = "Who can apply: " + SearchQuery.WhoMayApply.values()[i].label;
        }
        setOptions(whoMayApply, who);
        String[] grades = new String[16];
        grades[0] = "Any";
        for (int i = 1; i <= 15; i++) {
            grades[i] = "GS-" + i;
        }
        setOptions(gradeLow, grades);
        setOptions(gradeHigh, grades);
        setOptions(schedule, "Full-time or part-time", "Full-time", "Part-time");
        String[] sorts = new String[SearchQuery.Sort.values().length];
        for (int i = 0; i < sorts.length; i++) {
            sorts[i] = "Sort: " + SearchQuery.Sort.values()[i].label;
        }
        setOptions(sort, sorts);

        internships.setOnCheckedChangeListener((b, checked) -> updateWhoMayApply());
        recentGrads.setOnCheckedChangeListener((b, checked) -> updateWhoMayApply());
        fill(new Prefs(this).lastQuery());

        findViewById(R.id.search).setOnClickListener(v -> search());
        findViewById(R.id.saved_jobs).setOnClickListener(v -> startActivity(new Intent(this, SavedJobsActivity.class)));
        findViewById(R.id.saved_searches).setOnClickListener(v ->
                startActivity(new Intent(this, SavedSearchesActivity.class)));
        keyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        NewJobsChecker.schedule(this);
    }

    /** Internships and recent-graduate jobs have their own hiring paths, so "who can apply" doesn't apply. */
    private void updateWhoMayApply() {
        whoMayApply.setEnabled(!internships.isChecked() && !recentGrads.isChecked());
        whoMayApply.setAlpha(whoMayApply.isEnabled() ? 1f : 0.4f);
    }

    private void setOptions(Spinner spinner, String... options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void fill(SearchQuery q) {
        keyword.setText(q.keyword);
        location.setText(q.location);
        radius.setSelection(indexOf(RADII, q.radiusMiles));
        remote.setChecked(q.remoteOnly);
        internships.setChecked(q.internships);
        recentGrads.setChecked(q.recentGraduates);
        whoMayApply.setSelection(q.whoMayApply.ordinal());
        gradeLow.setSelection(q.gradeLow);
        gradeHigh.setSelection(q.gradeHigh);
        minSalary.setText(q.minSalary > 0 ? String.valueOf(q.minSalary) : "");
        schedule.setSelection(indexOf(SCHEDULES, q.schedule));
        sort.setSelection(q.sort.ordinal());
        updateWhoMayApply();
    }

    private SearchQuery read() {
        SearchQuery q = new SearchQuery();
        q.keyword = keyword.getText().toString();
        q.location = location.getText().toString();
        q.radiusMiles = RADII[radius.getSelectedItemPosition()];
        q.remoteOnly = remote.isChecked();
        q.internships = internships.isChecked();
        q.recentGraduates = recentGrads.isChecked();
        q.whoMayApply = SearchQuery.WhoMayApply.values()[whoMayApply.getSelectedItemPosition()];
        q.gradeLow = gradeLow.getSelectedItemPosition();
        q.gradeHigh = gradeHigh.getSelectedItemPosition();
        if (q.gradeLow > 0 && q.gradeHigh > 0 && q.gradeLow > q.gradeHigh) {
            int t = q.gradeLow;
            q.gradeLow = q.gradeHigh;
            q.gradeHigh = t;
        }
        try {
            q.minSalary = Integer.parseInt(minSalary.getText().toString().replace(",", "").trim());
        } catch (NumberFormatException e) {
            q.minSalary = 0;
        }
        q.schedule = SCHEDULES[schedule.getSelectedItemPosition()];
        q.sort = SearchQuery.Sort.values()[sort.getSelectedItemPosition()];
        return q;
    }

    private void search() {
        SearchQuery q = read();
        new Prefs(this).setLastQuery(q);
        try {
            startActivity(new Intent(this, ResultsActivity.class)
                    .putExtra(ResultsActivity.EXTRA_QUERY, q.toJson().toString()));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.reset) {
            fill(new SearchQuery());
            return true;
        }
        if (item.getItemId() == R.id.api_key) {
            startActivity(new Intent(this, SetupActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
