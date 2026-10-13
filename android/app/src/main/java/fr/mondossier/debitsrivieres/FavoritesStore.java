package fr.mondossier.debitsrivieres;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class FavoritesStore {

    private static final String PREFS = "debits_favoris";
    private static final String KEY = "stations";

    private FavoritesStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void add(Context c, HubEauClient.Station s) {
        List<HubEauClient.Station> all = list(c);
        for (HubEauClient.Station e : all) {
            if (e.code.equals(s.code)) return;
        }
        all.add(s);
        save(c, all);
    }

    public static void remove(Context c, String code) {
        List<HubEauClient.Station> all = list(c);
        List<HubEauClient.Station> out = new ArrayList<>();
        for (HubEauClient.Station e : all) {
            if (!e.code.equals(code)) out.add(e);
        }
        save(c, out);
    }

    public static boolean isFavorite(Context c, String code) {
        for (HubEauClient.Station e : list(c)) {
            if (e.code.equals(code)) return true;
        }
        return false;
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
                        o.optString("coursEau", null)));
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
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply();
    }
}
