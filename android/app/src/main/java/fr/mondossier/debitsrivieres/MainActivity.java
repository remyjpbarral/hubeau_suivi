package fr.mondossier.debitsrivieres;

import android.os.Bundle;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final long HOUR = 3600_000L;
    private static final long DAY = 24 * HOUR;

    static final long[] PERIODS = {6 * HOUR, 24 * HOUR, 7 * DAY, 30 * DAY, 365 * DAY};
    static final boolean[] TEMPS_REEL = {true, true, true, true, false};
    static final String[] PERIOD_LABELS = {
            "6 heures", "24 heures", "7 jours", "30 jours", "12 mois"
    };

    public interface AutocompleteCallback {
        void onResult(List<String> labels);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    final List<HubEauClient.Station> stations = new ArrayList<>();
    private final List<HubEauClient.Station> allStations = new ArrayList<>();
    final Map<String, List<HubEauClient.Observation>> series = new LinkedHashMap<>();
    private final List<HubEauClient.Station> referentielForMap = new ArrayList<>();

    public HubEauClient.Station currentStation = null;
    public int selectedPeriod = 1;
    public CharSequence statusText = "";

    private ViewPager2 pager;
    private SearchFragment searchFragment;
    private MapFragment mapFragment;
    private FavoritesFragment favoritesFragment;

    private static class TabsAdapter extends FragmentStateAdapter {
        TabsAdapter(FragmentActivity a) {
            super(a);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new SearchFragment();
                case 1:
                    return new MapFragment();
                default:
                    return new FavoritesFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pager = findViewById(R.id.pager);
        pager.setAdapter(new TabsAdapter(this));
        pager.setOffscreenPageLimit(3);

        TabLayout tabs = findViewById(R.id.tabs);
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            if (position == 0) tab.setText(R.string.tab_search);
            else if (position == 1) tab.setText(R.string.tab_map);
            else tab.setText(R.string.tab_favorites);
        }).attach();

        loadReferentiel();
    }

    void registerSearch(SearchFragment f) {
        this.searchFragment = f;
        f.refreshDisplay();
    }

    void registerMap(MapFragment f) {
        this.mapFragment = f;
        if (!referentielForMap.isEmpty()) f.addStations(referentielForMap, this::selectStation);
    }

    void registerFavorites(FavoritesFragment f) {
        this.favoritesFragment = f;
    }

    void goToSearch() {
        pager.setCurrentItem(0);
    }

    void goToMap() {
        pager.setCurrentItem(1);
    }

    public void selectStation(HubEauClient.Station s) {
        currentStation = s;
        statusText = "";
        if (searchFragment != null) searchFragment.refreshDisplay();
        if (mapFragment != null && s.lat != null) mapFragment.focusStation(s);
        loadDebits();
    }

    public void loadDebits() {
        if (currentStation == null) return;
        final String code = currentStation.code;
        final boolean tr = TEMPS_REEL[selectedPeriod];
        final long now = System.currentTimeMillis();
        final long start = now - PERIODS[selectedPeriod];
        statusText = getString(R.string.loading);
        if (searchFragment != null) searchFragment.refreshDisplay();
        runInBackground(() -> {
            try {
                List<HubEauClient.Observation> obs = tr
                        ? HubEauClient.fetchDebitsTempsReel(code, start, now)
                        : HubEauClient.fetchDebitsJournaliers(code, start, now);
                runOnUiThread(() -> {
                    series.clear();
                    series.put(currentStation.libelle, obs);
                    statusText = obs.isEmpty() ? getString(R.string.no_data) : "";
                    if (searchFragment != null) searchFragment.refreshDisplay();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText = getString(R.string.error_api) + " : " + e.getMessage();
                    if (searchFragment != null) searchFragment.refreshDisplay();
                });
            }
        });
    }

    HubEauClient.Station stationByLabel(String label) {
        for (HubEauClient.Station s : stations) {
            if (s.display().equals(label)) return s;
        }
        return null;
    }

    void autocomplete(String query, final AutocompleteCallback cb) {
        if (allStations.isEmpty()) return;
        List<HubEauClient.Station> found = StationCache.filter(allStations, query);
        stations.clear();
        stations.addAll(found);
        List<String> labels = new ArrayList<>();
        for (HubEauClient.Station s : found) labels.add(s.display());
        cb.onResult(labels);
    }

    private void loadReferentiel() {
        List<HubEauClient.Station> bundled = StationCache.loadBundled(this);
        List<HubEauClient.Station> cached = StationCache.load(this);
        if (cached.isEmpty()) cached = bundled;
        final List<HubEauClient.Station> source = cached;
        if (!source.isEmpty()) {
            allStations.clear();
            allStations.addAll(source);
            referentielForMap.clear();
            referentielForMap.addAll(source);
            if (mapFragment != null) mapFragment.addStations(source, this::selectStation);
        }
        if (!source.isEmpty() && StationCache.isFresh(this)) return;
        runInBackground(() -> {
            try {
                List<HubEauClient.Station> all = HubEauClient.fetchAllStations();
                StationCache.save(MainActivity.this, all);
                runOnUiThread(() -> {
                    allStations.clear();
                    allStations.addAll(all);
                    referentielForMap.clear();
                    referentielForMap.addAll(all);
                    if (mapFragment != null) mapFragment.addStations(all, MainActivity.this::selectStation);
                });
            } catch (Exception ignored) {
            }
        });
    }

    void runInBackground(Runnable r) {
        executor.execute(r);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
