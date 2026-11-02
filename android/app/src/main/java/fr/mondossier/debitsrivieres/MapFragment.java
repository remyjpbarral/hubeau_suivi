package fr.mondossier.debitsrivieres;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment implements StationListener {

    private MapView map;
    private Drawable icon;
    private android.widget.Button gpsButton;
    private static final int REQ_GPS = 1001;
    private final List<HubEauClient.Station> allStations = new ArrayList<>();
    private List<HubEauClient.Station> visibleStations = new ArrayList<>();
    private StationListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean markersShown = false;
    private static final int VISIBLE_LIMIT = 400;

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
        map = v.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(6.0);
        map.getController().setCenter(new GeoPoint(46.6, 2.4));
        icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_station_marker);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        gpsButton = v.findViewById(R.id.gps_button);
        gpsButton.setOnClickListener(btn -> centerOnGps());
        MainActivity a = (MainActivity) getActivity();
        if (a != null) a.registerMap(this);
    }

    private void centerOnGps() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && (requireContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GPS);
            return;
        }
        doCenterOnGps();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_GPS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            doCenterOnGps();
        } else {
            android.widget.Toast.makeText(requireContext(),
                    "Permission de localisation refusée", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void doCenterOnGps() {
        try {
            LocationManager lm = (LocationManager) requireContext()
                    .getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            Location last = null;
            try {
                last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (SecurityException ignored) {
            }
            if (last == null) {
                try {
                    last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                } catch (SecurityException ignored) {
                }
            }
            if (last != null) {
                map.getController().setZoom(11.0);
                map.getController().animateTo(new GeoPoint(last.getLatitude(), last.getLongitude()));
                return;
            }
            android.widget.Toast.makeText(requireContext(),
                    "Position en cours d'acquisition, retouchez 📍 dans un instant",
                    android.widget.Toast.LENGTH_SHORT).show();
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, new LocationListener() {
                @Override
                public void onLocationChanged(Location loc) {
                    try { lm.removeUpdates(this); } catch (Exception ignored) {}
                    if (map != null && isAdded()) {
                        map.getController().setZoom(11.0);
                        map.getController().animateTo(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
                    }
                }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            });
        } catch (Exception e) {
            android.widget.Toast.makeText(requireContext(),
                    "GPS indisponible : " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public void setAllStations(List<HubEauClient.Station> stations, StationListener listener) {
        this.listener = listener;
        allStations.clear();
        allStations.addAll(stations);
        refreshVisibleMarkers();
    }

    private void refreshVisibleMarkers() {
        if (map == null || allStations.isEmpty() || listener == null) return;
        BoundingBox box;
        try {
            box = map.getProjection().getBoundingBox();
        } catch (Exception e) {
            return;
        }
        List<HubEauClient.Station> inBox = new ArrayList<>();
        for (HubEauClient.Station s : allStations) {
            if (s.lat == null || s.lon == null) continue;
            if (box.contains(s.lat, s.lon)) inBox.add(s);
            if (inBox.size() >= VISIBLE_LIMIT) break;
        }
        visibleStations = inBox;
        final List<Marker> prepared = new ArrayList<>();
        for (final HubEauClient.Station s : inBox) {
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(s.lat, s.lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            if (icon != null) m.setIcon(icon);
            m.setTitle(s.libelle);
            m.setSnippet((s.coursEau != null && !s.coursEau.isEmpty() ? s.coursEau : "") + " · " + s.code);
            m.setOnMarkerClickListener((marker, mapView) -> {
                listener.onStationPicked(s);
                return true;
            });
            prepared.add(m);
        }
        map.getOverlays().clear();
        map.getOverlays().add(new org.osmdroid.views.overlay.MapEventsOverlay(new org.osmdroid.events.MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));
        map.getOverlays().addAll(prepared);
        map.invalidate();
        markersShown = true;
        MainActivity a = (MainActivity) getActivity();
        if (a != null) a.warmupZone(visibleStations);
    }

    public void focusStation(HubEauClient.Station s) {
        if (map == null || s.lat == null || s.lon == null) return;
        map.getController().setZoom(11.0);
        map.getController().animateTo(new GeoPoint(s.lat, s.lon));
    }

    @Override
    public void onStationPicked(HubEauClient.Station s) {
        if (listener != null) listener.onStationPicked(s);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
            map.addMapListener(new MapListener() {
                @Override
                public boolean onScroll(ScrollEvent event) {
                    scheduleRefresh();
                    return true;
                }

                @Override
                public boolean onZoom(ZoomEvent event) {
                    scheduleRefresh();
                    return true;
                }
            });
            if (!markersShown) refreshVisibleMarkers();
        }
    }

    private void scheduleRefresh() {
        mainHandler.removeCallbacks(this::refreshVisibleMarkers);
        mainHandler.postDelayed(this::refreshVisibleMarkers, 400);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
