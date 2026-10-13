package fr.mondossier.debitsrivieres;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final long HOUR = 3600_000L;
    private static final long DAY = 24 * HOUR;

    private static final long[] PERIODS = {6 * HOUR, 24 * HOUR, 7 * DAY, 30 * DAY, 365 * DAY};
    private static final boolean[] TEMPS_REEL = {true, true, true, true, false};
    private static final String[] PERIOD_LABELS = {
            "6 dernières heures", "24 dernières heures", "7 derniers jours",
            "30 derniers jours", "12 derniers mois"
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<HubEauClient.Station> stations = new ArrayList<>();
    private final List<HubEauClient.Station> allStations = new ArrayList<>();
    private final Map<String, List<HubEauClient.Observation>> series = new LinkedHashMap<>();

    private AutoCompleteTextView searchInput;
    private Spinner periodSpinner;
    private TextView stationInfo;
    private TextView statusText;
    private ChartView chart;
    private Button favToggle;
    private ArrayAdapter<String> stationAdapter;
    private ArrayAdapter<String> autocompleteAdapter;

    private HubEauClient.Station currentStation = null;
    private int selectedPeriod = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchInput = findViewById(R.id.search_input);
        periodSpinner = findViewById(R.id.period_spinner);
        stationInfo = findViewById(R.id.station_info);
        statusText = findViewById(R.id.status_text);
        chart = findViewById(R.id.chart);
        favToggle = findViewById(R.id.fav_toggle_button);

        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, PERIOD_LABELS);
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(periodAdapter);
        periodSpinner.setSelection(selectedPeriod);
        periodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedPeriod = pos;
                if (currentStation != null) loadDebits();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(v -> searchStations(searchInput.getText().toString()));

        Button refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> {
            if (currentStation != null) loadDebits();
            else Toast.makeText(this, "Sélectionnez d'abord une station", Toast.LENGTH_SHORT).show();
        });

        Button favListButton = findViewById(R.id.fav_list_button);
        favListButton.setOnClickListener(v -> showFavorites());

        favToggle.setOnClickListener(v -> {
            if (currentStation == null) {
                Toast.makeText(this, "Sélectionnez d'abord une station", Toast.LENGTH_SHORT).show();
                return;
            }
            if (FavoritesStore.isFavorite(this, currentStation.code)) {
                FavoritesStore.remove(this, currentStation.code);
                Toast.makeText(this, "Retiré des favoris", Toast.LENGTH_SHORT).show();
            } else {
                FavoritesStore.add(this, currentStation);
                Toast.makeText(this, "Ajouté aux favoris", Toast.LENGTH_SHORT).show();
            }
            updateFavButton();
        });

        autocompleteAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        searchInput.setAdapter(autocompleteAdapter);
        searchInput.setOnItemClickListener((parent, view, pos, id) -> {
            String picked = (String) parent.getItemAtPosition(pos);
            HubEauClient.Station s = stationByLabel(picked);
            if (s != null) selectStation(s);
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim();
                if (q.length() >= 3) autocomplete(q);
            }
        });

        statusText.setText(R.string.loading);
        loadReferentiel();
    }

    private void loadReferentiel() {
        runInBackground(() -> {
            List<HubEauClient.Station> cached = StationCache.load(MainActivity.this);
            if (!cached.isEmpty() && StationCache.isFresh(MainActivity.this)) {
                runOnUiThread(() -> {
                    allStations.clear();
                    allStations.addAll(cached);
                    stations.clear();
                    stations.addAll(StationCache.filter(allStations, ""));
                    statusText.setText("");
                    if (stations.isEmpty()) statusText.setText(R.string.no_results);
                });
                return;
            }
            try {
                List<HubEauClient.Station> all = HubEauClient.fetchAllStations();
                StationCache.save(MainActivity.this, all);
                runOnUiThread(() -> {
                    allStations.clear();
                    allStations.addAll(all);
                    stations.clear();
                    stations.addAll(StationCache.filter(allStations, ""));
                    statusText.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!cached.isEmpty()) {
                        allStations.clear();
                        allStations.addAll(cached);
                        stations.clear();
                        stations.addAll(StationCache.filter(allStations, ""));
                        statusText.setText("");
                    } else {
                        showError(getString(R.string.error_api) + " : " + e.getMessage());
                    }
                });
            }
        });
    }

    private HubEauClient.Station stationByLabel(String label) {
        for (HubEauClient.Station s : stations) {
            if (s.display().equals(label)) return s;
        }
        return null;
    }

    private void autocomplete(String query) {
        if (allStations.isEmpty()) return;
        List<HubEauClient.Station> found = StationCache.filter(allStations, query);
        stations.clear();
        stations.addAll(found);
        List<String> labels = new ArrayList<>();
        for (HubEauClient.Station s : found) labels.add(s.display());
        autocompleteAdapter.clear();
        autocompleteAdapter.addAll(labels);
        if (searchInput.isFocused() && !labels.isEmpty()
                && !searchInput.isPerformingCompletion()) {
            autocompleteAdapter.getFilter().filter(null);
            searchInput.showDropDown();
        }
    }

    private void searchStations(String query) {
        if (allStations.isEmpty()) {
            statusText.setText(R.string.loading);
            loadReferentiel();
            return;
        }
        List<HubEauClient.Station> found = StationCache.filter(allStations, query);
        stations.clear();
        stations.addAll(found);
        if (found.isEmpty()) {
            currentStation = null;
            stationInfo.setText("");
            series.clear();
            chart.setSeries(series);
            statusText.setText(R.string.no_results);
        } else {
            showStationPicker();
        }
    }

    private void showStationPicker() {
        List<String> labels = new ArrayList<>();
        for (HubEauClient.Station s : stations) labels.add(s.display());
        stationAdapter = new ArrayAdapter<>(this, R.layout.item_station, labels);
        stationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusText.setText("");
        new AlertDialog.Builder(this)
                .setTitle("Choisir une station")
                .setAdapter(stationAdapter, (dialog, which) -> selectStation(stations.get(which)))
                .show();
    }

    private void showFavorites() {
        List<HubEauClient.Station> favs = FavoritesStore.list(this);
        if (favs.isEmpty()) {
            Toast.makeText(this, getString(R.string.favorites_empty), Toast.LENGTH_LONG).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (HubEauClient.Station s : favs) labels.add(s.display());
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, R.layout.item_station, labels);
        new AlertDialog.Builder(this)
                .setTitle("Favoris")
                .setAdapter(ad, (dialog, which) -> selectStation(favs.get(which)))
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void selectStation(HubEauClient.Station s) {
        currentStation = s;
        stationInfo.setText(s.display());
        updateFavButton();
        loadDebits();
    }

    private void updateFavButton() {
        boolean fav = currentStation != null && FavoritesStore.isFavorite(this, currentStation.code);
        favToggle.setText(fav ? "★" : "☆");
    }

    private void loadDebits() {
        final String code = currentStation.code;
        final boolean tr = TEMPS_REEL[selectedPeriod];
        final long now = System.currentTimeMillis();
        final long start = now - PERIODS[selectedPeriod];
        statusText.setText(R.string.loading);
        runInBackground(() -> {
            try {
                List<HubEauClient.Observation> obs = tr
                        ? HubEauClient.fetchDebitsTempsReel(code, start, now)
                        : HubEauClient.fetchDebitsJournaliers(code, start, now);
                runOnUiThread(() -> {
                    series.clear();
                    series.put(currentStation.libelle, obs);
                    chart.setSeries(series);
                    if (obs.isEmpty()) {
                        statusText.setText(R.string.no_data);
                    } else {
                        statusText.setText("");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(getString(R.string.error_api) + " : " + e.getMessage()));
            }
        });
    }

    private void showError(String msg) {
        statusText.setText(msg);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void runInBackground(Runnable r) {
        executor.execute(r);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
