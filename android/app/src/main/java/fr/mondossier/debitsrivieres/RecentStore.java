package fr.mondossier.debitsrivieres;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RecentStore {

    private static final String PREFS = "debits_recents";
    private static final String KEY = "stations";
    private static final int CAP = 10;

    private RecentStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void add(Context c, HubEauClient.Station s) {
        List<HubEauClient.Station> all = list(c);
        List<HubEauClient.Station> out = new ArrayList<>();
        out.add(s);
        for (HubEauClient.Station e : all) {
            if (!e.code.equals(s.code) && out.size() < CAP) out.add(e);
        }
        save(c, out);
    }

    public static List<HubEauClient.Station> list(Context c) {
        List<HubEauClient.Station> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new HubEauClient.Station(
                        o.getString("code"),
                        o.getString("libelle"),
                        o.optString("coursEau", null),
                        o.has("lat") && !o.isNull("lat") ? o.optDouble("lat") : null,
                        o.has("lon") && !o.isNull("lon") ? o.optDouble("lon") : null));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void save(Context c, List<HubEauClient.Station> stations) {
        JSONArray arr = new JSONArray();
        for (HubEauClient.Station s : stations) {
            JSONObject o = new JSONObject();
            try {
                o.put("code", s.code);
                o.put("libelle", s.libelle);
                o.put("coursEau", s.coursEau == null ? "" : s.coursEau);
                if (s.lat != null) o.put("lat", s.lat);
                if (s.lon != null) o.put("lon", s.lon);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply();
    }
}
