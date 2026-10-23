package com.nornorhub.fedfinder;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.nornorhub.fedfinder.core.Format;
import com.nornorhub.fedfinder.core.Job;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Ui {

    static final ExecutorService IO = Executors.newCachedThreadPool();

    static final int NAVY = 0xFF112E51;
    static final int URGENT = 0xFFD84315;
    static final int MUTED = 0xFF6B6B6B;

    private Ui() {}

    /** USAJOBS text fields sometimes contain HTML; show it as formatted text. */
    static CharSequence rich(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains("<") && s.contains(">")) {
            return Html.fromHtml(s.replace("\n", "<br>"), Html.FROM_HTML_MODE_COMPACT);
        }
        return s;
    }

    static String tags(Job job) {
        List<String> tags = new ArrayList<>();
        if (job.isInternship()) {
            tags.add("🎓 Internship");
        }
        if (job.hiringPaths.contains("graduates")) {
            tags.add("🎓 Recent grads");
        }
        if (job.remote) {
            tags.add("🏠 Remote");
        } else if (job.telework) {
            tags.add("💻 Telework");
        }
        return String.join("  ", tags);
    }

    /** Job cards for search results and saved jobs. */
    static final class JobAdapter extends ArrayAdapter<Job> {

        JobAdapter(Context context) {
            super(context, 0, new ArrayList<>());
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.item_job, parent, false);
            }
            Job job = getItem(position);
            LocalDate today = LocalDate.now();
            ((TextView) view.findViewById(R.id.title)).setText(job.title);
            ((TextView) view.findViewById(R.id.agency)).setText(job.organization.isEmpty() ? job.department : job.organization);
            ((TextView) view.findViewById(R.id.location)).setText("📍 " + job.locationDisplay);
            String grade = job.gradeLabel();
            ((TextView) view.findViewById(R.id.pay)).setText(Format.pay(job) + (grade.isEmpty() ? "" : " · " + grade));

            TextView tags = view.findViewById(R.id.tags);
            String t = tags(job);
            tags.setText(t);
            tags.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);

            TextView deadline = view.findViewById(R.id.deadline);
            deadline.setText(Format.deadline(job, today));
            Long days = Format.daysLeft(job, today);
            deadline.setTextColor(days != null && days <= 7 ? URGENT : MUTED);
            view.setAlpha(days != null && days < 0 ? 0.5f : 1f);
            return view;
        }
    }
}
