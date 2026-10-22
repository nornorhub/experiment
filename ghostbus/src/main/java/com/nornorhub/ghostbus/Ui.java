package com.nornorhub.ghostbus;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.widget.TextView;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Ui {

    static final ExecutorService IO = Executors.newCachedThreadPool();

    static final int NJT_BLUE = 0xFF0B3D91;
    static final int GHOST = 0xFF8E24AA;
    static final int WARN = 0xFFE65100;
    static final int OK = 0xFF2E7D32;
    static final int MUTED = 0xFF757575;

    private Ui() {}

    static String clock(Context context, long epochSeconds) {
        return DateFormat.getTimeFormat(context).format(new Date(epochSeconds * 1000));
    }

    static String minutesUntil(long epochSeconds) {
        long minutes = Math.round((epochSeconds - System.currentTimeMillis() / 1000.0) / 60.0);
        return minutes <= 0 ? "Due" : minutes + " min";
    }

    static String age(long seconds) {
        if (seconds < 90) {
            return seconds + "s ago";
        }
        return Math.round(seconds / 60.0) + " min ago";
    }

    static String lateness(Integer seconds) {
        if (seconds == null) {
            return null;
        }
        long minutes = Math.round(seconds / 60.0);
        if (minutes == 0) {
            return "on time";
        }
        return minutes > 0 ? minutes + " min late" : -minutes + " min early";
    }

    static String miles(double miles) {
        return miles < 0.1 ? "at the stop" : String.format(java.util.Locale.US, "%.1f mi away", miles);
    }

    /** Styles a TextView as a coloured route badge using the route's GTFS colours when present. */
    static void routeBadge(TextView view, TransitDb.Route route, String fallbackLabel) {
        int bg = parseColor(route == null ? null : route.color, NJT_BLUE);
        int fg = parseColor(route == null ? null : route.textColor, Color.WHITE);
        if (bg == fg) {
            fg = Color.WHITE;
        }
        view.setText(route != null ? route.label() : fallbackLabel == null ? "?" : fallbackLabel);
        view.setTextColor(fg);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(bg);
        shape.setCornerRadius(dp(view.getContext(), 6));
        view.setBackground(shape);
    }

    static GradientDrawable dot(Context context, int color, int sizeDp, boolean square) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(square ? GradientDrawable.RECTANGLE : GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke((int) dp(context, 2), Color.WHITE);
        int px = (int) dp(context, sizeDp);
        d.setSize(px, px);
        return d;
    }

    static float dp(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    private static int parseColor(String hex, int fallback) {
        if (hex == null || hex.length() != 6) {
            return fallback;
        }
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
