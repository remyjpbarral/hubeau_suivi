package fr.mondossier.debitsrivieres;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.util.GeoPoint;

public class MapFragment extends Fragment implements StationListener {

    private MapView map;
    private Marker pendingMarker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue("DebitsRivieres/1.0");
        View v = inflater.inflate(R.layout.tab_map, container, false);
        map = v.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(6.0);
        map.getController().setCenter(new GeoPoint(46.6, 2.4));
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View v, Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        MainActivity a = (MainActivity) getActivity();
        if (a != null) a.registerMap(this);
    }

    public void addStations(java.util.List<HubEauClient.Station> stations, final StationListener listener) {
        if (map == null) return;
        map.getOverlays().clear();
        for (final HubEauClient.Station s : stations) {
            if (s.lat == null || s.lon == null) continue;
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(s.lat, s.lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle(s.libelle);
            m.setSnippet(s.coursEau != null ? s.coursEau : s.code);
            m.setOnMarkerClickListener((marker, mapView) -> {
                listener.onStationPicked(s);
                return true;
            });
            map.getOverlays().add(m);
        }
        map.invalidate();
    }

    public void focusStation(HubEauClient.Station s) {
        if (map == null || s.lat == null || s.lon == null) return;
        map.getController().setZoom(11.0);
        map.getController().setCenter(new GeoPoint(s.lat, s.lon));
        map.invalidate();
    }

    @Override
    public void onStationPicked(HubEauClient.Station s) {
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
