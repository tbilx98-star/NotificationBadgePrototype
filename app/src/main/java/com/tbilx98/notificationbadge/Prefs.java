package com.tbilx98.notificationbadge;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class Prefs {

    private static final String P =
            "badge_prefs";

    private static SharedPreferences p(
            Context c) {

        return c.getSharedPreferences(
                P,
                Context.MODE_PRIVATE
        );
    }

    public static Set<String> getSelected(
            Context c) {

        return new HashSet<>(
                p(c).getStringSet(
                        "selected",
                        new HashSet<String>()
                )
        );
    }

    public static void setSelected(
            Context c,
            Set<String> s) {

        p(c)
                .edit()
                .putStringSet(
                        "selected",
                        new HashSet<>(s)
                )
                .apply();
    }

    public static int getX(Context c) {
        return p(c).getInt("x", 300);
    }

    public static int getY(Context c) {
        return p(c).getInt("y", 200);
    }

    public static void setPosition(
            Context c,
            int x,
            int y) {

        p(c)
                .edit()
                .putInt("x", x)
                .putInt("y", y)
                .apply();
    }

    private Prefs() {
    }
}
