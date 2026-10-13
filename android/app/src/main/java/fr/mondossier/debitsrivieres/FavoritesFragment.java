package fr.mondossier.debitsrivieres;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class FavoritesFragment extends Fragment implements StationListener {

    private ListView list;
    private TextView empty;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tab_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, Bundle savedInstanceState) {
        MainActivity a = (MainActivity) getActivity();
        if (a != null) a.registerFavorites(this);
        list = v.findViewById(R.id.fav_list);
        empty = v.findViewById(R.id.fav_empty);
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public void refresh() {
        if (list == null || getContext() == null) return;
        List<HubEauClient.Station> favs = FavoritesStore.list(getContext());
        List<String> labels = new ArrayList<>();
        for (HubEauClient.Station s : favs) labels.add(s.display());
        list.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_station, labels));
        list.setEmptyView(empty);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            MainActivity a = (MainActivity) getActivity();
            if (a != null) {
                a.selectStation(favs.get(pos));
                a.goToSearch();
            }
        });
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            FavoritesStore.remove(requireContext(), favs.get(pos).code);
            refresh();
            return true;
        });
    }

    @Override
    public void onStationPicked(HubEauClient.Station s) {
    }
}
