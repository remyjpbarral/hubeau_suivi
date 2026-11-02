package fr.mondossier.debitsrivieres;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

public final class DebitCache {

    public static final class Entry {
        public final HubEauClient.Series series;
        public final long fetchedAt;

        Entry(HubEauClient.Series s, long t) {
            series = s;
            fetchedAt = t;
        }

        public boolean isFresh() {
            return System.currentTimeMillis() - fetchedAt < 10 * 60_000L;
        }
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private DebitCache() {
    }

    private static String key(String code, long periodMs, boolean tempsReel) {
        return code + "|" + periodMs + "|" + tempsReel;
    }

    public static void put(String code, long periodMs, boolean tempsReel, HubEauClient.Series obs) {
        CACHE.put(key(code, periodMs, tempsReel), new Entry(obs, System.currentTimeMillis()));
    }

    public static HubEauClient.Series getFresh(String code, long periodMs, boolean tempsReel) {
        Entry e = CACHE.get(key(code, periodMs, tempsReel));
        if (e != null && e.isFresh()) return e.series;
        return null;
    }

    public static void warmup(List<HubEauClient.Station> stations, java.util.concurrent.ExecutorService executor, final long periodMs, final boolean tempsReel) {
        List<HubEauClient.Station> todo = new ArrayList<>();
        for (HubEauClient.Station s : stations) {
            if (getFresh(s.code, periodMs, tempsReel) == null) todo.add(s);
        }
        if (todo.isEmpty()) return;
        executor.execute(() -> {
            for (HubEauClient.Station s : todo) {
                try {
                    long now = System.currentTimeMillis();
                    HubEauClient.Series obs = tempsReel
                            ? HubEauClient.fetchSeriesTempsReel(s.code, now - periodMs, now)
                            : HubEauClient.fetchSeriesJournaliers(s.code, now - periodMs, now);
                    put(s.code, periodMs, tempsReel, obs);
                } catch (Exception ignored) {
                }
            }
        });
    }
}
