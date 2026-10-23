package com.nornorhub.fedfinder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.nornorhub.fedfinder.core.Job;
import com.nornorhub.fedfinder.core.SearchQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Saved jobs and saved searches. */
final class JobsDb extends SQLiteOpenHelper {

    /** How many job IDs to remember per saved search when looking for new ones. */
    private static final int MAX_SEEN = 1000;

    static final class SavedSearch {
        long id;
        String name;
        SearchQuery query;
        boolean notify;
        boolean seeded;
        Set<String> seen = new LinkedHashSet<>();
        int newCount;
        long lastChecked;
    }

    private static JobsDb instance;

    static synchronized JobsDb get(Context context) {
        if (instance == null) {
            instance = new JobsDb(context.getApplicationContext());
        }
        return instance;
    }

    private JobsDb(Context context) {
        super(context, "jobs.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE saved_jobs (id TEXT PRIMARY KEY, json TEXT, close_date TEXT, saved_at INTEGER)");
        db.execSQL("CREATE TABLE saved_searches (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, query TEXT, "
                + "notify INTEGER, seeded INTEGER, seen TEXT, new_count INTEGER, last_checked INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    // --- Saved jobs --------------------------------------------------------------------------

    boolean isSaved(String jobId) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM saved_jobs WHERE id = ?", new String[] {jobId})) {
            return c.moveToFirst();
        }
    }

    void saveJob(Job job) {
        ContentValues v = new ContentValues();
        v.put("id", job.id);
        v.put("json", job.rawJson);
        v.put("close_date", job.closeDate);
        v.put("saved_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("saved_jobs", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void unsaveJob(String jobId) {
        getWritableDatabase().delete("saved_jobs", "id = ?", new String[] {jobId});
    }

    /** Saved jobs, still-open ones first, soonest deadline first. */
    List<Job> savedJobs(String today) {
        List<Job> jobs = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, json FROM saved_jobs ORDER BY (close_date IS NULL OR close_date < ?), close_date",
                new String[] {today})) {
            while (c.moveToNext()) {
                try {
                    Job j = Job.fromDescriptor(new JSONObject(c.getString(1)));
                    j.id = c.getString(0);
                    jobs.add(j);
                } catch (JSONException ignored) {
                    // Skip a corrupt row rather than lose the whole list.
                }
            }
        }
        return jobs;
    }

    // --- Saved searches ----------------------------------------------------------------------

    long addSearch(String name, SearchQuery query, boolean notify) throws JSONException {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("query", query.toJson().toString());
        v.put("notify", notify ? 1 : 0);
        v.put("seeded", 0);
        v.put("seen", "");
        v.put("new_count", 0);
        v.put("last_checked", 0);
        return getWritableDatabase().insert("saved_searches", null, v);
    }

    List<SavedSearch> searches() {
        List<SavedSearch> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, query, notify, seeded, seen, new_count, last_checked FROM saved_searches ORDER BY id",
                null)) {
            while (c.moveToNext()) {
                list.add(read(c));
            }
        }
        return list;
    }

    SavedSearch search(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, query, notify, seeded, seen, new_count, last_checked FROM saved_searches WHERE id = ?",
                new String[] {String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    boolean anyNotifying() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM saved_searches WHERE notify = 1 LIMIT 1", null)) {
            return c.moveToFirst();
        }
    }

    void setNotify(long id, boolean notify) {
        ContentValues v = new ContentValues();
        v.put("notify", notify ? 1 : 0);
        update(id, v);
    }

    void clearNewCount(long id) {
        ContentValues v = new ContentValues();
        v.put("new_count", 0);
        update(id, v);
    }

    /** Records a check: remembers these job IDs and returns how many of them are new. */
    synchronized int recordCheck(long id, Collection<String> jobIds) {
        SavedSearch s = search(id);
        if (s == null) {
            return 0;
        }
        int fresh = 0;
        if (s.seeded) {
            for (String jobId : jobIds) {
                if (!s.seen.contains(jobId)) {
                    fresh++;
                }
            }
        }
        Set<String> seen = new LinkedHashSet<>(s.seen);
        seen.removeAll(jobIds);
        seen.addAll(jobIds);
        List<String> trimmed = new ArrayList<>(seen);
        if (trimmed.size() > MAX_SEEN) {
            trimmed = trimmed.subList(trimmed.size() - MAX_SEEN, trimmed.size());
        }
        ContentValues v = new ContentValues();
        v.put("seeded", 1);
        v.put("seen", String.join("\n", trimmed));
        v.put("new_count", s.newCount + fresh);
        v.put("last_checked", System.currentTimeMillis());
        update(id, v);
        return fresh;
    }

    void deleteSearch(long id) {
        getWritableDatabase().delete("saved_searches", "id = ?", new String[] {String.valueOf(id)});
    }

    private void update(long id, ContentValues v) {
        getWritableDatabase().update("saved_searches", v, "id = ?", new String[] {String.valueOf(id)});
    }

    private static SavedSearch read(Cursor c) {
        SavedSearch s = new SavedSearch();
        s.id = c.getLong(0);
        s.name = c.getString(1);
        try {
            s.query = SearchQuery.fromJson(new JSONObject(c.getString(2)));
        } catch (JSONException e) {
            s.query = new SearchQuery();
        }
        s.notify = c.getInt(3) == 1;
        s.seeded = c.getInt(4) == 1;
        String seen = c.getString(5);
        if (seen != null && !seen.isEmpty()) {
            s.seen.addAll(Arrays.asList(seen.split("\n")));
        }
        s.newCount = c.getInt(6);
        s.lastChecked = c.getLong(7);
        return s;
    }
}
