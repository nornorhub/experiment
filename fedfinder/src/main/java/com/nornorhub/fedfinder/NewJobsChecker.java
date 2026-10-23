package com.nornorhub.fedfinder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import com.nornorhub.fedfinder.core.Job;
import com.nornorhub.fedfinder.core.SearchQuery;
import com.nornorhub.fedfinder.core.SearchResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Checks saved searches for new jobs in the background a few times a day and notifies. */
public class NewJobsChecker extends JobService {

    private static final int JOB_ID = 1;
    private static final long INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static final String CHANNEL_ID = "new_jobs";

    /** Turns the background check on if any saved search wants notifications, off otherwise. */
    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (!JobsDb.get(context).anyNotifying()) {
            scheduler.cancel(JOB_ID);
            return;
        }
        if (scheduler.getPendingJob(JOB_ID) != null) {
            return;
        }
        scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, NewJobsChecker.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(INTERVAL_MS)
                .setPersisted(true)
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Context context = getApplicationContext();
        new Thread(() -> {
            boolean retry = false;
            try {
                checkAll(context);
            } catch (IOException e) {
                retry = !(e instanceof UsaJobsApi.AuthException);
            }
            jobFinished(params, retry);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    static void checkAll(Context context) throws IOException {
        Prefs prefs = new Prefs(context);
        if (!prefs.hasKey()) {
            return;
        }
        UsaJobsApi api = new UsaJobsApi(prefs);
        for (JobsDb.SavedSearch s : JobsDb.get(context).searches()) {
            if (s.notify) {
                check(context, api, s);
            }
        }
    }

    /** Checks one search. The first check only remembers what's there, so it never notifies. */
    static int check(Context context, UsaJobsApi api, JobsDb.SavedSearch search) throws IOException {
        SearchQuery q = search.query.copy();
        q.sort = SearchQuery.Sort.NEWEST;
        q.page = 1;
        q.resultsPerPage = 100;
        SearchResponse response = api.search(q);
        List<String> ids = new ArrayList<>();
        for (Job j : response.jobs) {
            ids.add(j.id);
        }
        int fresh = JobsDb.get(context).recordCheck(search.id, ids);
        if (fresh > 0 && search.notify) {
            notifyNewJobs(context, search, fresh);
        }
        return fresh;
    }

    private static void notifyNewJobs(Context context, JobsDb.SavedSearch search, int count) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "New jobs for saved searches", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, ResultsActivity.class)
                .putExtra(ResultsActivity.EXTRA_SAVED_SEARCH_ID, search.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, (int) search.id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(count + (count == 1 ? " new job: " : " new jobs: ") + search.name)
                .setContentText(search.query.describe())
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify((int) search.id, n);
    }
}
