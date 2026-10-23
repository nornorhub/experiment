package com.nornorhub.fedfinder.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One USAJOBS listing (a SearchResultItem's MatchedObjectDescriptor). */
public final class Job {

    public String id;
    public String title;
    public String organization;
    public String department;
    public String locationDisplay;
    public final List<String> locations = new ArrayList<>();
    public String url;
    public String applyUrl;
    public double minPay;
    public double maxPay;
    /** USAJOBS RateIntervalCode, e.g. "PA" (per year), "PH" (per hour), "WC" (unpaid). */
    public String payInterval;
    public String payIntervalName;
    /** Pay plan, e.g. "GS". */
    public String payPlan;
    public String lowGrade;
    public String highGrade;
    public String schedule;
    public String appointmentType;
    /** yyyy-MM-dd, or null. */
    public String openDate;
    public String closeDate;
    public String summary;
    public String duties;
    public String qualifications;
    public String education;
    public String requirements;
    public String howToApply;
    public String whoMayApply;
    public final List<String> hiringPaths = new ArrayList<>();
    public boolean telework;
    public boolean remote;
    public String openings;
    public String securityClearance;

    /** The descriptor this was parsed from, so a saved job can be re-parsed later. */
    public String rawJson;

    public static Job fromSearchItem(JSONObject item) throws JSONException {
        JSONObject d = item.getJSONObject("MatchedObjectDescriptor");
        Job job = fromDescriptor(d);
        String id = item.optString("MatchedObjectId", "");
        if (!id.isEmpty()) {
            job.id = id;
        }
        return job;
    }

    public static Job fromDescriptor(JSONObject d) {
        Job j = new Job();
        j.rawJson = d.toString();
        j.id = text(d, "PositionID");
        j.title = text(d, "PositionTitle");
        j.organization = text(d, "OrganizationName");
        j.department = text(d, "DepartmentName");
        j.locationDisplay = text(d, "PositionLocationDisplay");
        JSONArray locs = d.optJSONArray("PositionLocation");
        if (locs != null) {
            for (int i = 0; i < locs.length(); i++) {
                JSONObject l = locs.optJSONObject(i);
                if (l != null && !text(l, "LocationName").isEmpty()) {
                    j.locations.add(text(l, "LocationName"));
                }
            }
        }
        if (j.locationDisplay.isEmpty() && !j.locations.isEmpty()) {
            j.locationDisplay = j.locations.size() == 1 ? j.locations.get(0) : j.locations.size() + " locations";
        }
        j.url = text(d, "PositionURI");
        JSONArray apply = d.optJSONArray("ApplyURI");
        j.applyUrl = apply != null && apply.length() > 0 ? apply.optString(0) : j.url;

        JSONObject pay = first(d, "PositionRemuneration");
        if (pay != null) {
            j.minPay = number(pay, "MinimumRange");
            j.maxPay = number(pay, "MaximumRange");
            j.payInterval = text(pay, "RateIntervalCode");
            j.payIntervalName = text(pay, "Description");
        }
        JSONObject grade = first(d, "JobGrade");
        j.payPlan = grade != null ? text(grade, "Code") : "";
        JSONObject schedule = first(d, "PositionSchedule");
        j.schedule = schedule != null ? text(schedule, "Name") : "";
        JSONObject offering = first(d, "PositionOfferingType");
        j.appointmentType = offering != null ? text(offering, "Name") : "";
        j.openDate = date(text(d, "PublicationStartDate"));
        j.closeDate = date(text(d, "ApplicationCloseDate"));
        j.qualifications = text(d, "QualificationSummary");

        JSONObject details = d.optJSONObject("UserArea") != null
                ? d.optJSONObject("UserArea").optJSONObject("Details") : null;
        if (details == null) {
            details = new JSONObject();
        }
        j.summary = text(details, "JobSummary");
        j.duties = joinArray(details.optJSONArray("MajorDuties"));
        j.education = text(details, "Education");
        j.requirements = text(details, "Requirements");
        j.howToApply = text(details, "HowToApply");
        JSONObject who = details.optJSONObject("WhoMayApply");
        j.whoMayApply = who != null ? text(who, "Name") : "";
        JSONArray paths = details.optJSONArray("HiringPath");
        if (paths != null) {
            for (int i = 0; i < paths.length(); i++) {
                j.hiringPaths.add(paths.optString(i));
            }
        }
        j.lowGrade = text(details, "LowGrade");
        j.highGrade = text(details, "HighGrade");
        j.telework = bool(details, "TeleworkEligible");
        j.remote = bool(details, "RemoteIndicator");
        j.openings = text(details, "TotalOpenings");
        j.securityClearance = text(details, "SecurityClearance");
        return j;
    }

    public boolean isInternship() {
        return hiringPaths.contains("student")
                || title.toLowerCase(Locale.US).contains("intern")
                || appointmentType.toLowerCase(Locale.US).contains("intern");
    }

    /** "GS-11", "GS-9–12", or "" when unknown. */
    public String gradeLabel() {
        String low = stripZeros(lowGrade);
        String high = stripZeros(highGrade);
        if (low.isEmpty() && high.isEmpty()) {
            return "";
        }
        String plan = payPlan.isEmpty() ? "" : payPlan + "-";
        if (low.isEmpty() || high.isEmpty() || low.equals(high)) {
            return plan + (low.isEmpty() ? high : low);
        }
        return plan + low + "–" + high;
    }

    private static String stripZeros(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        while (t.length() > 1 && t.charAt(0) == '0') {
            t = t.substring(1);
        }
        return t;
    }

    private static JSONObject first(JSONObject o, String key) {
        JSONArray a = o.optJSONArray(key);
        return a != null && a.length() > 0 ? a.optJSONObject(0) : null;
    }

    private static String text(JSONObject o, String key) {
        if (o.isNull(key)) {
            return "";
        }
        Object v = o.opt(key);
        if (v instanceof JSONArray) {
            return joinArray((JSONArray) v);
        }
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static boolean bool(JSONObject o, String key) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static double number(JSONObject o, String key) {
        try {
            return Double.parseDouble(text(o, key).replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String joinArray(JSONArray a) {
        if (a == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (s.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /** "2026-11-05T23:59:59.9970" -> "2026-11-05". */
    private static String date(String s) {
        return s.length() >= 10 ? s.substring(0, 10) : null;
    }
}
