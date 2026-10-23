package com.nornorhub.fedfinder.core;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A USAJOBS search, turned into query parameters for https://data.usajobs.gov/api/search.
 * Parameter names follow https://developer.usajobs.gov/api-reference/get-api-search.
 */
public final class SearchQuery {

    public enum WhoMayApply {
        ANYONE(null, "Anyone"),
        PUBLIC("public", "Open to the public"),
        VETERANS("vet", "Veterans"),
        FEDERAL("fed-competitive;fed-excepted;fed-internal-search", "Current federal employees");

        final String hiringPath;
        public final String label;

        WhoMayApply(String hiringPath, String label) {
            this.hiringPath = hiringPath;
            this.label = label;
        }
    }

    public enum Sort {
        RELEVANCE(null, null, "Best match"),
        NEWEST("opendate", "Desc", "Newest"),
        CLOSING("closedate", "Asc", "Closing soonest"),
        PAY("salary", "Desc", "Highest pay");

        final String field;
        final String direction;
        public final String label;

        Sort(String field, String direction, String label) {
            this.field = field;
            this.direction = direction;
            this.label = label;
        }
    }

    /** USAJOBS PositionScheduleTypeCode: 1 = full-time, 2 = part-time. 0 means any. */
    public static final int SCHEDULE_ANY = 0;
    public static final int SCHEDULE_FULL_TIME = 1;
    public static final int SCHEDULE_PART_TIME = 2;

    public String keyword = "";
    public String location = "";
    /** Miles around {@link #location}; 0 for USAJOBS' default. */
    public int radiusMiles;
    public boolean remoteOnly;
    /** Pathways internships and other jobs for current students. */
    public boolean internships;
    /** Pathways Recent Graduates jobs. */
    public boolean recentGraduates;
    public WhoMayApply whoMayApply = WhoMayApply.ANYONE;
    /** GS grade range, 1-15; 0 means no limit. */
    public int gradeLow;
    public int gradeHigh;
    public int minSalary;
    public int schedule = SCHEDULE_ANY;
    public Sort sort = Sort.RELEVANCE;
    /** Only jobs posted in the last N days (0-60); 0 means any time. */
    public int postedWithinDays;

    public int page = 1;
    public int resultsPerPage = 25;

    public SearchQuery copy() {
        try {
            return fromJson(toJson());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The hiring paths sent to USAJOBS. Internships and graduates take priority over "who may apply". */
    List<String> hiringPaths() {
        List<String> paths = new ArrayList<>();
        if (internships) {
            paths.add("student");
        }
        if (recentGraduates) {
            paths.add("graduates");
        }
        if (paths.isEmpty() && whoMayApply.hiringPath != null) {
            paths.add(whoMayApply.hiringPath);
        }
        return paths;
    }

    public Map<String, String> params() {
        Map<String, String> p = new LinkedHashMap<>();
        if (!keyword.trim().isEmpty()) {
            p.put("Keyword", keyword.trim());
        }
        if (!location.trim().isEmpty()) {
            p.put("LocationName", location.trim());
            if (radiusMiles > 0) {
                p.put("Radius", String.valueOf(radiusMiles));
            }
        }
        if (remoteOnly) {
            p.put("RemoteIndicator", "True");
        }
        List<String> paths = hiringPaths();
        if (!paths.isEmpty()) {
            p.put("HiringPath", String.join(";", paths));
        }
        if (gradeLow > 0) {
            p.put("PayGradeLow", twoDigits(gradeLow));
        }
        if (gradeHigh > 0) {
            p.put("PayGradeHigh", twoDigits(gradeHigh));
        }
        if (minSalary > 0) {
            p.put("RemunerationMinimumAmount", String.valueOf(minSalary));
        }
        if (schedule != SCHEDULE_ANY) {
            p.put("PositionScheduleTypeCode", String.valueOf(schedule));
        }
        if (sort.field != null) {
            p.put("SortField", sort.field);
            p.put("SortDirection", sort.direction);
        }
        if (postedWithinDays > 0) {
            p.put("DatePosted", String.valueOf(Math.min(60, postedWithinDays)));
        }
        p.put("Fields", "Full");
        p.put("ResultsPerPage", String.valueOf(resultsPerPage));
        p.put("Page", String.valueOf(page));
        return p;
    }

    public String toQueryString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params().entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(encode(e.getValue()));
        }
        return sb.toString();
    }

    /** A short human description, e.g. "nurse · Newark, NJ (25 mi) · Internships · GS-5+". */
    public String describe() {
        List<String> parts = new ArrayList<>();
        parts.add(keyword.trim().isEmpty() ? "All jobs" : "\"" + keyword.trim() + "\"");
        if (!location.trim().isEmpty()) {
            parts.add(location.trim() + (radiusMiles > 0 ? " (" + radiusMiles + " mi)" : ""));
        }
        if (remoteOnly) {
            parts.add("Remote");
        }
        if (internships) {
            parts.add("Internships");
        }
        if (recentGraduates) {
            parts.add("Recent grads");
        }
        if (!internships && !recentGraduates && whoMayApply != WhoMayApply.ANYONE) {
            parts.add(whoMayApply.label);
        }
        if (gradeLow > 0 && gradeHigh > 0) {
            parts.add(gradeLow == gradeHigh ? "GS-" + gradeLow : "GS-" + gradeLow + "–" + gradeHigh);
        } else if (gradeLow > 0) {
            parts.add("GS-" + gradeLow + "+");
        } else if (gradeHigh > 0) {
            parts.add("up to GS-" + gradeHigh);
        }
        if (minSalary > 0) {
            parts.add("$" + String.format(java.util.Locale.US, "%,d", minSalary) + "+");
        }
        if (schedule == SCHEDULE_FULL_TIME) {
            parts.add("Full-time");
        } else if (schedule == SCHEDULE_PART_TIME) {
            parts.add("Part-time");
        }
        return String.join(" · ", parts);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("keyword", keyword);
        o.put("location", location);
        o.put("radiusMiles", radiusMiles);
        o.put("remoteOnly", remoteOnly);
        o.put("internships", internships);
        o.put("recentGraduates", recentGraduates);
        o.put("whoMayApply", whoMayApply.name());
        o.put("gradeLow", gradeLow);
        o.put("gradeHigh", gradeHigh);
        o.put("minSalary", minSalary);
        o.put("schedule", schedule);
        o.put("sort", sort.name());
        o.put("postedWithinDays", postedWithinDays);
        return o;
    }

    public static SearchQuery fromJson(JSONObject o) {
        SearchQuery q = new SearchQuery();
        q.keyword = o.optString("keyword", "");
        q.location = o.optString("location", "");
        q.radiusMiles = o.optInt("radiusMiles");
        q.remoteOnly = o.optBoolean("remoteOnly");
        q.internships = o.optBoolean("internships");
        q.recentGraduates = o.optBoolean("recentGraduates");
        q.whoMayApply = enumOr(WhoMayApply.class, o.optString("whoMayApply"), WhoMayApply.ANYONE);
        q.gradeLow = o.optInt("gradeLow");
        q.gradeHigh = o.optInt("gradeHigh");
        q.minSalary = o.optInt("minSalary");
        q.schedule = o.optInt("schedule");
        q.sort = enumOr(Sort.class, o.optString("sort"), Sort.RELEVANCE);
        q.postedWithinDays = o.optInt("postedWithinDays");
        return q;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private static String twoDigits(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
