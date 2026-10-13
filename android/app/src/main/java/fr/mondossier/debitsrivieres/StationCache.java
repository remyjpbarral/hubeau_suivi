package fr.mondossier.debitsrivieres;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StationCache {

    private static final String PREFS = "debits_cache";
    private static final String KEY = "stations";
    private static final String KEY_TS = "timestamp";

    private StationCache() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isFresh(Context c) {
        long ts = prefs(c).getLong(KEY_TS, 0);
        return System.currentTimeMillis() - ts < 7L * 24 * 3600_000L;
    }

    public static void save(Context c, List<HubEauClient.Station> stations) {
        JSONArray arr = new JSONArray();
        for (HubEauClient.Station s : stations) {
            JSONObject o = new JSONObject();
            try {
                o.put("code", s.code);
                o.put("libelle", s.libelle);
                o.put("coursEau", s.coursEau == null ? "" : s.coursEau);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs(c).edit()
                .putString(KEY, arr.toString())
                .putLong(KEY_TS, System.currentTimeMillis())
                .apply();
    }

    public static List<HubEauClient.Station> load(Context c) {
        List<HubEauClient.Station> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new HubEauClient.Station(
                        o.getString("code"),
                        o.getString("libelle"),
                        o.optString("coursEau", null)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static List<HubEauClient.Station> filter(List<HubEauClient.Station> all, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return all;
        List<HubEauClient.Station> startsWith = new ArrayList<>();
        List<HubEauClient.Station> contains = new ArrayList<>();
        for (HubEauClient.Station s : all) {
            String lib = normalize(s.libelle);
            String ce = normalize(s.coursEau);
            if (lib.startsWith(q) || ce.startsWith(q)) {
                startsWith.add(s);
            } else if (lib.contains(q) || ce.contains(q)) {
                contains.add(s);
            }
        }
        List<HubEauClient.Station> out = new ArrayList<>(startsWith);
        out.addAll(contains);
        if (out.size() > 30) return new ArrayList<>(out.subList(0, 30));
        return out;
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String n = s.toLowerCase(Locale.FRANCE)
                .replaceAll("[éèêë]", "e")
                .replaceAll("[àâä]", "a")
                .replaceAll("[ùûü]", "u")
                .replaceAll("[ôö]", "o")
                .replaceAll("[îï]", "i")
                .replaceAll("ç", "c")
                .replace("-", " ").replace("'", " ")
                .trim();
        return n.replaceAll("\\s+", " ");
    }
}
