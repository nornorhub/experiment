package com.nornorhub.fedfinder;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.nornorhub.fedfinder.core.Format;
import com.nornorhub.fedfinder.core.Job;
import java.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;

/** The full listing, with Apply, Save and Share. */
public class JobActivity extends Activity {

    private static final String EXTRA_JOB_ID = "job_id";
    private static final String EXTRA_JOB_JSON = "job_json";

    private Job job;
    private Button save;

    static void open(Context context, Job job) {
        context.startActivity(new Intent(context, JobActivity.class)
                .putExtra(EXTRA_JOB_ID, job.id)
                .putExtra(EXTRA_JOB_JSON, job.rawJson));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            job = Job.fromDescriptor(new JSONObject(getIntent().getStringExtra(EXTRA_JOB_JSON)));
            job.id = getIntent().getStringExtra(EXTRA_JOB_ID);
        } catch (JSONException | NullPointerException e) {
            finish();
            return;
        }
        setContentView(R.layout.activity_job);
        setTitle(job.organization.isEmpty() ? "Job" : job.organization);

        ((TextView) findViewById(R.id.title)).setText(job.title);
        ((TextView) findViewById(R.id.agency)).setText(
                job.department.isEmpty() || job.department.equals(job.organization)
                        ? job.organization : job.organization + "\n" + job.department);
        TextView deadline = findViewById(R.id.deadline);
        LocalDate today = LocalDate.now();
        deadline.setText(Format.deadline(job, today) + (job.closeDate != null ? "  (" + job.closeDate + ")" : ""));
        Long days = Format.daysLeft(job, today);
        deadline.setTextColor(days != null && days <= 7 ? Ui.URGENT : Ui.MUTED);
        TextView tags = findViewById(R.id.tags);
        String t = Ui.tags(job);
        tags.setText(t);
        tags.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);

        LinearLayout facts = findViewById(R.id.facts);
        fact(facts, "💵 Pay", Format.pay(job));
        fact(facts, "📊 Grade", job.gradeLabel());
        fact(facts, "📍 Location", job.locations.size() > 1 ? String.join("\n", job.locations) : job.locationDisplay);
        fact(facts, "🕒 Schedule", job.schedule);
        fact(facts, "📄 Appointment", job.appointmentType);
        fact(facts, "👥 Who can apply", job.whoMayApply);
        fact(facts, "🏠 Remote / telework", job.remote ? "Remote job" : job.telework ? "Telework eligible" : "");
        fact(facts, "🔢 Openings", job.openings);
        fact(facts, "🔒 Clearance", job.securityClearance);
        fact(facts, "📅 Posted", job.openDate);

        LinearLayout sections = findViewById(R.id.sections);
        section(sections, "Summary", job.summary);
        section(sections, "Duties", job.duties);
        section(sections, "Qualifications", job.qualifications);
        section(sections, "Education", job.education);
        section(sections, "Requirements", job.requirements);
        section(sections, "How to apply", job.howToApply);

        findViewById(R.id.apply).setOnClickListener(v -> openUrl(job.applyUrl.isEmpty() ? job.url : job.applyUrl));
        findViewById(R.id.view_full).setOnClickListener(v -> openUrl(job.url));
        save = findViewById(R.id.save);
        updateSaveButton();
        save.setOnClickListener(v -> {
            JobsDb db = JobsDb.get(this);
            if (db.isSaved(job.id)) {
                db.unsaveJob(job.id);
            } else {
                db.saveJob(job);
                Toast.makeText(this, "Saved. Find it under ⭐ Saved jobs.", Toast.LENGTH_SHORT).show();
            }
            updateSaveButton();
        });
        findViewById(R.id.share).setOnClickListener(v -> startActivity(Intent.createChooser(
                new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, job.title)
                        .putExtra(Intent.EXTRA_TEXT, job.title + " (" + job.organization + ")\n" + job.url),
                "Share job")));
    }

    private void updateSaveButton() {
        save.setText(JobsDb.get(this).isSaved(job.id) ? "★ Saved" : "☆ Save");
    }

    private void fact(LinearLayout parent, String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        View row = LayoutInflater.from(this).inflate(R.layout.item_fact, parent, false);
        ((TextView) row.findViewById(R.id.label)).setText(label);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        parent.addView(row);
    }

    private void section(LinearLayout parent, String heading, String body) {
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        View s = LayoutInflater.from(this).inflate(R.layout.item_section, parent, false);
        ((TextView) s.findViewById(R.id.heading)).setText(heading);
        ((TextView) s.findViewById(R.id.body)).setText(Ui.rich(body));
        parent.addView(s);
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser found to open " + url, Toast.LENGTH_LONG).show();
        }
    }
}
