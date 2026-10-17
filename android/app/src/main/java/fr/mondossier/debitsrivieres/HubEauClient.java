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
        public final Double lat;
        public final Double lon;

        Station(String code, String libelle, String coursEau) {
            this(code, libelle, coursEau, null, null);
        }

        public Station(String code, String libelle, String coursEau, Double lat, Double lon) {
            this.code = code;
            this.libelle = libelle;
            this.coursEau = coursEau;
            this.lat = lat;
            this.lon = lon;
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
        c.setConnectTimeout(10000);
        c.setReadTimeout(45000);
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
                + "&fields=code_station,libelle_station,libelle_cours_eau,longitude_station,latitude_station";
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
                    s.optString("libelle_cours_eau", null),
                    s.has("latitude_station") && !s.isNull("latitude_station") ? s.optDouble("latitude_station") : null,
                    s.has("longitude_station") && !s.isNull("longitude_station") ? s.optDouble("longitude_station") : null));
        }
        return result;
    }

    public static List<Station> searchStationsAny(String query) throws Exception {
        String q = query.trim();
        if (q.isEmpty()) return searchStations("");
        java.util.LinkedHashMap<String, Station> merged = new java.util.LinkedHashMap<>();
        String paramsBase = "&en_service=true&size=30&format=json"
                + "&fields=code_station,libelle_station,libelle_cours_eau&libelle_station=";
        String enc = java.net.URLEncoder.encode(q, "UTF-8");
        try {
            for (Station s : parseStations(get(BASE + "/referentiel/stations?" + paramsBase + enc))) {
                merged.put(s.code, s);
            }
        } catch (Exception ignored) {
        }
        if (merged.size() < 30) {
            try {
                String url = BASE + "/referentiel/stations?en_service=true&size=30&format=json"
                        + "&fields=code_station,libelle_station,libelle_cours_eau&libelle_cours_eau=" + enc;
                for (Station s : parseStations(get(url))) {
                    merged.putIfAbsent(s.code, s);
                }
            } catch (Exception ignored) {
            }
        }
        if (merged.isEmpty() && !q.isEmpty()) {
            try {
                for (Station s : searchStations(q)) merged.put(s.code, s);
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static List<Station> parseStations(String json) throws Exception {
        List<Station> result = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.optJSONArray("data");
        if (arr == null) return result;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            result.add(new Station(
                    s.getString("code_station"),
                    s.optString("libelle_station", s.getString("code_station")),
                    s.optString("libelle_cours_eau", null),
                    s.has("latitude_station") && !s.isNull("latitude_station") ? s.optDouble("latitude_station") : null,
                    s.has("longitude_station") && !s.isNull("longitude_station") ? s.optDouble("longitude_station") : null));
        }
        return result;
    }

    public static List<Station> fetchAllStations() throws Exception {
        String url = BASE + "/referentiel/stations?en_service=true&size=5000&format=json"
                + "&fields=code_station,libelle_station,libelle_cours_eau,longitude_station,latitude_station";
        return parseStations(get(url));
    }

    public static List<Observation> fetchDebitsTempsReel(String stationCode, long startMs, long endMs) throws Exception {
        SimpleDateFormat fmt = isoUtc();
        String url = BASE + "/observations_tr"
                + "?code_entite=" + java.net.URLEncoder.encode(stationCode, "UTF-8")
                + "&grandeur_hydro=Q"
                + "&date_debut_obs=" + fmt.format(new Date(startMs))
                + "&date_fin_obs=" + fmt.format(new Date(endMs))
                + "&size=5000&format=json&sort=asc";
        List<Observation> list = parseObservationsTr(url);
        // le temps réel ne maintient qu'un mois d'historique : complément par débits moyens journaliers si vide
        if (list.isEmpty() && endMs - startMs > 24L * 3600_000L) {
            return fetchDebitsJournaliers(stationCode, startMs, endMs);
        }
        return list;
    }

    public interface RelaySource {
        String load(String stationCode);
        void save(String stationCode, String content);
    }

    private static RelaySource relaySource;

    public static void setRelaySource(RelaySource s) {
        relaySource = s;
    }

    public static List<Observation> fetchDebitsFromRelay(String stationCode, long startMs, long endMs) throws Exception {
        String url = "https://raw.githubusercontent.com/remyjpbarral/hubeau_suivi/data/daily/stations/"
                + java.net.URLEncoder.encode(stationCode, "UTF-8") + ".json";
        String raw = null;
        Exception netError = null;
        try {
            raw = get(url);
            if (relaySource != null) relaySource.save(stationCode, raw);
        } catch (Exception e) {
            netError = e;
            if (relaySource != null) raw = relaySource.load(stationCode);
        }
        if (raw == null) {
            throw netError != null ? netError : new RuntimeException("relay inaccessible");
        }
        JSONObject root = new JSONObject(raw);
        JSONArray arr = root.getJSONArray("obs");
        List<Observation> list = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        java.util.LinkedHashMap<String, Observation> byDate = new java.util.LinkedHashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONArray pair = arr.getJSONArray(i);
            try {
                long t = fmt.parse(pair.getString(0)).getTime();
                if (t >= startMs && t <= endMs) {
                    byDate.put(pair.getString(0), new Observation(t, (float) pair.getDouble(1)));
                }
            } catch (Exception ignored) {
            }
        }
        list.addAll(byDate.values());
        java.util.Collections.sort(list, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
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
        java.util.Collections.sort(list, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
        return list;
    }

    private static List<Observation> parseObservationsTr(String url) throws Exception {
        List<Observation> list = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String next = url;
        int pages = 0;
        while (next != null && !next.isEmpty() && pages < 10) {
            JSONObject root = new JSONObject(get(next));
            JSONArray arr = root.optJSONArray("data");
            if (arr != null) {
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
            }
            next = root.has("next") && !root.isNull("next") ? root.optString("next") : null;
            pages++;
        }
        java.util.Collections.sort(list, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
        return list;
    }
}
