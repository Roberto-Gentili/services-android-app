package org.rg.services.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.rg.finance.BinanceWallet;
import org.rg.finance.CryptoComWallet;
import org.rg.finance.Wallet;
import org.rg.services.MainActivity;
import org.rg.services.R;
import org.rg.util.LoggerChain;
import org.rg.util.RestTemplateSupplier;
import org.rg.util.Throwables;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class MainFragment extends Fragment {
    private SharedPreferences appPreferences;
    private static MainFragment INSTANCE;
    private final Collection<Wallet> wallets;
    private final ExecutorService executorService;
    private BalanceUpdater balanceUpdater;
    private final DecimalFormatSymbols decimalFormatSymbols;
    private DecimalFormat numberFormatter;
    private DecimalFormat numberFormatterWithFourDecimals;
    private final DateTimeFormatter dateFormatter;
    private Supplier<Double> eurValueSupplier;
    private CoinViewManager coinViewManager;

    private MainFragment() {
        try {
            int SDK_INT = android.os.Build.VERSION.SDK_INT;
            if (SDK_INT > 8)  {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                        .permitAll().build();
                StrictMode.setThreadPolicy(policy);
            }
            executorService = ForkJoinPool.commonPool();
            wallets = new ArrayList<>();
            //executorService = Executors.newFixedThreadPool(4);
            //executorService = Executors.newSingleThreadExecutor();
            decimalFormatSymbols = new DecimalFormatSymbols();
            decimalFormatSymbols.setGroupingSeparator('.');
            decimalFormatSymbols.setDecimalSeparator(',');
            dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/YYYY");
        } catch (Throwable exc) {
            exc.printStackTrace();
            throw exc;
        }
    }

    public static MainFragment getInstance() {
        if (INSTANCE == null) {
            synchronized (MainFragment.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MainFragment();
                }
            }
        }
        return INSTANCE;
    }

    private boolean isStringNotEmpty(String value){
        return value != null && !value.trim().isEmpty();
    }

    private synchronized void init() {
        stopBalanceUpdating();
        ((TextView)getView().findViewById(R.id.balanceLabel)).setVisibility(View.INVISIBLE);
        ((TextView)getView().findViewById(R.id.pureBalanceLabel)).setVisibility(View.INVISIBLE);
        ((TextView)getView().findViewById(R.id.balance)).setVisibility(View.INVISIBLE);
        ((TextView)getView().findViewById(R.id.pureBalance)).setVisibility(View.INVISIBLE);
        ((TextView)getView().findViewById(R.id.updateTime)).setVisibility(View.INVISIBLE);
        ((TextView)getView().findViewById(R.id.linkToReport)).setVisibility(View.INVISIBLE);
        ((Button)getView().findViewById(R.id.updateReportButton)).setVisibility(View.INVISIBLE);
        ((ScrollView)getView().findViewById(R.id.mainScrollView)).setVisibility(View.INVISIBLE);
        ((TextView)getView().findViewById(R.id.loadingDataAdvisor)).setVisibility(View.VISIBLE);
        ((ProgressBar)getView().findViewById(R.id.progressBar)).setVisibility(View.VISIBLE);
        String binanceApiKey = appPreferences.getString("binanceApiKey", null);
        String binanceApiSecret = appPreferences.getString("binanceApiSecret", null);
        String cryptoComApiKey = appPreferences.getString("cryptoComApiKey", null);
        String cryptoComApiSecret = appPreferences.getString("cryptoComApiSecret", null);
        wallets.clear();
        if (isStringNotEmpty(cryptoComApiKey) && isStringNotEmpty(cryptoComApiSecret)) {
            CryptoComWallet wallet = new CryptoComWallet(
                RestTemplateSupplier.getSharedInstance().get(),
                executorService,
                cryptoComApiKey,
                cryptoComApiSecret
            );
            wallet.setTimeOffset(Long.valueOf(
                appPreferences.getString("cryptoComTimeOffset", "-1000")
            ));
            wallets.add(wallet);
        }
        eurValueSupplier = null;
        if (isStringNotEmpty(binanceApiKey) && isStringNotEmpty(binanceApiSecret)) {
            BinanceWallet wallet = new BinanceWallet(
                RestTemplateSupplier.getSharedInstance().get(),
                executorService,
                binanceApiKey,
                binanceApiSecret
            );
            wallets.add(wallet);
            eurValueSupplier = () -> wallet.getValueForCoin("EUR");
        }
        numberFormatter = new DecimalFormat("#,##0.00", decimalFormatSymbols);
        numberFormatterWithFourDecimals = new DecimalFormat("#,##0.0000", decimalFormatSymbols);
        if (!wallets.isEmpty()) {
            startBalanceUpdating();
        } else {
            ((MainActivity)getActivity()).goToSettingsView();
        }
        coinViewManager = new CoinViewManager(this);
    }

    public boolean canRun() {
        String binanceApiKey = appPreferences.getString("binanceApiKey", null);
        String binanceApiSecret = appPreferences.getString("binanceApiSecret", null);
        String cryptoComApiKey = appPreferences.getString("cryptoComApiKey", null);
        String cryptoComApiSecret = appPreferences.getString("cryptoComApiSecret", null);
        return (isStringNotEmpty(cryptoComApiKey) && isStringNotEmpty(cryptoComApiSecret) || (isStringNotEmpty(binanceApiKey) && isStringNotEmpty(binanceApiSecret)));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        appPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
        init();
        Button updateButton = (Button)view.findViewById(R.id.updateReportButton);
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateReport((Button)view);
            }
        });
    }

    public void updateReport(Button updateButton) {
        if (updateButton.isEnabled()) {
            synchronized (updateButton) {
                if (updateButton.isEnabled()) {
                    updateButton.setEnabled(false);
                    ((ProgressBar) getView().findViewById(R.id.updateReportProgressBar)).setVisibility(View.VISIBLE);
                    try {
                        Supplier<Boolean> alreadyRunningChecker = launchCryptoReportUpdate();
                        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
                        scheduler.schedule(() -> {
                            int remainedAttempts = 12;
                            while (remainedAttempts >= 0) {
                                try {
                                    if (alreadyRunningChecker.get()) {
                                        synchronized (alreadyRunningChecker) {
                                            try {
                                                alreadyRunningChecker.wait(5000);
                                            } catch (InterruptedException exc) {

                                            }
                                        }
                                    } else {
                                        break;
                                    }
                                } catch (Throwable exc) {
                                    remainedAttempts--;
                                }
                            }
                            if (remainedAttempts < 0) {
                                LoggerChain.getInstance().logError("Maximum number of attempts reached");
                            }
                            getActivity().runOnUiThread(() -> {
                                ((ProgressBar) getView().findViewById(R.id.updateReportProgressBar)).setVisibility(View.INVISIBLE);
                                updateButton.setEnabled(true);
                                scheduler.shutdownNow();
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.reportUrl)));
                                startActivity(browserIntent);
                            });
                        }, 30, TimeUnit.SECONDS);
                    } catch (Throwable exc) {
                        LoggerChain.getInstance().logError("Could not update report: " + exc.getMessage());
                        getActivity().runOnUiThread(() -> {
                            updateButton.setEnabled(true);
                        });
                        return;
                    }
                }
            }
        }
    }

    private Supplier<Boolean> launchCryptoReportUpdate() {
        String gitHubActionToken = appPreferences.getString("gitHubAuthorizationToken", null);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "token " + gitHubActionToken);
        UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.github.com")
            .pathSegment("user")
            .build();
        ResponseEntity<Map> response = RestTemplateSupplier.getSharedInstance().get().exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        String username = (String)response.getBody().get("login");
        uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.github.com")
            .pathSegment("repos")
            .pathSegment(username)
            .pathSegment("services")
            .pathSegment("actions")
            .pathSegment("workflows")
            .build();
        response = RestTemplateSupplier.getSharedInstance().get().exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Collection<String> workflowIds = ((Collection<Map<String, Object>>)response.getBody().get("workflows"))
            .stream().map(workflowInfo ->
                (Integer)((Map<String, Object>)workflowInfo).get("id")
            ).map(String::valueOf).collect(Collectors.toList());
        Supplier<Boolean> runningChecker = buildUpdateCryptoReportRunningChecker(gitHubActionToken, username, workflowIds);
        if (!runningChecker.get()) {
            String workflowId = ((Collection<Map<String, Object>>)response.getBody().get("workflows"))
                .stream().filter(wFInfo ->
                    ((String)wFInfo.get("path")).endsWith("/[R] update crypto report.yml")
                ).findFirst().map(workflowInfo ->
                    (Integer)((Map<String, Object>)workflowInfo).get("id")
                ).map(String::valueOf).orElseGet(() -> null);
            uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.github.com")
                    .pathSegment("repos")
                    .pathSegment(username)
                    .pathSegment("services")
                    .pathSegment("actions")
                    .pathSegment("workflows")
                    .pathSegment(workflowId)
                    .pathSegment("dispatches")
                    .build();
            Map<String, Object> requestBody = new LinkedHashMap<>();
            try {
                requestBody.put("ref", "main");
                if (canRun()) {
                    Map<String, Object> inputs = new LinkedHashMap<>();
                    requestBody.put("inputs", inputs);
                    String binanceApiKey = appPreferences.getString("binanceApiKey", null);
                    String binanceApiSecret = appPreferences.getString("binanceApiSecret", null);
                    String cryptoComApiKey = appPreferences.getString("cryptoComApiKey", null);
                    String cryptoComApiSecret = appPreferences.getString("cryptoComApiSecret", null);
                    if (isStringNotEmpty(cryptoComApiKey) && isStringNotEmpty(cryptoComApiSecret)) {
                        inputs.put("cryptoComApiKey", cryptoComApiKey);
                        inputs.put("cryptoComApiSecret", cryptoComApiSecret);
                    }
                    if (isStringNotEmpty(binanceApiKey) && isStringNotEmpty(binanceApiSecret)) {
                        inputs.put("binanceApiKey", binanceApiKey);
                        inputs.put("binanceApiSecret", binanceApiSecret);
                    }
                }
            } catch (Throwable exc) {
                Throwables.sneakyThrow(exc);
            }
            RestTemplateSupplier.getSharedInstance().get().exchange(uriComponents.toString(), HttpMethod.POST, new HttpEntity<Map<String, Object>>(requestBody, headers), Map.class);
        }
        return runningChecker;
    }

    private Supplier<Boolean> buildUpdateCryptoReportRunningChecker(String gitHubActionToken, String username, Collection<String> workflowIds) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "token " + gitHubActionToken);
        return () -> {
            for (String workflowId : workflowIds) {
                for (String status : new String[]{"requested", "queued", "in_progress"}) {
                    //Documentation at https://docs.github.com/en/rest/actions/workflow-runs#list-workflow-runs
                    UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.github.com")
                        .pathSegment("repos")
                        .pathSegment(username)
                        .pathSegment("services")
                        .pathSegment("actions")
                        .pathSegment("workflows")
                        .pathSegment(workflowId)
                        .pathSegment("runs")
                        .queryParam("status", status)
                        .build();
                    Map<String, Object> responseBody = RestTemplateSupplier.getSharedInstance().get().exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
                    if (((int)responseBody.get("total_count")) > 0) {
                        return true;
                    }
                }
            }
            return false;
        };
    }

    @Override
    public void onResume() {
        startBalanceUpdating();
        super.onResume();
    }

    @Override
    public void onPause() {
        stopBalanceUpdating();
        super.onPause();
    }

    private void startBalanceUpdating() {
        synchronized(this) {
            if (this.balanceUpdater == null) {
                balanceUpdater = new BalanceUpdater(this);
                balanceUpdater.activate();
            }
        }
    }

    private void stopBalanceUpdating() {
        synchronized(this) {
            BalanceUpdater balanceUpdater = this.balanceUpdater;
            if (balanceUpdater != null) {
                this.balanceUpdater = null;
                balanceUpdater.stop();
            }
        }
    }

    private static class BalanceUpdater {
        private boolean isAlive;
        private final MainFragment fragment;

        private BalanceUpdater(MainFragment fragment) {
            this.fragment = fragment;
        }

        private void activate() {
            System.out.println("Wallet updater " + this + " activated");
            isAlive = true;
            TextView balanceLabel = (TextView) fragment.getView().findViewById(R.id.balanceLabel);
            TextView pureBalanceLabel = (TextView) fragment.getView().findViewById(R.id.pureBalanceLabel);
            TextView balance = (TextView) fragment.getView().findViewById(R.id.balance);
            TextView pureBalance = (TextView) fragment.getView().findViewById(R.id.pureBalance);
            TextView updateTime = (TextView) fragment.getView().findViewById(R.id.updateTime);
            TextView linkToReport = (TextView) fragment.getView().findViewById(R.id.linkToReport);
            TextView loadingDataAdvisor = (TextView) fragment.getView().findViewById(R.id.loadingDataAdvisor);
            Button updateReportButton = (Button) fragment.getView().findViewById(R.id.updateReportButton);
            ProgressBar progressBar = (ProgressBar) fragment.getView().findViewById(R.id.progressBar);
            ScrollView coinsView = ((ScrollView) fragment.getView().findViewById(R.id.mainScrollView));
            CompletableFuture.runAsync(() -> {
                System.out.println("Wallet updater " + this + " starting");
                while (isAlive) {
                    try {
                        fragment.coinViewManager.refresh();
                        Double eurValue = fragment.coinViewManager.getEuroValue();
                        Double summedCoinAmountInUSDT = fragment.coinViewManager.getAmountInDollar();
                        Double amount = eurValue != null ? summedCoinAmountInUSDT / eurValue : summedCoinAmountInUSDT;
                        Double pureAmount = ((((((summedCoinAmountInUSDT * 99.6D) / 100D) - 1D) * 99.9D) / 100D) - (eurValue != null ? eurValue : 1D)) / (eurValue != null ? eurValue : 1D);
                        this.fragment.getActivity().runOnUiThread(() -> {
                            balance.setText(fragment.numberFormatter.format(amount) + (eurValue != null? " €" : " $"));
                            pureBalance.setText(fragment.numberFormatter.format(pureAmount) + (eurValue != null? " €" : " $"));
                            updateTime.setText("Last update: " + fragment.coinViewManager.getUpdateTime().format(fragment.dateFormatter));
                            loadingDataAdvisor.setVisibility(View.INVISIBLE);
                            progressBar.setVisibility(View.INVISIBLE);
                            balanceLabel.setVisibility(View.VISIBLE);
                            balance.setVisibility(View.VISIBLE);
                            pureBalanceLabel.setVisibility(View.VISIBLE);
                            pureBalance.setVisibility(View.VISIBLE);
                            updateTime.setVisibility(View.VISIBLE);
                            linkToReport.setMovementMethod(LinkMovementMethod.getInstance());
                            if (fragment.isStringNotEmpty(fragment.appPreferences.getString("gitHubAuthorizationToken", null))) {
                                linkToReport.setVisibility(View.VISIBLE);
                                updateReportButton.setVisibility(View.VISIBLE);
                            }
                            coinsView.setVisibility(View.VISIBLE);
                        });
                    } catch (Throwable exc) {

                    }
                    synchronized (this) {
                        try {
                            this.wait(Long.valueOf(
                                    this.fragment.appPreferences.getString("intervalBetweenRequestGroups", "0")
                            ));
                        } catch (InterruptedException exc) {
                            exc.printStackTrace();
                        }
                    }
                }
                System.out.println("Wallet updater " + this + " stopped");
            });
        }

        private void stop() {
            System.out.println("Wallet updater " + this + " stop requested");
            isAlive = false;
        }
    }

    private static class CoinViewManager {
        private final MainFragment fragment;
        private List<String> headerLabels;
        private AtomicReference<Double> eurValueWrapper;
        private Double amount;
        private LocalDateTime updateTime;

        private CoinViewManager(MainFragment fragment) {
            this.fragment = fragment;
            eurValueWrapper = new AtomicReference<Double>();
            Collection<String> ownedCoins = new TreeSet<>();
            Collection<CompletableFuture<Collection<String>>> tasks = new ArrayList<>();
            for (Wallet wallet : fragment.wallets) {
                tasks.add(
                    CompletableFuture.supplyAsync(
                            () -> {
                                return wallet.getOwnedCoins();
                            },
                            fragment.executorService
                    ).exceptionally(exc -> {
                        if (!(exc instanceof HttpClientErrorException)) {
                            LoggerChain.getInstance().logError(wallet.getClass().getSimpleName() + " exception occurred: " + exc.getMessage());
                        }
                        return Throwables.sneakyThrow(exc);
                    })
                );
            }
            tasks.stream().map(CompletableFuture::join).forEach(ownedCoins::addAll);
            for (String coinName : ownedCoins) {
                setQuantityForCoin(coinName, 0D);
            }
        }

        private synchronized void buildHeader() {
            TableLayout coinsTable = (TableLayout) fragment.getActivity().findViewById(R.id.mainHorizontalScrollViewLayoutTable);
            if (coinsTable.getChildAt(0) != null) {
                return;
            }
            addHeaderColumn("Coin");
            addHeaderColumn("Quant.");
            addHeaderColumn("U.P. in $");
            addHeaderColumn("Am. in $");
            if (fragment.eurValueSupplier != null) {
                addHeaderColumn("Am. in €");
            }
        }

        private void addHeaderColumn(String text) {
            fragment.getActivity().runOnUiThread(() -> {
                TableLayout coinsTable = (TableLayout) fragment.getActivity().findViewById(R.id.mainHorizontalScrollViewLayoutTable);
                TableRow header = (TableRow) coinsTable.getChildAt(0);
                if (header == null) {
                    header = new TableRow(fragment.getActivity());
                    coinsTable.addView(header);
                }
                TextView textView = new TextView(fragment.getActivity());
                textView.setText("   " + text + "   ");
                textView.setTextSize(16F);
                float siz = textView.getTextSize();
                textView.setTextColor(Color.YELLOW);
                textView.setTypeface(null, Typeface.BOLD);
                header.addView(textView);
            });
        }

        private void setQuantityForCoin(String coinName, Double value) {
            setValueForCoin(coinName, value, 1, fragment.numberFormatter);
        }

        private void setUnitPriceForCoinInDollar(String coinName, Double value) {
            setValueForCoin(coinName, value, 2, fragment.numberFormatterWithFourDecimals);
        }

        private void setAmountForCoinInDollar(String coinName, Double value) {
            setValueForCoin(coinName, value, 3, fragment.numberFormatter);
        }

        private void setAmountForCoinInEuro(String coinName, Double value) {
            setValueForCoin(coinName, value, 4, fragment.numberFormatter);
        }

        private void setValueForCoin(String coinName, Double value, int columnIndex, DecimalFormat numberFormatter) {
            fragment.getActivity().runOnUiThread(() -> {
                TableLayout coinsTable = (TableLayout) fragment.getActivity().findViewById(R.id.mainHorizontalScrollViewLayoutTable);
                int childCount = coinsTable.getChildCount();
                TableRow row = null;
                if (childCount > 0) {
                    for (int i = 0; i < childCount; i++) {
                        TableRow tempRow = (TableRow) coinsTable.getChildAt(i);
                        TextView coinNameTextView = (TextView) tempRow.getChildAt(0);
                        if (coinNameTextView.getText().equals(coinName)) {
                            row = tempRow;
                            break;
                        }
                    }
                } else {
                    buildHeader();
                }
                if (row == null) {
                    row = new TableRow(fragment.getActivity());
                    TextView coinNameTextView = new TextView(fragment.getActivity());
                    coinNameTextView.setText(coinName);
                    coinNameTextView.setTextColor(Color.WHITE);
                    coinNameTextView.setGravity(Gravity.LEFT);
                    row.addView(coinNameTextView);
                    coinsTable.addView(row);
                }
                TextView valueTextView = (TextView) row.getChildAt(columnIndex);
                if (valueTextView == null) {
                    for (int i = 1; i <= columnIndex; i++) {
                        valueTextView = new TextView(fragment.getActivity());
                        valueTextView.setGravity(Gravity.RIGHT);
                        valueTextView.setTextColor(Color.WHITE);
                        row.addView(valueTextView);
                    }
                    valueTextView = (TextView) row.getChildAt(columnIndex);
                }
                valueTextView.setText(numberFormatter.format(value));
            });
        }

        public void refresh () {
            updateTime = LocalDateTime.now(ZoneId.of("Europe/Rome"));
            if (fragment.eurValueSupplier != null) {
                eurValueWrapper.set(null);
            }
            Collection<CompletableFuture<Double>> tasks = new ArrayList<>();
            for (Wallet wallet : fragment.wallets) {
                tasks.add(
                    CompletableFuture.supplyAsync(
                        () -> {
                            Collection<CompletableFuture<Double>> innerTasks = new ArrayList<>();
                            for (String coinName : wallet.getOwnedCoins()) {
                                innerTasks.add(CompletableFuture.supplyAsync(() -> {
                                        Double quantity = wallet.getQuantityForCoin(coinName);
                                        Double unitPriceInDollar = wallet.getValueForCoin(coinName);
                                        setQuantityForCoin(coinName, quantity);
                                        setUnitPriceForCoinInDollar(coinName, unitPriceInDollar);
                                        setAmountForCoinInDollar(coinName, quantity * unitPriceInDollar);
                                        if (fragment.eurValueSupplier != null) {
                                            if (eurValueWrapper.get() == null) {
                                                synchronized (fragment.eurValueSupplier) {
                                                    if (eurValueWrapper.get() == null) {
                                                        eurValueWrapper.set(fragment.eurValueSupplier.get());
                                                    }
                                                }
                                            }
                                            Double eurValue = eurValueWrapper.get();

                                            setAmountForCoinInEuro(coinName, (quantity * unitPriceInDollar) / eurValue);
                                        }
                                        return unitPriceInDollar * quantity;
                                    },
                                    fragment.executorService)
                                );
                            }
                            return innerTasks.stream().mapToDouble(CompletableFuture::join).sum();
                        },
                        fragment.executorService
                    ).exceptionally(exc -> {
                        if (!(exc instanceof HttpClientErrorException)) {
                            LoggerChain.getInstance().logError(wallet.getClass().getSimpleName() + " exception occurred: " + exc.getMessage());
                        }
                        return Throwables.sneakyThrow(exc);
                    })
                );
            }
            amount = tasks.stream().mapToDouble(CompletableFuture::join).sum();
        }

        public Double getAmountInDollar() {
            return amount;
        }

        public Double getAmountInEuro() {
            Double eurValue = eurValueWrapper.get();
            if (eurValue != null) {
                return amount / eurValue;
            }
            return null;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public Double getEuroValue() {
            return eurValueWrapper.get();
        }
    }
}