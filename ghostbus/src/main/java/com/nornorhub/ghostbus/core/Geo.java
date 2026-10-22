package com.nornorhub.ghostbus.core;

public final class Geo {

    private static final double EARTH_RADIUS_MILES = 3958.8;

    private Geo() {}

    public static double milesBetween(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_MILES * Math.asin(Math.sqrt(a));
    }
}
