package fr.mondossier.debitsrivieres;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class CrashLogger implements Thread.UncaughtExceptionHandler {

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Context context;

    private CrashLogger(Context c) {
        this.context = c.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context c) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashLogger(c));
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String trace = sw.toString();
            Log.e("DebitsRivieres", "CRASH", e);
            SharedPreferences p = context.getSharedPreferences("debits_crash", Context.MODE_PRIVATE);
            String prev = p.getString("last_crash", "");
            p.edit().putString("last_crash", trace).putString("prev_crash", prev).apply();
        } catch (Exception ignored) {
        }
        if (defaultHandler != null) defaultHandler.uncaughtException(t, e);
    }

    public static String last(Context c) {
        return c.getSharedPreferences("debits_crash", Context.MODE_PRIVATE).getString("last_crash", "");
    }

    public static String lastAnr(Context c) {
        return c.getSharedPreferences("debits_crash", Context.MODE_PRIVATE).getString("last_anr", "");
    }

    public static String fullReport(Context c) {
        StringBuilder sb = new StringBuilder();
        String anr = lastAnr(c);
        String crash = last(c);
        if (!anr.isEmpty()) {
            sb.append("=== DERNIER GEL (ANR) ===\n").append(anr).append("\n\n");
        }
        if (!crash.isEmpty()) {
            sb.append("=== DERNIER PLANTAGE ===\n").append(crash).append("\n\n");
        }
        if (sb.length() == 0) return "";
        sb.append("App: Débits Rivières | Android ").append(android.os.Build.VERSION.RELEASE)
          .append(" | ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL);
        return sb.toString();
    }
}
