package fr.mondossier.debitsrivieres;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment implements StationListener {

    private AutoCompleteTextView searchInput;
    private TextView stationInfo;
    private TextView statusText;
    private ChartView chart;
    private Button favToggle;
    private Spinner periodSpinner;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private final Runnable debounceRunnable = this::runAutocomplete;

    private MainActivity activity() {
        return (MainActivity) getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tab_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, Bundle savedInstanceState) {
        searchInput = v.findViewById(R.id.search_input);
        stationInfo = v.findViewById(R.id.station_info);
        statusText = v.findViewById(R.id.status_text);
        chart = v.findViewById(R.id.chart);
        favToggle = v.findViewById(R.id.fav_toggle_button);
        periodSpinner = v.findViewById(R.id.period_spinner);
        Button refreshButton = v.findViewById(R.id.refresh_button);

        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, MainActivity.PERIOD_LABELS);
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(periodAdapter);
        periodSpinner.setSelection(activity() != null ? activity().selectedPeriod : 1);
        periodSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (activity() != null) {
                    activity().selectedPeriod = pos;
                    activity().loadDebits();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        refreshButton.setOnClickListener(btn -> {
            if (activity() != null) activity().loadDebits();
        });

        favToggle.setOnClickListener(btn -> {
            MainActivity a = activity();
            if (a == null) return;
            if (a.currentStation == null) {
                Toast.makeText(requireContext(), "Sélectionnez d'abord une station", Toast.LENGTH_SHORT).show();
                return;
            }
            if (FavoritesStore.isFavorite(requireContext(), a.currentStation.code)) {
                FavoritesStore.remove(requireContext(), a.currentStation.code);
                Toast.makeText(requireContext(), "Retiré des favoris", Toast.LENGTH_SHORT).show();
            } else {
                FavoritesStore.add(requireContext(), a.currentStation);
                Toast.makeText(requireContext(), "Ajouté aux favoris ★", Toast.LENGTH_SHORT).show();
            }
            updateFavButton();
        });

        MainActivity a = activity();
        if (a != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
            searchInput.setAdapter(adapter);
            searchInput.setOnItemClickListener((parent, view, pos, id) -> {
                String picked = (String) parent.getItemAtPosition(pos);
                HubEauClient.Station s = a.stationByLabel(picked);
                if (s != null) a.selectStation(s);
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
                    debounceHandler.removeCallbacks(debounceRunnable);
                    if (s.toString().trim().length() >= 2) {
                        debounceHandler.postDelayed(debounceRunnable, 150);
                    }
                }
            });
        }
        MainActivity a0 = activity();
        if (a0 != null) a0.registerSearch(this);
    }

    private void runAutocomplete() {
        MainActivity a = activity();
        if (a == null) return;
        a.autocomplete(searchInput.getText().toString(), labels -> {
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) searchInput.getAdapter();
            adapter.clear();
            adapter.addAll(labels);
            if (searchInput.isFocused() && !labels.isEmpty() && !searchInput.isPerformingCompletion()) {
                adapter.getFilter().filter(null);
                searchInput.showDropDown();
            }
        });
    }

    public void updateFavButton() {
        MainActivity a = activity();
        if (a == null || a.currentStation == null) {
            favToggle.setText("☆");
            return;
        }
        boolean fav = FavoritesStore.isFavorite(requireContext(), a.currentStation.code);
        favToggle.setText(fav ? "★" : "☆");
    }

    public void refreshDisplay() {
        MainActivity a = activity();
        if (a == null) return;
        if (a.currentStation != null) {
            stationInfo.setText(a.currentStation.display());
            updateFavButton();
        }
        chart.setSeries(a.series);
        statusText.setText(a.statusText);
        periodSpinner.setSelection(a.selectedPeriod);
    }

    @Override
    public void onStationPicked(HubEauClient.Station s) {
        MainActivity a = activity();
        if (a != null) a.selectStation(s);
    }
}
