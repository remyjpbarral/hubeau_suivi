package fr.mondossier.debitsrivieres;

import android.os.Bundle;

import java.io.File;

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
    private ChartFragment chartFragment;
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
                    return new ChartFragment();
                case 2:
                    return new MapFragment();
                default:
                    return new FavoritesFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            org.osmdroid.config.Configuration.getInstance().setUserAgentValue("DebitsRivieres/1.0");
            File base = new File(getCacheDir(), "osmdroid");
            File tiles = new File(base, "tiles");
            if (!tiles.exists()) tiles.mkdirs();
            org.osmdroid.config.Configuration.getInstance().setOsmdroidBasePath(base);
            org.osmdroid.config.Configuration.getInstance().setOsmdroidTileCache(tiles);
        } catch (Exception ignored) {
        }
        setContentView(R.layout.activity_main);

        pager = findViewById(R.id.pager);
        pager.setAdapter(new TabsAdapter(this));
        pager.setOffscreenPageLimit(4);
        pager.setUserInputEnabled(false);

        TabLayout tabs = findViewById(R.id.tabs);
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            if (position == 0) tab.setText(R.string.tab_search);
            else if (position == 1) tab.setText(R.string.tab_chart);
            else if (position == 2) tab.setText(R.string.tab_map);
            else tab.setText(R.string.tab_favorites);
        }).attach();

        loadReferentiel();
        warmupFavoritesAndRecent();
    }

    void registerSearch(SearchFragment f) {
        this.searchFragment = f;
        f.refreshDisplay();
    }

    void registerChart(ChartFragment f) {
        this.chartFragment = f;
        f.refreshDisplay();
    }

    void registerMap(MapFragment f) {
        this.mapFragment = f;
        if (!referentielForMap.isEmpty()) f.setAllStations(referentielForMap, this::selectStation);
    }

    void registerFavorites(FavoritesFragment f) {
        this.favoritesFragment = f;
    }

    void goToSearch() {
        pager.setCurrentItem(0);
    }

    void goToChart() {
        pager.setCurrentItem(1);
    }

    void goToMap() {
        pager.setCurrentItem(1);
    }

    public void selectStation(HubEauClient.Station s) {
        currentStation = s;
        RecentStore.add(this, s);
        statusText = "";
        if (searchFragment != null) searchFragment.refreshDisplay();
        if (chartFragment != null) chartFragment.refreshDisplay();
        if (mapFragment != null && s.lat != null) mapFragment.focusStation(s);
        goToChart();
        loadDebits();
    }

    public void loadDebits() {
        if (currentStation == null) return;
        final String code = currentStation.code;
        final boolean tr = TEMPS_REEL[selectedPeriod];
        final long now = System.currentTimeMillis();
        final long start = now - PERIODS[selectedPeriod];
        List<HubEauClient.Observation> cached = DebitCache.getFresh(code);
        if (cached != null) {
            series.clear();
            series.put(currentStation.libelle, cached);
            statusText = "";
            if (chartFragment != null) chartFragment.refreshDisplay();
            return;
        }
        statusText = getString(R.string.loading);
        if (chartFragment != null) chartFragment.refreshDisplay();
        runInBackground(() -> {
            try {
                List<HubEauClient.Observation> obs = tr
                        ? HubEauClient.fetchDebitsTempsReel(code, start, now)
                        : HubEauClient.fetchDebitsJournaliers(code, start, now);
                DebitCache.put(code, obs);
                runOnUiThread(() -> {
                    series.clear();
                    series.put(currentStation.libelle, obs);
                    statusText = obs.isEmpty() ? getString(R.string.no_data) : "";
                    if (chartFragment != null) chartFragment.refreshDisplay();
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
            if (mapFragment != null) mapFragment.setAllStations(source, this::selectStation);
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
                    if (mapFragment != null) mapFragment.setAllStations(all, MainActivity.this::selectStation);
                });
            } catch (Exception ignored) {
            }
        });
    }

    void warmupZone(List<HubEauClient.Station> stations) {
        List<HubEauClient.Station> todo = new ArrayList<>();
        for (HubEauClient.Station s : stations) {
            if (DebitCache.getFresh(s.code) == null) todo.add(s);
            if (todo.size() >= 15) break;
        }
        if (todo.isEmpty()) return;
        final long period = PERIODS[selectedPeriod];
        final boolean tr = TEMPS_REEL[selectedPeriod];
        executor.execute(() -> {
            for (HubEauClient.Station s : todo) {
                try {
                    long now = System.currentTimeMillis();
                    List<HubEauClient.Observation> obs = tr
                            ? HubEauClient.fetchDebitsTempsReel(s.code, now - period, now)
                            : HubEauClient.fetchDebitsJournaliers(s.code, now - period, now);
                    DebitCache.put(s.code, obs);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void warmupFavoritesAndRecent() {
        List<HubEauClient.Station> targets = new ArrayList<>();
        for (HubEauClient.Station s : FavoritesStore.list(this)) targets.add(s);
        for (HubEauClient.Station s : RecentStore.list(this)) {
            boolean dup = false;
            for (HubEauClient.Station t : targets) if (t.code.equals(s.code)) dup = true;
            if (!dup) targets.add(s);
        }
        if (targets.isEmpty()) return;
        DebitCache.warmup(targets, executor, PERIODS[selectedPeriod], TEMPS_REEL[selectedPeriod]);
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
