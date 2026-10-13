package fr.mondossier.debitsrivieres;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;

public final class AnrWatchdog {

    private static final long CHECK_INTERVAL_MS = 2000;
    private static final long BLOCK_THRESHOLD_MS = 6000;

    private AnrWatchdog() {
    }

    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final AtomicLong lastBeat = new AtomicLong(System.currentTimeMillis());
        final SharedPreferences prefs = app.getSharedPreferences("debits_crash", Context.MODE_PRIVATE);

        final Runnable beat = new Runnable() {
            @Override
            public void run() {
                lastBeat.set(System.currentTimeMillis());
                main.postDelayed(this, CHECK_INTERVAL_MS / 2);
            }
        };
        main.post(beat);

        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(CHECK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastBeat.get() > BLOCK_THRESHOLD_MS) {
                    Thread ui = Looper.getMainLooper().getThread();
                    StackTraceElement[] stack = ui.getStackTrace();
                    if (stack.length > 0) {
                        boolean io = false;
                        for (StackTraceElement el : stack) {
                            String m = el.getMethodName();
                            if (m.contains("read") || m.contains("connect") || m.contains("execute")
                                    || m.contains("save") || m.contains("load")) {
                                io = true;
                                break;
                            }
                        }
                        StringWriter sw = new StringWriter();
                        sw.write("ANR détecté (thread principal bloqué >" + BLOCK_THRESHOLD_MS + "ms)\n");
                        sw.write(io ? "Cause probable : opération réseau/disque sur le thread principal\n\n"
                                : "Pile du thread principal :\n\n");
                        for (StackTraceElement el : stack) {
                            sw.write("    at " + el.toString() + "\n");
                        }
                        String trace = sw.toString();
                        if (prefs.getString("last_anr", "").isEmpty()) {
                            prefs.edit().putString("last_anr", trace).apply();
                        } else {
                            prefs.edit().putString("last_anr", trace)
                                    .putString("prev_anr", prefs.getString("last_anr", "")).apply();
                        }
                    }
                    lastBeat.set(System.currentTimeMillis());
                }
            }
        });
        t.setDaemon(true);
        t.setName("anr-watchdog");
        t.start();
    }
}
