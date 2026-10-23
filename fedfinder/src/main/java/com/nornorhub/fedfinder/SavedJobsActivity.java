package com.nornorhub.fedfinder;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import com.nornorhub.fedfinder.core.Job;
import java.time.LocalDate;
import java.util.List;

/** Saved jobs, soonest deadline first. */
public class SavedJobsActivity extends Activity {

    private Ui.JobAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setTitle("⭐ Saved jobs");
        empty = findViewById(R.id.empty);
        ListView list = findViewById(R.id.list);
        adapter = new Ui.JobAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> JobActivity.open(this, adapter.getItem(position)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<Job> jobs = JobsDb.get(this).savedJobs(LocalDate.now().toString());
        adapter.clear();
        adapter.addAll(jobs);
        empty.setText("No saved jobs yet. Open a job and tap ☆ Save to keep it here.");
        empty.setVisibility(jobs.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
