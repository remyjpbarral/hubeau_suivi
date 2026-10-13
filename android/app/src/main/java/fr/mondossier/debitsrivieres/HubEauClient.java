package fr.mondossier.debitsrivieres;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class HubEauClient {

    private static final String BASE = "https://hubeau.eaufrance.fr/api/v1/hydrometrie";
    private static final String UA = "DebitsRivieres/1.0 (Android; suivi hydrometrique)";

    public static final class Station {
        public final String code;
        public final String libelle;
        public final String coursEau;
        public final Double dernierDebit;

        Station(String code, String libelle, String coursEau, Double dernierDebit) {
            this.code = code;
            this.libelle = libelle;
            this.coursEau = coursEau;
            this.dernierDebit = dernierDebit;
        }

        public String display() {
            String s = libelle + " (" + code + ")";
            if (coursEau != null && !coursEau.isEmpty()) s += " – " + coursEau;
            if (dernierDebit != null) s += " – " + String.format(Locale.FRANCE, "%.1f m³/s", dernierDebit);
            return s;
        }
    }

    public static final class Observation {
        public final long timestampMs;
        public final float debit; // m3/s, moyen ou instantané

        Observation(long t, float d) {
            this.timestampMs = t;
            this.debit = d;
        }
    }

    private HubEauClient() {
    }

    private static String get(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        try {
            int code = c.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? c.getErrorStream() : c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            if (code >= 400) {
                throw new RuntimeException("HTTP " + code + " " + sb.substring(0, Math.min(sb.length(), 200)));
            }
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static long toMillis(String dateObs, String heureObs) {
        try {
            String iso = dateObs;
            if (heureObs != null && heureObs.length() >= 4 && !heureObs.equals("000000")) {
                iso = dateObs + "T" + heureObs.substring(0, 2) + ":" + heureObs.substring(2, 4) + ":00Z";
            }
            SimpleDateFormat fmt;
            if (iso.contains("T")) {
                fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            } else {
                fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
            Date d = fmt.parse(iso);
            return d != null ? d.getTime() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    public static List<Station> searchStations(String query) throws Exception {
        List<Station> result = new ArrayList<>();
        JSONObject root = new JSONObject(get(BASE + "/stations?en_service=true&size=30&format=json"
                + (query.trim().isEmpty() ? "" : "&libelle_station=" + java.net.URLEncoder.encode(query.trim(), "UTF-8"))));
        JSONArray arr = root.optJSONArray("data");
        if (arr == null) return result;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            result.add(new Station(
                    s.getString("code_station"),
                    s.optString("libelle_station", s.getString("code_station")),
                    s.optString("libelle_cours_eau", null),
                    s.has("dernier_debit") && !s.isNull("dernier_debit")
                            ? s.getDouble("dernier_debit") : null));
        }
        return result;
    }

    public static List<Observation> fetchDebits(String stationCode, long startMs, long endMs, boolean tempsReel) throws Exception {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String path = tempsReel ? "/observations_tr" : "/observations";
        String url = BASE + path
                + "?code_entite=" + java.net.URLEncoder.encode(stationCode, "UTF-8")
                + "&grandeur_hydro=Q"
                + "&date_debut_obs=" + fmt.format(new Date(startMs))
                + "&date_fin_obs=" + fmt.format(new Date(endMs))
                + "&size=2000&format=json&sort=asc";
        JSONObject root = new JSONObject(get(url));
        JSONArray arr = root.optJSONArray("data");
        List<Observation> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            double d = o.optDouble("resultat_obs", Double.NaN);
            if (Double.isNaN(d)) continue;
            // Hubeau renvoie les débits en l/s pour Q ; conversion en m3/s si > seuil arbitraire de cohérence
            double debit = d;
            if (tempsReel) debit = d; // observations_tr : l/s
            if (debit > 20000) debit = debit / 1000.0;
            long t = toMillis(o.optString("date_obs", ""), o.optString("heure_obs", null));
            if (t > 0) list.add(new Observation(t, (float) debit));
        }
        return list;
    }
}
