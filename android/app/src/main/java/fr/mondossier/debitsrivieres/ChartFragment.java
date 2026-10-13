package fr.mondossier.debitsrivieres;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class ChartFragment extends Fragment {

    private TextView title;
    private TextView status;
    private ChartView chart;
    private Spinner periodSpinner;

    private MainActivity activity() {
        return (MainActivity) getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tab_chart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, Bundle savedInstanceState) {
        title = v.findViewById(R.id.chart_station_title);
        status = v.findViewById(R.id.chart_status);
        chart = v.findViewById(R.id.chart);
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

        MainActivity a = activity();
        if (a != null) a.registerChart(this);
    }

    public void refreshDisplay() {
        MainActivity a = activity();
        if (a == null) return;
        if (a.currentStation != null) {
            title.setText(a.currentStation.display());
        } else {
            title.setText(getString(R.string.pick_station_hint));
        }
        chart.setSeries(a.series);
        status.setText(a.statusText);
        periodSpinner.setSelection(a.selectedPeriod);
    }
}
