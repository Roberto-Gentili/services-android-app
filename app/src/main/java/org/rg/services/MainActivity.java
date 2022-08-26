package org.rg.services;

import android.os.Bundle;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.rg.finance.Wallet;
import org.rg.services.ui.main.MainFragment;
import org.rg.services.ui.main.SettingsFragment;
import org.rg.util.LoggerChain;
import org.rg.util.RestTemplateSupplier;
import org.rg.util.Throwables;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MainActivity extends AppCompatActivity {

    public static class Model {
        public final static Map<String, Object> currentValues;
        public final static Map<String, Map<String, Map<String, Double>>> currentCoinValues;
        private final static DateTimeFormatter dateFormatter;
        public static boolean isReadyToBeShown;
        private static LocalDateTime lastUpdateTime;

        static {
            currentValues = new ConcurrentHashMap<>();
            currentCoinValues = new ConcurrentHashMap<>();
            dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/yyyy");
        }

        public static void setLastUpdateTime() {
            lastUpdateTime = LocalDateTime.now(ZoneId.systemDefault());
        }

        public static String getLastUpdateTimeAsString() {
            if (lastUpdateTime != null) {
                return dateFormatter.format(lastUpdateTime);
            }
            return null;
        }
    }

    public static class Engine {
        private static ExecutorService executorService;
        private static Supplier<Integer> executorServiceSupplierSizeSupplier;
        private static int currentExecutorServiceSize;
        private final static Collection<Wallet> wallets;

        static {
            wallets = new ArrayList<>();
        }

        public static <W extends Wallet> W getWallet(Class<W> type) {
            W wallet = (W)wallets.stream().filter(wlt -> wlt.getClass().equals(type)).findFirst().orElseGet(() -> null);
            if (wallet == null) {
                synchronized(wallets) {
                    if ((wallet = (W)wallets.stream().filter(wlt -> wlt.getClass().equals(type)).findFirst().orElseGet(() -> null)) == null) {
                        try {
                            wallets.add(
                                wallet = type.getDeclaredConstructor(RestTemplate.class, Supplier.class, String.class, String.class)
                                .newInstance(
                                    RestTemplateSupplier.getSharedInstance().get(),
                                    (Supplier<ExecutorService>)Engine::getExecutorService,
                                    null,
                                    null
                                )
                            );
                        } catch (Throwable exc) {
                            Throwables.sneakyThrow(exc);
                        }
                    }
                }
            }
            return wallet;
        }

        public static ExecutorService getExecutorService() {
            if (executorService == null || Engine.currentExecutorServiceSize != executorServiceSupplierSizeSupplier.get()) {
                synchronized (Engine.class) {
                    int currentExecutorServiceSize = executorServiceSupplierSizeSupplier.get();
                    if (executorService == null || Engine.currentExecutorServiceSize != currentExecutorServiceSize) {
                        ExecutorService oldExecutorService = executorService;
                        //ForkJoinPool.commonPool()
                        executorService = Executors.newFixedThreadPool(currentExecutorServiceSize);
                        Engine.currentExecutorServiceSize = currentExecutorServiceSize;
                        shutDown(oldExecutorService, () -> executorService);
                    }
                }
            }
            return executorService;
        }

        static void resetExecutorService() {
            ExecutorService oldExecutorService = null;
            synchronized (Engine.class) {
                oldExecutorService = executorService;
                executorService = null;
            }
            shutDown(oldExecutorService, Engine::getExecutorService);
        }

        private static void shutDown(ExecutorService toBeShuttedDown, Supplier<ExecutorService> executorSupplier) {
            if (toBeShuttedDown != null) {
                CompletableFuture.runAsync(() -> {
                    toBeShuttedDown.shutdown();
                }, executorSupplier.get());
            }
        }
    }




    public MainActivity() {
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8)  {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        Engine.executorServiceSupplierSizeSupplier = () ->
            getLongValueFromAppPreferencesOrDefault("threadPoolSize", R.integer.default_thread_pool_size).intValue();
        //RestTemplateSupplier.getSharedInstance().enableRequestLogger();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Consumer<String> logger = message -> {
            if (!message.toLowerCase().contains("invalid symbol")) {
                runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        };
        LoggerChain.getInstance().appendExceptionLogger(logger);
        LoggerChain.getInstance().appendInfoLogger(logger);
        Engine.resetExecutorService();
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            goToMainView();
        }

    }

    protected MainFragment getMainFragment(){
        return (MainFragment)getSupportFragmentManager().findFragmentById(R.id.container);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settingsMenuItem:
                goToSettingsView();
                break;
            case R.id.cryptoInfoMenuItem:
                goToMainView();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void goToSettingsView() {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.container, new SettingsFragment())
            .commit();
    }

    public void goToMainView() {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.container, new MainFragment())
            .commitNow();
    }

    public Long getLongValueFromAppPreferencesOrDefault(String valueName, int id) {
        Integer defaultValue = getResources().getInteger(id);
        String valueAsString = PreferenceManager.getDefaultSharedPreferences(this).getString(valueName, String.valueOf(defaultValue));
        try {
            return Long.valueOf(valueAsString);
        } catch (Throwable exc) {
            return defaultValue.longValue();
        }
    }
}