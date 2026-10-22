package com.nornorhub.ghostbus.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns a realtime feed into the upcoming buses at one stop, flagging the ones we can't see. */
public final class Arrivals {

    /** A bus whose GPS hasn't reported for this long is treated as possibly gone. */
    public static final long STALE_GPS_SECONDS = 180;

    public enum Status {
        /** GPS reported recently. */
        LIVE,
        /** The bus has a GPS position, but it hasn't updated in a while. */
        STALE_GPS,
        /** A prediction exists but no bus is reporting GPS for it: a possible ghost bus. */
        NO_GPS,
        CANCELED
    }

    public static final class Arrival {
        public String tripId;
        public String routeId;
        public String vehicleId;
        /** Predicted arrival, epoch seconds. */
        public long time;
        /** Seconds behind schedule according to NJ Transit, or null if unknown. */
        public Integer delay;
        public Status status;
        /** Seconds since the bus last reported GPS, or -1 if it never did. */
        public long gpsAgeSeconds = -1;
        /** Straight-line distance from the bus to the stop, or NaN if unknown. */
        public double milesAway = Double.NaN;
        public GtfsRealtime.VehiclePosition vehicle;
    }

    private Arrivals() {}

    public static List<Arrival> forStop(GtfsRealtime feed, String stopId, double stopLat, double stopLon,
                                        long nowSeconds, long horizonSeconds) {
        Map<String, GtfsRealtime.VehiclePosition> byTrip = new HashMap<>();
        Map<String, GtfsRealtime.VehiclePosition> byVehicle = new HashMap<>();
        for (GtfsRealtime.VehiclePosition vp : feed.vehicles) {
            if (vp.tripId != null) {
                byTrip.put(vp.tripId, vp);
            }
            if (vp.vehicleId != null) {
                byVehicle.put(vp.vehicleId, vp);
            }
        }

        List<Arrival> result = new ArrayList<>();
        for (GtfsRealtime.TripUpdate tu : feed.tripUpdates) {
            for (GtfsRealtime.StopTimeUpdate stu : tu.stops) {
                if (!stopId.equals(stu.stopId) || stu.skipped) {
                    continue;
                }
                long time = stu.time();
                if (time == 0 || time < nowSeconds - 60 || time > nowSeconds + horizonSeconds) {
                    continue;
                }
                Arrival a = new Arrival();
                a.tripId = tu.tripId;
                a.routeId = tu.routeId;
                a.time = time;
                a.delay = stu.delay();

                GtfsRealtime.VehiclePosition vp = tu.tripId != null ? byTrip.get(tu.tripId) : null;
                if (vp == null && tu.vehicleId != null) {
                    vp = byVehicle.get(tu.vehicleId);
                }
                a.vehicle = vp;
                a.vehicleId = vp != null && vp.vehicleId != null ? vp.vehicleId : tu.vehicleId;
                if (a.routeId == null && vp != null) {
                    a.routeId = vp.routeId;
                }

                if (tu.canceled) {
                    a.status = Status.CANCELED;
                } else if (vp == null || !vp.hasPosition) {
                    a.status = Status.NO_GPS;
                } else {
                    long reported = vp.timestamp != 0 ? vp.timestamp : feed.timestamp;
                    a.gpsAgeSeconds = Math.max(0, nowSeconds - reported);
                    a.milesAway = Geo.milesBetween(vp.lat, vp.lon, stopLat, stopLon);
                    a.status = a.gpsAgeSeconds > STALE_GPS_SECONDS ? Status.STALE_GPS : Status.LIVE;
                }
                result.add(a);
                break;
            }
        }
        Collections.sort(result, (x, y) -> Long.compare(x.time, y.time));
        return result;
    }
}
