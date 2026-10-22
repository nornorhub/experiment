package com.nornorhub.ghostbus;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.nornorhub.ghostbus.core.Csv;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Loads stops.txt, routes.txt and trips.txt from NJ Transit's GTFS zip into the local database. */
final class GtfsImporter {

    interface Progress {
        void update(String message);
    }

    private GtfsImporter() {}

    static void importZip(File zip, TransitDb transitDb, Progress progress) throws IOException {
        SQLiteDatabase db = transitDb.getWritableDatabase();
        int found = 0;
        db.beginTransaction();
        try {
            db.delete("stops", null, null);
            db.delete("routes", null, null);
            db.delete("trips", null, null);
            try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    String name = new File(entry.getName()).getName();
                    InputStream body = new NoClose(in);
                    switch (name) {
                        case "stops.txt":
                            progress.update("Loading stops…");
                            importStops(new Csv(body), db, progress);
                            found++;
                            break;
                        case "routes.txt":
                            progress.update("Loading routes…");
                            importRoutes(new Csv(body), db);
                            found++;
                            break;
                        case "trips.txt":
                            progress.update("Loading trips…");
                            importTrips(new Csv(body), db, progress);
                            found++;
                            break;
                        default:
                            break;
                    }
                }
            }
            if (found < 3) {
                throw new IOException("NJ Transit's download was missing stops, routes or trips.");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void importStops(Csv csv, SQLiteDatabase db, Progress progress) throws IOException {
        int id = csv.column("stop_id");
        int code = csv.column("stop_code");
        int name = csv.column("stop_name");
        int lat = csv.column("stop_lat");
        int lon = csv.column("stop_lon");
        ContentValues v = new ContentValues();
        int count = 0;
        List<String> row;
        while ((row = csv.next()) != null) {
            v.clear();
            v.put("stop_id", Csv.get(row, id));
            v.put("code", Csv.get(row, code));
            v.put("name", tidyName(Csv.get(row, name)));
            v.put("lat", parseDouble(Csv.get(row, lat)));
            v.put("lon", parseDouble(Csv.get(row, lon)));
            db.insertWithOnConflict("stops", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            if (++count % 5000 == 0) {
                progress.update("Loading stops… " + count);
            }
        }
    }

    private static void importRoutes(Csv csv, SQLiteDatabase db) throws IOException {
        int id = csv.column("route_id");
        int shortName = csv.column("route_short_name");
        int longName = csv.column("route_long_name");
        int color = csv.column("route_color");
        int textColor = csv.column("route_text_color");
        ContentValues v = new ContentValues();
        List<String> row;
        while ((row = csv.next()) != null) {
            v.clear();
            v.put("route_id", Csv.get(row, id));
            v.put("short_name", Csv.get(row, shortName));
            v.put("long_name", tidyName(Csv.get(row, longName)));
            v.put("color", Csv.get(row, color));
            v.put("text_color", Csv.get(row, textColor));
            db.insertWithOnConflict("routes", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private static void importTrips(Csv csv, SQLiteDatabase db, Progress progress) throws IOException {
        int id = csv.column("trip_id");
        int route = csv.column("route_id");
        int headsign = csv.column("trip_headsign");
        ContentValues v = new ContentValues();
        int count = 0;
        List<String> row;
        while ((row = csv.next()) != null) {
            v.clear();
            v.put("trip_id", Csv.get(row, id));
            v.put("route_id", Csv.get(row, route));
            v.put("headsign", tidyName(Csv.get(row, headsign)));
            db.insertWithOnConflict("trips", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            if (++count % 10000 == 0) {
                progress.update("Loading trips… " + count);
            }
        }
    }

    /** NJ Transit names are ALL CAPS; "MAIN ST AT 1ST AVE" reads better as "Main St At 1st Ave". */
    static String tidyName(String s) {
        if (s.isEmpty() || !s.equals(s.toUpperCase())) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        boolean startOfWord = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(startOfWord ? c : Character.toLowerCase(c));
            startOfWord = !Character.isLetterOrDigit(c) && c != '\'';
        }
        return out.toString();
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Lets a CSV reader finish one zip entry without closing the whole zip. */
    private static final class NoClose extends FilterInputStream {
        NoClose(InputStream in) {
            super(in);
        }

        @Override
        public void close() {}
    }
}
