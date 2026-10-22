package com.nornorhub.ghostbus.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The parts of a GTFS-realtime feed this app uses. Field numbers follow
 * https://gtfs.org/realtime/reference/ (gtfs-realtime.proto).
 */
public final class GtfsRealtime {

    private static final int TRIP_CANCELED = 3;
    private static final int STOP_SKIPPED = 1;

    public long timestamp;
    public final List<TripUpdate> tripUpdates = new ArrayList<>();
    public final List<VehiclePosition> vehicles = new ArrayList<>();
    public final List<Alert> alerts = new ArrayList<>();

    public static final class TripUpdate {
        public String tripId;
        public String routeId;
        public boolean canceled;
        public String vehicleId;
        public long timestamp;
        public final List<StopTimeUpdate> stops = new ArrayList<>();
    }

    public static final class StopTimeUpdate {
        public int stopSequence;
        public String stopId;
        public long arrivalTime;
        public long departureTime;
        public Integer arrivalDelay;
        public Integer departureDelay;
        public boolean skipped;

        /** Predicted time in epoch seconds, or 0 if the feed gave none. */
        public long time() {
            return arrivalTime != 0 ? arrivalTime : departureTime;
        }

        /** Seconds behind schedule (negative when early), or null if unknown. */
        public Integer delay() {
            return arrivalDelay != null ? arrivalDelay : departureDelay;
        }
    }

    public static final class VehiclePosition {
        public String tripId;
        public String routeId;
        public String vehicleId;
        public String vehicleLabel;
        public boolean hasPosition;
        public double lat;
        public double lon;
        public float bearing;
        public long timestamp;
    }

    public static final class Alert {
        public final List<String> routeIds = new ArrayList<>();
        public final List<String> stopIds = new ArrayList<>();
        public String header;
        public String description;
        public long start;
        public long end;
    }

    /** Adds everything from {@code other} into this feed. */
    public void merge(GtfsRealtime other) {
        timestamp = Math.max(timestamp, other.timestamp);
        tripUpdates.addAll(other.tripUpdates);
        vehicles.addAll(other.vehicles);
        alerts.addAll(other.alerts);
    }

    public static GtfsRealtime parse(byte[] data) throws IOException {
        GtfsRealtime feed = new GtfsRealtime();
        ProtoReader r = new ProtoReader(data);
        while (r.next()) {
            if (r.field() == 1) {
                parseHeader(r.readMessage(), feed);
            } else if (r.field() == 2) {
                parseEntity(r.readMessage(), feed);
            } else {
                r.skip();
            }
        }
        return feed;
    }

    private static void parseHeader(ProtoReader r, GtfsRealtime feed) throws IOException {
        while (r.next()) {
            if (r.field() == 3) {
                feed.timestamp = r.readVarint();
            } else {
                r.skip();
            }
        }
    }

    private static void parseEntity(ProtoReader r, GtfsRealtime feed) throws IOException {
        while (r.next()) {
            switch (r.field()) {
                case 3:
                    feed.tripUpdates.add(parseTripUpdate(r.readMessage()));
                    break;
                case 4:
                    feed.vehicles.add(parseVehicle(r.readMessage()));
                    break;
                case 5:
                    feed.alerts.add(parseAlert(r.readMessage()));
                    break;
                default:
                    r.skip();
            }
        }
    }

    private static TripUpdate parseTripUpdate(ProtoReader r) throws IOException {
        TripUpdate tu = new TripUpdate();
        while (r.next()) {
            switch (r.field()) {
                case 1: {
                    Trip trip = parseTrip(r.readMessage());
                    tu.tripId = trip.tripId;
                    tu.routeId = trip.routeId;
                    tu.canceled = trip.scheduleRelationship == TRIP_CANCELED;
                    break;
                }
                case 2:
                    tu.stops.add(parseStopTimeUpdate(r.readMessage()));
                    break;
                case 3:
                    tu.vehicleId = parseVehicleDescriptor(r.readMessage())[0];
                    break;
                case 4:
                    tu.timestamp = r.readVarint();
                    break;
                default:
                    r.skip();
            }
        }
        return tu;
    }

    private static StopTimeUpdate parseStopTimeUpdate(ProtoReader r) throws IOException {
        StopTimeUpdate stu = new StopTimeUpdate();
        while (r.next()) {
            switch (r.field()) {
                case 1:
                    stu.stopSequence = r.readInt32();
                    break;
                case 2: {
                    long[] ev = parseStopTimeEvent(r.readMessage());
                    stu.arrivalTime = ev[1];
                    stu.arrivalDelay = ev[0] == 1 ? (int) ev[2] : null;
                    break;
                }
                case 3: {
                    long[] ev = parseStopTimeEvent(r.readMessage());
                    stu.departureTime = ev[1];
                    stu.departureDelay = ev[0] == 1 ? (int) ev[2] : null;
                    break;
                }
                case 4:
                    stu.stopId = r.readString();
                    break;
                case 5:
                    stu.skipped = r.readInt32() == STOP_SKIPPED;
                    break;
                default:
                    r.skip();
            }
        }
        return stu;
    }

    /** Returns {hasDelay, time, delay}. */
    private static long[] parseStopTimeEvent(ProtoReader r) throws IOException {
        long[] ev = new long[3];
        while (r.next()) {
            if (r.field() == 1) {
                ev[0] = 1;
                ev[2] = r.readInt32();
            } else if (r.field() == 2) {
                ev[1] = r.readVarint();
            } else {
                r.skip();
            }
        }
        return ev;
    }

    private static VehiclePosition parseVehicle(ProtoReader r) throws IOException {
        VehiclePosition vp = new VehiclePosition();
        while (r.next()) {
            switch (r.field()) {
                case 1: {
                    Trip trip = parseTrip(r.readMessage());
                    vp.tripId = trip.tripId;
                    vp.routeId = trip.routeId;
                    break;
                }
                case 2:
                    parsePosition(r.readMessage(), vp);
                    break;
                case 5:
                    vp.timestamp = r.readVarint();
                    break;
                case 8: {
                    String[] d = parseVehicleDescriptor(r.readMessage());
                    vp.vehicleId = d[0];
                    vp.vehicleLabel = d[1];
                    break;
                }
                default:
                    r.skip();
            }
        }
        return vp;
    }

    private static void parsePosition(ProtoReader r, VehiclePosition vp) throws IOException {
        boolean lat = false;
        boolean lon = false;
        while (r.next()) {
            switch (r.field()) {
                case 1:
                    vp.lat = r.readFloat();
                    lat = true;
                    break;
                case 2:
                    vp.lon = r.readFloat();
                    lon = true;
                    break;
                case 3:
                    vp.bearing = r.readFloat();
                    break;
                default:
                    r.skip();
            }
        }
        vp.hasPosition = lat && lon && !(vp.lat == 0 && vp.lon == 0);
    }

    /** Returns {id, label}. */
    private static String[] parseVehicleDescriptor(ProtoReader r) throws IOException {
        String[] d = new String[2];
        while (r.next()) {
            if (r.field() == 1) {
                d[0] = r.readString();
            } else if (r.field() == 2) {
                d[1] = r.readString();
            } else {
                r.skip();
            }
        }
        return d;
    }

    private static final class Trip {
        String tripId;
        String routeId;
        int scheduleRelationship;
    }

    private static Trip parseTrip(ProtoReader r) throws IOException {
        Trip t = new Trip();
        while (r.next()) {
            switch (r.field()) {
                case 1:
                    t.tripId = r.readString();
                    break;
                case 4:
                    t.scheduleRelationship = r.readInt32();
                    break;
                case 5:
                    t.routeId = r.readString();
                    break;
                default:
                    r.skip();
            }
        }
        return t;
    }

    private static Alert parseAlert(ProtoReader r) throws IOException {
        Alert a = new Alert();
        while (r.next()) {
            switch (r.field()) {
                case 1:
                    if (a.start == 0 && a.end == 0) {
                        long[] period = parseTimeRange(r.readMessage());
                        a.start = period[0];
                        a.end = period[1];
                    } else {
                        r.skip();
                    }
                    break;
                case 5:
                    parseInformedEntity(r.readMessage(), a);
                    break;
                case 10:
                    a.header = parseTranslatedString(r.readMessage());
                    break;
                case 11:
                    a.description = parseTranslatedString(r.readMessage());
                    break;
                default:
                    r.skip();
            }
        }
        return a;
    }

    private static long[] parseTimeRange(ProtoReader r) throws IOException {
        long[] range = new long[2];
        while (r.next()) {
            if (r.field() == 1) {
                range[0] = r.readVarint();
            } else if (r.field() == 2) {
                range[1] = r.readVarint();
            } else {
                r.skip();
            }
        }
        return range;
    }

    private static void parseInformedEntity(ProtoReader r, Alert a) throws IOException {
        while (r.next()) {
            if (r.field() == 2) {
                a.routeIds.add(r.readString());
            } else if (r.field() == 5) {
                a.stopIds.add(r.readString());
            } else if (r.field() == 4) {
                String routeId = parseTrip(r.readMessage()).routeId;
                if (routeId != null) {
                    a.routeIds.add(routeId);
                }
            } else {
                r.skip();
            }
        }
    }

    /** Picks the English translation, or the first one if none is marked English. */
    private static String parseTranslatedString(ProtoReader r) throws IOException {
        String first = null;
        while (r.next()) {
            if (r.field() != 1) {
                r.skip();
                continue;
            }
            ProtoReader t = r.readMessage();
            String text = null;
            String lang = null;
            while (t.next()) {
                if (t.field() == 1) {
                    text = t.readString();
                } else if (t.field() == 2) {
                    lang = t.readString();
                } else {
                    t.skip();
                }
            }
            if (lang != null && lang.toLowerCase().startsWith("en")) {
                return text;
            }
            if (first == null) {
                first = text;
            }
        }
        return first;
    }
}
