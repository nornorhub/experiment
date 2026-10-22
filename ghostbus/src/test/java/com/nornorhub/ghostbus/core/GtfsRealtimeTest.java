package com.nornorhub.ghostbus.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class GtfsRealtimeTest {

    private static final long NOW = 1_700_000_000L;

    private GtfsRealtime feed;

    @Before
    public void setUp() throws IOException {
        feed = GtfsRealtime.parse(readResource("feed.pb"));
    }

    @Test
    public void parsesHeaderAndEntities() {
        assertEquals(NOW, feed.timestamp);
        assertEquals(8, feed.tripUpdates.size());
        assertEquals(3, feed.vehicles.size());
        assertEquals(1, feed.alerts.size());
    }

    @Test
    public void parsesTripUpdates() {
        GtfsRealtime.TripUpdate t1 = feed.tripUpdates.get(0);
        assertEquals("T1", t1.tripId);
        assertEquals("62", t1.routeId);
        assertEquals("V1", t1.vehicleId);
        assertFalse(t1.canceled);
        GtfsRealtime.StopTimeUpdate stu = t1.stops.get(0);
        assertEquals("S1", stu.stopId);
        assertEquals(7, stu.stopSequence);
        assertEquals(NOW + 300, stu.time());
        assertEquals(Integer.valueOf(120), stu.delay());

        assertTrue(feed.tripUpdates.get(3).canceled);
        assertTrue(feed.tripUpdates.get(5).stops.get(0).skipped);
        assertNull(feed.tripUpdates.get(1).stops.get(0).delay());
        assertEquals(NOW + 900, feed.tripUpdates.get(2).stops.get(0).time());
        assertEquals(Integer.valueOf(-60), feed.tripUpdates.get(6).stops.get(0).delay());
    }

    @Test
    public void parsesVehiclePositions() {
        GtfsRealtime.VehiclePosition v1 = feed.vehicles.get(0);
        assertEquals("T1", v1.tripId);
        assertEquals("62", v1.routeId);
        assertEquals("V1", v1.vehicleId);
        assertEquals("Bus V1", v1.vehicleLabel);
        assertTrue(v1.hasPosition);
        assertEquals(40.7357, v1.lat, 1e-4);
        assertEquals(-74.1724, v1.lon, 1e-4);
        assertEquals(90f, v1.bearing, 0f);
        assertEquals(NOW - 20, v1.timestamp);
        assertNull(feed.vehicles.get(2).tripId);
    }

    @Test
    public void parsesAlerts() {
        GtfsRealtime.Alert a = feed.alerts.get(0);
        assertEquals("Detour on Broad St", a.header);
        assertEquals("Buses use Main St ✓", a.description);
        assertEquals(List.of("62"), a.routeIds);
        assertEquals(List.of("S1"), a.stopIds);
        assertEquals(NOW - 3600, a.start);
        assertEquals(NOW + 3600, a.end);
    }

    @Test
    public void arrivalsFlagGhostAndStaleBuses() {
        List<Arrivals.Arrival> list = Arrivals.forStop(feed, "S1", 40.7357, -74.1724, NOW, 2 * 3600);

        // T5 is for another stop, T6 skips this stop, T8 is beyond the horizon.
        assertEquals(5, list.size());
        String[] order = {"T7", "T1", "T4", "T2", "T3"};
        for (int i = 0; i < order.length; i++) {
            assertEquals(order[i], list.get(i).tripId);
        }

        Arrivals.Arrival t7 = list.get(0);
        assertEquals(Arrivals.Status.LIVE, t7.status);
        assertEquals("V7", t7.vehicleId);
        assertEquals(5, t7.gpsAgeSeconds);

        Arrivals.Arrival t1 = list.get(1);
        assertEquals(Arrivals.Status.LIVE, t1.status);
        assertEquals(20, t1.gpsAgeSeconds);
        assertEquals(0.0, t1.milesAway, 0.01);

        assertEquals(Arrivals.Status.CANCELED, list.get(2).status);

        Arrivals.Arrival t2 = list.get(3);
        assertEquals(Arrivals.Status.NO_GPS, t2.status);
        assertTrue(Double.isNaN(t2.milesAway));
        assertEquals(-1, t2.gpsAgeSeconds);

        Arrivals.Arrival t3 = list.get(4);
        assertEquals(Arrivals.Status.STALE_GPS, t3.status);
        assertEquals(600, t3.gpsAgeSeconds);
        assertTrue(t3.milesAway > 1);
    }

    @Test
    public void mergeCombinesFeeds() throws IOException {
        GtfsRealtime other = GtfsRealtime.parse(readResource("feed.pb"));
        feed.merge(other);
        assertEquals(16, feed.tripUpdates.size());
        assertEquals(6, feed.vehicles.size());
    }

    @Test(expected = IOException.class)
    public void rejectsGarbage() throws IOException {
        GtfsRealtime.parse("{\"error\":\"bad token\"}".getBytes());
    }

    @Test
    public void milesBetweenNewarkAndNewYork() {
        // Newark Penn Station to NY Penn Station is about 9 miles in a straight line.
        double miles = Geo.milesBetween(40.7347, -74.1644, 40.7506, -73.9935);
        assertEquals(9.0, miles, 0.3);
    }

    static byte[] readResource(String name) throws IOException {
        try (InputStream in = GtfsRealtimeTest.class.getClassLoader().getResourceAsStream(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
