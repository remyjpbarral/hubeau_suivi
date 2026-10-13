package fr.mondossier.debitsrivieres;

import android.content.Context;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment implements StationListener {

    private MapView map;
    private boolean markersReady = false;
    private List<HubEauClient.Station> pendingStations;
    private StationListener pendingListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            File base = new File(context.getCacheDir(), "osmdroid");
            File tiles = new File(base, "tiles");
            if (!tiles.exists()) tiles.mkdirs();
            Configuration.getInstance().setOsmdroidBasePath(base);
            Configuration.getInstance().setOsmdroidTileCache(tiles);
        } catch (Exception ignored) {
        }
        Configuration.getInstance().setUserAgentValue("DebitsRivieres/1.0");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.tab_map, container, false);
        try {
            map = v.findViewById(R.id.map);
            map.setTileSource(TileSourceFactory.MAPNIK);
            map.setMultiTouchControls(true);
            map.getController().setZoom(6.0);
            map.getController().setCenter(new GeoPoint(46.6, 2.4));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Carte indisponible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View v, Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        MainActivity a = (MainActivity) getActivity();
        if (a != null) a.registerMap(this);
    }

    public void addStations(final List<HubEauClient.Station> stations, final StationListener listener) {
        if (map == null || stations == null) {
            pendingStations = stations;
            pendingListener = listener;
            return;
        }
        markersReady = false;
        final List<Marker> prepared = new ArrayList<>();
        Thread t = new Thread(() -> {
            for (final HubEauClient.Station s : stations) {
                if (s.lat == null || s.lon == null) continue;
                try {
                    Marker m = new Marker(map);
                    m.setPosition(new GeoPoint(s.lat, s.lon));
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    m.setTitle(s.libelle);
                    m.setSnippet((s.coursEau != null && !s.coursEau.isEmpty() ? s.coursEau : "") + " · " + s.code);
                    m.setOnMarkerClickListener((marker, mapView) -> {
                        listener.onStationPicked(s);
                        return true;
                    });
                    prepared.add(m);
                } catch (Exception ignored) {
                }
            }
            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                if (map == null) return;
                map.getOverlays().clear();
                map.getOverlays().addAll(prepared);
                map.invalidate();
                markersReady = true;
            });
        });
        t.setDaemon(true);
        t.start();
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
