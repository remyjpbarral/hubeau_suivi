package fr.mondossier.debitsrivieres;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

public final class DebitCache {

    public static final class Entry {
        public final List<HubEauClient.Observation> obs;
        public final long fetchedAt;

        Entry(List<HubEauClient.Observation> o, long t) {
            obs = o;
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

    public static void put(String code, long periodMs, boolean tempsReel, List<HubEauClient.Observation> obs) {
        CACHE.put(key(code, periodMs, tempsReel), new Entry(obs, System.currentTimeMillis()));
    }

    public static List<HubEauClient.Observation> getFresh(String code, long periodMs, boolean tempsReel) {
        Entry e = CACHE.get(key(code, periodMs, tempsReel));
        if (e != null && e.isFresh()) return e.obs;
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
                    List<HubEauClient.Observation> obs = tempsReel
                            ? HubEauClient.fetchDebitsTempsReel(s.code, now - periodMs, now)
                            : HubEauClient.fetchDebitsJournaliers(s.code, now - periodMs, now);
                    put(s.code, periodMs, tempsReel, obs);
                } catch (Exception ignored) {
                }
            }
        });
    }
}
