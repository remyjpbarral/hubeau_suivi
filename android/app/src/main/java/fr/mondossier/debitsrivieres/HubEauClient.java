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

    private static final String BASE = "https://hubeau.eaufrance.fr/api/v2/hydrometrie";
    private static final String UA = "DebitsRivieres/1.0 (Android; suivi hydrometrique)";

    public static final class Station {
        public final String code;
        public final String libelle;
        public final String coursEau;

        Station(String code, String libelle, String coursEau) {
            this.code = code;
            this.libelle = libelle;
            this.coursEau = coursEau;
        }

        public String display() {
            String s = libelle + " (" + code + ")";
            if (coursEau != null && !coursEau.isEmpty()) s += " – " + coursEau;
            return s;
        }
    }

    public static final class Observation {
        public final long timestampMs;
        public final float debit; // m3/s

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

    private static SimpleDateFormat isoUtc() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    }

    private static SimpleDateFormat isoDate() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    }

    public static List<Station> searchStations(String query) throws Exception {
        List<Station> result = new ArrayList<>();
        String q = query.trim();
        String params = "&en_service=true&size=30&format=json"
                + "&fields=code_station,libelle_station,libelle_cours_eau";
        if (!q.isEmpty()) {
            params += "&libelle_station=" + java.net.URLEncoder.encode(q, "UTF-8");
        }
        JSONObject root = new JSONObject(get(BASE + "/referentiel/stations?" + params));
        JSONArray arr = root.optJSONArray("data");
        if (arr == null) return result;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            result.add(new Station(
                    s.getString("code_station"),
                    s.optString("libelle_station", s.getString("code_station")),
                    s.optString("libelle_cours_eau", null)));
        }
        return result;
    }

    public static List<Observation> fetchDebitsTempsReel(String stationCode, long startMs, long endMs) throws Exception {
        SimpleDateFormat fmt = isoUtc();
        String url = BASE + "/observations_tr"
                + "?code_entite=" + java.net.URLEncoder.encode(stationCode, "UTF-8")
                + "&grandeur_hydro=Q"
                + "&date_debut_obs=" + fmt.format(new Date(startMs))
                + "&date_fin_obs=" + fmt.format(new Date(endMs))
                + "&size=20000&format=json&sort=asc";
        List<Observation> list = parseObservationsTr(url);
        // le temps réel ne maintient qu'un mois d'historique : complément par débits moyens journaliers si vide
        if (list.isEmpty() && endMs - startMs > 24L * 3600_000L) {
            return fetchDebitsJournaliers(stationCode, startMs, endMs);
        }
        return list;
    }

    public static List<Observation> fetchDebitsJournaliers(String stationCode, long startMs, long endMs) throws Exception {
        SimpleDateFormat fmt = isoDate();
        String url = BASE + "/obs_elab"
                + "?code_entite=" + java.net.URLEncoder.encode(stationCode, "UTF-8")
                + "&grandeur_hydro_elab=QmnJ"
                + "&date_debut_obs_elab=" + fmt.format(new Date(startMs))
                + "&date_fin_obs_elab=" + fmt.format(new Date(endMs))
                + "&size=20000&format=json&sort=asc";
        List<Observation> list = new ArrayList<>();
        JSONObject root = new JSONObject(get(url));
        JSONArray arr = root.optJSONArray("data");
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            double lps = o.optDouble("resultat_obs_elab", Double.NaN);
            if (Double.isNaN(lps)) continue;
            list.add(new Observation(
                    isoDate().parse(o.optString("date_obs_elab", "")).getTime(),
                    (float) (lps / 1000.0)));
        }
        return list;
    }

    private static List<Observation> parseObservationsTr(String url) throws Exception {
        List<Observation> list = new ArrayList<>();
        JSONObject root = new JSONObject(get(url));
        JSONArray arr = root.optJSONArray("data");
        if (arr == null) return list;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            double lps = o.optDouble("resultat_obs", Double.NaN);
            String dateObs = o.optString("date_obs", "");
            if (Double.isNaN(lps) || dateObs.isEmpty()) continue;
            Date d;
            try {
                d = fmt.parse(dateObs);
            } catch (Exception e) {
                continue;
            }
            if (d != null) list.add(new Observation(d.getTime(), (float) (lps / 1000.0)));
        }
        return list;
    }
}
