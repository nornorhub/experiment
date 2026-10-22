package com.nornorhub.ghostbus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.nornorhub.ghostbus.core.Geo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local copy of NJ Transit's stops, routes and trips, plus the user's favourites and bus log. */
final class TransitDb extends SQLiteOpenHelper {

    static final class Stop {
        String id;
        String code;
        String name;
        double lat;
        double lon;
        double milesAway = Double.NaN;
    }

    static final class Route {
        String id;
        String shortName;
        String longName;
        String color;
        String textColor;

        String label() {
            return shortName.isEmpty() ? id : shortName;
        }
    }

    static final class Trip {
        String routeId;
        String headsign;
    }

    /** What the user has logged for one route at one stop. */
    static final class Reliability {
        int caught;
        int noShows;
        /** Average seconds late across caught buses with a known schedule, or null. */
        Integer avgLateSeconds;

        int total() {
            return caught + noShows;
        }
    }

    private static TransitDb instance;

    static synchronized TransitDb get(Context context) {
        if (instance == null) {
            instance = new TransitDb(context.getApplicationContext());
        }
        return instance;
    }

    private TransitDb(Context context) {
        super(context, "transit.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE stops (stop_id TEXT PRIMARY KEY, code TEXT, name TEXT, lat REAL, lon REAL)");
        db.execSQL("CREATE INDEX stops_code ON stops (code)");
        db.execSQL("CREATE INDEX stops_lat ON stops (lat)");
        db.execSQL("CREATE TABLE routes (route_id TEXT PRIMARY KEY, short_name TEXT, long_name TEXT, "
                + "color TEXT, text_color TEXT)");
        db.execSQL("CREATE TABLE trips (trip_id TEXT PRIMARY KEY, route_id TEXT, headsign TEXT)");
        db.execSQL("CREATE TABLE favorites (stop_id TEXT PRIMARY KEY, added INTEGER)");
        db.execSQL("CREATE TABLE observations (id INTEGER PRIMARY KEY AUTOINCREMENT, stop_id TEXT, "
                + "route_id TEXT, trip_id TEXT, caught INTEGER, late_seconds INTEGER, at INTEGER)");
        db.execSQL("CREATE INDEX observations_stop_route ON observations (stop_id, route_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    // --- Stops -------------------------------------------------------------------------------

    boolean hasStops() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM stops LIMIT 1", null)) {
            return c.moveToFirst();
        }
    }

    Stop stop(String stopId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT stop_id, code, name, lat, lon FROM stops WHERE stop_id = ?", new String[] {stopId})) {
            return c.moveToFirst() ? readStop(c) : null;
        }
    }

    /** Matches the stop number printed on the sign, or words in the stop name. */
    List<Stop> searchStops(String query) {
        query = query.trim();
        List<Stop> result = new ArrayList<>();
        if (query.isEmpty()) {
            return result;
        }
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();
        for (String word : query.split("\\s+")) {
            if (where.length() > 0) {
                where.append(" AND ");
            }
            where.append("name LIKE ?");
            args.add("%" + word + "%");
        }
        args.add(query);
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT stop_id, code, name, lat, lon FROM stops WHERE (" + where + ") OR code = ? "
                        + "ORDER BY name LIMIT 200",
                args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                result.add(readStop(c));
            }
        }
        return result;
    }

    List<Stop> nearbyStops(double lat, double lon, int limit) {
        List<Stop> result = new ArrayList<>();
        for (double radius = 0.01; radius <= 0.16 && result.size() < limit; radius *= 2) {
            result.clear();
            double lonRadius = radius / Math.cos(Math.toRadians(lat));
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT stop_id, code, name, lat, lon FROM stops "
                            + "WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
                    new String[] {
                        String.valueOf(lat - radius), String.valueOf(lat + radius),
                        String.valueOf(lon - lonRadius), String.valueOf(lon + lonRadius)
                    })) {
                while (c.moveToNext()) {
                    Stop s = readStop(c);
                    s.milesAway = Geo.milesBetween(lat, lon, s.lat, s.lon);
                    result.add(s);
                }
            }
        }
        Collections.sort(result, (a, b) -> Double.compare(a.milesAway, b.milesAway));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private static Stop readStop(Cursor c) {
        Stop s = new Stop();
        s.id = c.getString(0);
        s.code = c.getString(1);
        s.name = c.getString(2);
        s.lat = c.getDouble(3);
        s.lon = c.getDouble(4);
        return s;
    }

    // --- Routes and trips --------------------------------------------------------------------

    List<Route> routes() {
        List<Route> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT route_id, short_name, long_name, color, text_color FROM routes", null)) {
            while (c.moveToNext()) {
                result.add(readRoute(c));
            }
        }
        Collections.sort(result, (a, b) -> compareRouteNames(a.label(), b.label()));
        return result;
    }

    Route route(String routeId) {
        if (routeId == null) {
            return null;
        }
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT route_id, short_name, long_name, color, text_color FROM routes WHERE route_id = ?",
                new String[] {routeId})) {
            return c.moveToFirst() ? readRoute(c) : null;
        }
    }

    Trip trip(String tripId) {
        if (tripId == null) {
            return null;
        }
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT route_id, headsign FROM trips WHERE trip_id = ?", new String[] {tripId})) {
            if (!c.moveToFirst()) {
                return null;
            }
            Trip t = new Trip();
            t.routeId = c.getString(0);
            t.headsign = c.getString(1);
            return t;
        }
    }

    private static Route readRoute(Cursor c) {
        Route r = new Route();
        r.id = c.getString(0);
        r.shortName = c.getString(1) == null ? "" : c.getString(1);
        r.longName = c.getString(2) == null ? "" : c.getString(2);
        r.color = c.getString(3);
        r.textColor = c.getString(4);
        return r;
    }

    /** Sorts "2" before "10" and "10" before "10X". */
    static int compareRouteNames(String a, String b) {
        int na = leadingNumber(a);
        int nb = leadingNumber(b);
        if (na != nb) {
            return Integer.compare(na, nb);
        }
        return a.compareToIgnoreCase(b);
    }

    private static int leadingNumber(String s) {
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == 0 || i > 9) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(s.substring(0, i));
    }

    // --- Favourites --------------------------------------------------------------------------

    List<Stop> favorites() {
        List<Stop> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.stop_id, s.code, s.name, s.lat, s.lon FROM favorites f "
                        + "JOIN stops s ON s.stop_id = f.stop_id ORDER BY f.added", null)) {
            while (c.moveToNext()) {
                result.add(readStop(c));
            }
        }
        return result;
    }

    boolean isFavorite(String stopId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM favorites WHERE stop_id = ?", new String[] {stopId})) {
            return c.moveToFirst();
        }
    }

    void setFavorite(String stopId, boolean favorite) {
        if (favorite) {
            ContentValues v = new ContentValues();
            v.put("stop_id", stopId);
            v.put("added", System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            getWritableDatabase().delete("favorites", "stop_id = ?", new String[] {stopId});
        }
    }

    // --- The user's own log of buses ---------------------------------------------------------

    void logBus(String stopId, String routeId, String tripId, boolean caught, Integer lateSeconds) {
        ContentValues v = new ContentValues();
        v.put("stop_id", stopId);
        v.put("route_id", routeId);
        v.put("trip_id", tripId);
        v.put("caught", caught ? 1 : 0);
        if (lateSeconds != null) {
            v.put("late_seconds", lateSeconds);
        }
        v.put("at", System.currentTimeMillis());
        getWritableDatabase().insert("observations", null, v);
    }

    Reliability reliability(String stopId, String routeId) {
        Reliability r = new Reliability();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT SUM(caught), SUM(1 - caught), AVG(CASE WHEN caught = 1 THEN late_seconds END) "
                        + "FROM observations WHERE stop_id = ? AND route_id = ?",
                new String[] {stopId, routeId == null ? "" : routeId})) {
            if (c.moveToFirst()) {
                r.caught = c.getInt(0);
                r.noShows = c.getInt(1);
                r.avgLateSeconds = c.isNull(2) ? null : (int) Math.round(c.getDouble(2));
            }
        }
        return r;
    }
}
