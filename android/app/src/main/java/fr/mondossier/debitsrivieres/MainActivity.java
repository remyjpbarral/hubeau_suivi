package fr.mondossier.debitsrivieres;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
    private static final boolean[] TEMPS_REEL = {true, true, true, false, false};
    private static final String[] PERIOD_LABELS = {
            "6 dernières heures", "24 dernières heures", "7 derniers jours",
            "30 derniers jours", "12 derniers mois"
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<HubEauClient.Station> stations = new ArrayList<>();
    private final Map<String, List<HubEauClient.Observation>> series = new LinkedHashMap<>();

    private EditText searchInput;
    private Spinner stationSpinner;
    private Spinner periodSpinner;
    private TextView stationInfo;
    private TextView statusText;
    private ChartView chart;
    private ArrayAdapter<String> stationAdapter;

    private int selectedStation = -1;
    private int selectedPeriod = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchInput = findViewById(R.id.search_input);
        stationSpinner = new Spinner(this);
        periodSpinner = findViewById(R.id.period_spinner);
        stationInfo = findViewById(R.id.station_info);
        statusText = findViewById(R.id.status_text);
        chart = findViewById(R.id.chart);

        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, PERIOD_LABELS);
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(periodAdapter);
        periodSpinner.setSelection(selectedPeriod);
        periodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedPeriod = pos;
                if (selectedStation >= 0) loadDebits();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(v -> searchStations(searchInput.getText().toString()));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            searchStations(searchInput.getText().toString());
            return true;
        });

        Button refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> {
            if (selectedStation >= 0) loadDebits();
            else Toast.makeText(this, "Sélectionnez d'abord une station", Toast.LENGTH_SHORT).show();
        });

        stationAdapter = new ArrayAdapter<>(this, R.layout.item_station, new ArrayList<>());
        stationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        statusText.setText(R.string.loading);
        searchStations("");
    }

    private void searchStations(String query) {
        statusText.setText(R.string.loading);
        runInBackground(() -> {
            try {
                List<HubEauClient.Station> found = HubEauClient.searchStations(query);
                runOnUiThread(() -> {
                    stations.clear();
                    stations.addAll(found);
                    selectedStation = -1;
                    series.clear();
                    chart.setSeries(series);
                    stationInfo.setText("");
                    if (found.isEmpty()) {
                        showStationPicker(getString(R.string.no_results));
                    } else {
                        List<String> labels = new ArrayList<>();
                        for (HubEauClient.Station s : found) labels.add(s.display());
                        stationAdapter = new ArrayAdapter<>(MainActivity.this,
                                R.layout.item_station, labels);
                        stationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        showStationPicker(null);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(getString(R.string.error_api) + " : " + e.getMessage()));
            }
        });
    }

    private void showStationPicker(String message) {
        if (message != null) {
            statusText.setText(message);
            return;
        }
        statusText.setText("");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Choisir une station")
                .setAdapter(stationAdapter, (dialog, which) -> {
                    selectedStation = which;
                    stationInfo.setText(stations.get(which).display());
                    loadDebits();
                })
                .show();
    }

    private void loadDebits() {
        final String code = stations.get(selectedStation).code;
        final boolean tr = TEMPS_REEL[selectedPeriod];
        final long now = System.currentTimeMillis();
        final long start = now - PERIODS[selectedPeriod];
        statusText.setText(R.string.loading);
        runInBackground(() -> {
            try {
                List<HubEauClient.Observation> obs = HubEauClient.fetchDebits(code, start, now, tr);
                runOnUiThread(() -> {
                    series.clear();
                    series.put(stations.get(selectedStation).libelle, obs);
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
