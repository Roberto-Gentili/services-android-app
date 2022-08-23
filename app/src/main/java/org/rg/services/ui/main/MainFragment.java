package org.rg.services.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.rg.finance.BinanceWallet;
import org.rg.finance.CryptoComWallet;
import org.rg.finance.Wallet;
import org.rg.services.MainActivity;
import org.rg.services.R;
import org.rg.util.AsyncLooper;
import org.rg.util.LoggerChain;
import org.rg.util.RestTemplateSupplier;
import org.rg.util.Throwables;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.CharBuffer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class MainFragment extends Fragment {
    private SharedPreferences appPreferences;
    private final Collection<Wallet> wallets;
    private final DecimalFormatSymbols decimalFormatSymbols;
    private DecimalFormat numberFormatter;
    private DecimalFormat numberFormatterWithTwoVariableDecimals;
    private DecimalFormat numberFormatterWithFiveVariableDecimals;
    private BalanceUpdater balanceUpdater;
    private CoinViewManager coinViewManager;
    private CompletableFuture<String> gitHubUsernameSupplier;

    public MainFragment() {
        try {
            wallets = new ArrayList<>();
            decimalFormatSymbols = new DecimalFormatSymbols();
            decimalFormatSymbols.setGroupingSeparator('.');
            decimalFormatSymbols.setDecimalSeparator(',');
        } catch (Throwable exc) {
            exc.printStackTrace();
            throw exc;
        }
    }

    private boolean isStringNotEmpty(String value){
        return value != null && !value.trim().isEmpty();
    }

    private ExecutorService getExecutorService() {
        return ((MainActivity)getActivity()).getExecutorService();
    }

    private boolean isUseAlwaysTheDollarCurrencyForBalancesDisabled() {
        return !appPreferences.getBoolean("useAlwaysTheDollarCurrencyForBalances", false);
    }

    private synchronized void init() {
        stop();
        ((TextView) getView().findViewById(R.id.balanceLabel)).setVisibility(View.INVISIBLE);
        ((TextView) getView().findViewById(R.id.pureBalanceLabel)).setVisibility(View.INVISIBLE);
        ((TextView) getView().findViewById(R.id.balance)).setVisibility(View.INVISIBLE);
        ((TextView) getView().findViewById(R.id.pureBalance)).setVisibility(View.INVISIBLE);
        ((TextView) getView().findViewById(R.id.balanceCurrency)).setVisibility(View.INVISIBLE);
        ((TextView) getView().findViewById(R.id.pureBalanceCurrency)).setVisibility(View.INVISIBLE);
        ((TextView) getView().findViewById(R.id.lastUpdateLabel)).setVisibility(View.INVISIBLE);
        ((TextView) getView().findViewById(R.id.lastUpdate)).setVisibility(View.INVISIBLE);
        ((TextView) getView().findViewById(R.id.linkToReport)).setVisibility(View.INVISIBLE);
        ((Button) getView().findViewById(R.id.updateReportButton)).setVisibility(View.INVISIBLE);
        ((ScrollView) getView().findViewById(R.id.coinsView)).setVisibility(View.INVISIBLE);
        ((TextView) getView().findViewById(R.id.loadingDataAdvisor)).setVisibility(View.VISIBLE);
        ((ProgressBar) getView().findViewById(R.id.progressBar)).setVisibility(View.VISIBLE);
        String binanceApiKey = appPreferences.getString("binanceApiKey", null);
        String binanceApiSecret = appPreferences.getString("binanceApiSecret", null);
        String cryptoComApiKey = appPreferences.getString("cryptoComApiKey", null);
        String cryptoComApiSecret = appPreferences.getString("cryptoComApiSecret", null);
        wallets.clear();
        if (isStringNotEmpty(cryptoComApiKey) && isStringNotEmpty(cryptoComApiSecret)) {
            CryptoComWallet wallet = new CryptoComWallet(
                    RestTemplateSupplier.getSharedInstance().get(),
                    getExecutorService(),
                    cryptoComApiKey,
                    cryptoComApiSecret
            );
            wallet.setTimeOffset(Long.valueOf(
                    appPreferences.getString("cryptoComTimeOffset", "-1000")
            ));
            wallets.add(wallet);
        }
        if (isStringNotEmpty(binanceApiKey) && isStringNotEmpty(binanceApiSecret)) {
            BinanceWallet wallet = new BinanceWallet(
                    RestTemplateSupplier.getSharedInstance().get(),
                    getExecutorService(),
                    binanceApiKey,
                    binanceApiSecret
            );
            wallets.add(wallet);
        }
        numberFormatter = new DecimalFormat("#,##0.00", decimalFormatSymbols);
        numberFormatterWithTwoVariableDecimals = new DecimalFormat("#,##0.##", decimalFormatSymbols);
        numberFormatterWithFiveVariableDecimals = new DecimalFormat("#,##0.#####", decimalFormatSymbols);
        if (!wallets.isEmpty()) {
            activate();
        } else {
            ((MainActivity) getActivity()).goToSettingsView();
        }
        gitHubUsernameSupplier = CompletableFuture.supplyAsync(
            () -> {
                if (isStringNotEmpty(appPreferences.getString("gitHubAuthorizationToken", null))) {
                    return retrieveGitHubUsername();
                }
                return null;
            },
            getExecutorService()
        ).exceptionally(exc -> {
            LoggerChain.getInstance().logError("Unable to retrieve GitHub username: " + exc.getMessage());
            return null;
        });
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
                                            alreadyRunningChecker.wait(5000);
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
                            runOnUIThread(() -> {
                                ((ProgressBar) getView().findViewById(R.id.updateReportProgressBar)).setVisibility(View.INVISIBLE);
                                updateButton.setEnabled(true);
                                scheduler.shutdownNow();
                                String reportUrl = getResources().getString(R.string.reportUrl).replace(
                            "${username}",
                                    gitHubUsernameSupplier.join()
                                );
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl));
                                startActivity(browserIntent);
                            });
                        }, 30, TimeUnit.SECONDS);
                    } catch (Throwable exc) {
                        LoggerChain.getInstance().logError("Could not update report: " + exc.getMessage());
                        runOnUIThread(() -> {
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
            .pathSegment("repos")
            .pathSegment(gitHubUsernameSupplier.join())
            .pathSegment("services")
            .pathSegment("actions")
            .pathSegment("workflows")
            .build();
        ResponseEntity<Map> response = RestTemplateSupplier.getSharedInstance().get().exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Collection<String> workflowIds = ((Collection<Map<String, Object>>)response.getBody().get("workflows"))
            .stream().map(workflowInfo ->
                (Integer)((Map<String, Object>)workflowInfo).get("id")
            ).map(String::valueOf).collect(Collectors.toList());
        Supplier<Boolean> runningChecker = buildUpdateCryptoReportRunningChecker(gitHubActionToken, workflowIds);
        if (!runningChecker.get()) {
            String workflowId = ((Collection<Map<String, Object>>)response.getBody().get("workflows"))
                .stream().filter(wFInfo ->
                    ((String)wFInfo.get("path")).endsWith("/[R] update crypto report.yml")
                ).findFirst().map(workflowInfo ->
                    (Integer)((Map<String, Object>)workflowInfo).get("id")
                ).map(String::valueOf).orElseGet(() -> null);
            uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.github.com")
                    .pathSegment("repos")
                    .pathSegment(gitHubUsernameSupplier.join())
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

    private String retrieveGitHubUsername() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "token " + appPreferences.getString("gitHubAuthorizationToken", null));
        UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.github.com")
                .pathSegment("user")
                .build();
        ResponseEntity<Map> response = RestTemplateSupplier.getSharedInstance().get().exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return (String)response.getBody().get("login");
    }

    private Supplier<Boolean> buildUpdateCryptoReportRunningChecker(String gitHubActionToken, Collection<String> workflowIds) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "token " + gitHubActionToken);
        return () -> {
            for (String workflowId : workflowIds) {
                for (String status : new String[]{"requested", "queued", "in_progress"}) {
                    //Documentation at https://docs.github.com/en/rest/actions/workflow-runs#list-workflow-runs
                    UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.github.com")
                        .pathSegment("repos")
                        .pathSegment(gitHubUsernameSupplier.join())
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
        activate();
        super.onResume();
    }

    @Override
    public void onPause() {
        stop();
        super.onPause();
    }

    private synchronized void activate() {
        if (this.balanceUpdater == null) {
            balanceUpdater = new BalanceUpdater(this);
            balanceUpdater.activate();
        }
        if (this.coinViewManager == null) {
            coinViewManager = new CoinViewManager(this);
            coinViewManager.activate();
        }
    }

    private synchronized void stop() {
        BalanceUpdater balanceUpdater = this.balanceUpdater;
        if (balanceUpdater != null) {
            this.balanceUpdater = null;
            balanceUpdater.stop();
        }
        CoinViewManager coinViewManager = this.coinViewManager;
        if (coinViewManager != null) {
            this.coinViewManager = null;
            coinViewManager.stop();
        }
    }

    private void setHighlightedValue(TextView textView, DecimalFormat numberFormatter, Double newValue) {
        setHighlightedValue(textView, numberFormatter, newValue, false);
    }

    private void setHighlightedValue(TextView textView, DecimalFormat numberFormatter, Double newValue, boolean inverted) {
        synchronized (textView) {
            String previousValueAsString = String.valueOf(textView.getText());
            String currentValueAsString = numberFormatter.format(newValue);
            if (!previousValueAsString.isEmpty() && !previousValueAsString.equals(currentValueAsString)) {
                try {
                    Double previousValue = numberFormatter.parse(previousValueAsString).doubleValue();
                    if ((!inverted && newValue > previousValue) || (inverted && newValue < previousValue)) {
                        textView.setTextColor(Color.GREEN);
                    } else {
                        textView.setTextColor(Color.RED);
                    }
                } catch (ParseException e) {}
                textView.setText(currentValueAsString);
            } else {
                if (currentValueAsString.equals("NaN")) {
                    textView.setTypeface(textView.getTypeface(), Typeface.ITALIC);
                }
                textView.setTextColor(Color.WHITE);
            }
        }
    }

    private void setHighlightedValue(TextView textView, String newValue) {
        synchronized (textView) {
            String previousValueAsString = String.valueOf(textView.getText());
            if (!previousValueAsString.isEmpty() && !previousValueAsString.equals(newValue)) {
                textView.setTextColor(Color.CYAN);
                textView.setText(newValue);
            } else {
                textView.setText(newValue);
                textView.setTextColor(Color.WHITE);
            }
        }
    }

    private void runOnUIThread(Runnable action) {
        getActivity().runOnUiThread(() -> {
          try {
              action.run();
          } catch (Throwable exc) {
              LoggerChain.getInstance().logError(exc.getMessage());
          }
        });
    }

    private static class BalanceUpdater {
        private final MainFragment fragment;
        private AsyncLooper updateTask;

        private BalanceUpdater(MainFragment fragment) {
            this.fragment = fragment;
        }

        private synchronized void activate() {
            if (updateTask != null && updateTask.isAlive()) {
                return;
            }
            System.out.println("Wallet updater " + this + " activated");
            TextView balanceLabel = (TextView) fragment.getView().findViewById(R.id.balanceLabel);
            TextView pureBalanceLabel = (TextView) fragment.getView().findViewById(R.id.pureBalanceLabel);
            TextView balance = (TextView) fragment.getView().findViewById(R.id.balance);
            TextView pureBalance = (TextView) fragment.getView().findViewById(R.id.pureBalance);
            TextView balanceCurrency = (TextView) fragment.getView().findViewById(R.id.balanceCurrency);
            TextView pureBalanceCurrency = (TextView) fragment.getView().findViewById(R.id.pureBalanceCurrency);
            TextView lastUpdateLabel = (TextView) fragment.getView().findViewById(R.id.lastUpdateLabel);
            TextView lastUpdate = (TextView) fragment.getView().findViewById(R.id.lastUpdate);
            TextView linkToReport = (TextView) fragment.getView().findViewById(R.id.linkToReport);
            TextView loadingDataAdvisor = (TextView) fragment.getView().findViewById(R.id.loadingDataAdvisor);
            Button updateReportButton = (Button) fragment.getView().findViewById(R.id.updateReportButton);
            ProgressBar progressBar = (ProgressBar) fragment.getView().findViewById(R.id.progressBar);
            ScrollView coinsView = ((ScrollView) fragment.getView().findViewById(R.id.coinsView));
            updateTask = new AsyncLooper(() -> {
                try {
                    CoinViewManager coinViewManager = fragment.coinViewManager;
                    if (coinViewManager != null) {
                        if (coinViewManager.refresh()) {
                            this.fragment.runOnUIThread(() -> {
                                fragment.setHighlightedValue(balance, fragment.numberFormatter, fragment.coinViewManager.getAmount());
                                fragment.setHighlightedValue(pureBalance, fragment.numberFormatter, fragment.coinViewManager.getPureAmount());
                                fragment.setHighlightedValue(lastUpdate, ((MainActivity)fragment.getActivity()).getLastUpdateTimeAsString());
                                if (loadingDataAdvisor.getVisibility() != View.INVISIBLE) {
                                    if (coinViewManager.isCurrencyInEuro()) {
                                        balanceCurrency.setText("€");
                                        pureBalanceCurrency.setText("€");
                                    } else {
                                        balanceCurrency.setText("$");
                                        pureBalanceCurrency.setText("$");
                                    }
                                    loadingDataAdvisor.setVisibility(View.INVISIBLE);
                                    progressBar.setVisibility(View.INVISIBLE);
                                    balanceLabel.setVisibility(View.VISIBLE);
                                    balanceCurrency.setVisibility(View.VISIBLE);
                                    balance.setVisibility(View.VISIBLE);
                                    pureBalanceLabel.setVisibility(View.VISIBLE);
                                    pureBalanceCurrency.setVisibility(View.VISIBLE);
                                    pureBalance.setVisibility(View.VISIBLE);
                                    lastUpdateLabel.setVisibility(View.VISIBLE);
                                    lastUpdate.setVisibility(View.VISIBLE);
                                    coinsView.setVisibility(View.VISIBLE);
                                    if (fragment.gitHubUsernameSupplier.join() != null) {
                                        linkToReport.setMovementMethod(LinkMovementMethod.getInstance());
                                        String reportUrl = fragment.getResources().getString(R.string.reportUrl).replace(
                                                "${username}",
                                                fragment.gitHubUsernameSupplier.join()
                                        );
                                        linkToReport.setText(Html.fromHtml(String.valueOf(linkToReport.getText()).replace("&reportUrl;", reportUrl), Html.FROM_HTML_MODE_LEGACY));
                                        updateReportButton.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                fragment.updateReport((Button) view);
                                            }
                                        });
                                        linkToReport.setVisibility(View.VISIBLE);
                                        updateReportButton.setVisibility(View.VISIBLE);
                                    }
                                }
                            });
                        }
                    }
                } catch (Throwable exc) {
                    LoggerChain.getInstance().logError("Exception occurred: " + exc.getMessage());
                }
            }, fragment.getExecutorService()).atTheEndOfEveryIterationWaitFor(750L).activate();
        }

        private void stop() {
            AsyncLooper updateTask = null;
            synchronized (this) {
                updateTask = this.updateTask;
                if (updateTask == null) {
                    return;
                }
                System.out.println("Wallet updater " + this + " stop requested");
                this.updateTask = null;
            }
            updateTask.kill();
        }
    }

    private static class CoinViewManager {
        private static class HeaderLabel {
            private final static String COIN = "Coin";
            private final static String UP_IN_USDT = "U.P. in $";
            private final static String PPR_IN_USDT = "PPR in $";
            private final static String QUANTITY = "Quant.";
            private final static String AMOUNT_IN_USDT = "Am. in $";
            private final static String AMOUNT_IN_EURO = "Am. in €";
        }

        private final MainFragment fragment;
        private Map<String, Object> currentValues;
        private Map<String, Map<Wallet, Map<String, Double>>> currentCoinValues;
        private Collection<CompletableFuture<Collection<String>>> retrievingCoinValueTasks;
        private Collection<String> coinsToBeAlwaysDisplayed;
        private AsyncLooper retrievingCoinValuesTask;
        private Map<String, Integer> headerLabelsForSpaces;
        private boolean canBeRefreshed;
        private Double totalInvestment;

        private CoinViewManager(MainFragment fragment) {
            this.fragment = fragment;
            this.currentValues = new ConcurrentHashMap<>();
            this.currentCoinValues = new ConcurrentHashMap<>();
            this.coinsToBeAlwaysDisplayed = Arrays.asList(fragment.appPreferences.getString("coinsToBeAlwaysDisplayed", "BTC, ETH").toUpperCase().replace(" ", "").split(","));
            headerLabelsForSpaces = new LinkedHashMap<>();
            String totalInvestmentAsString = fragment.appPreferences.getString("totalInvestment", "0");
            if (!totalInvestmentAsString.isEmpty()) {
                totalInvestment = Double.valueOf(totalInvestmentAsString);
            }
        }

        private void setUpHeaderLabelsForSpaces() {
            headerLabelsForSpaces.put(HeaderLabel.COIN, 2);
            headerLabelsForSpaces.put(HeaderLabel.UP_IN_USDT, 2);
            if (totalInvestment != null) {
                headerLabelsForSpaces.put(HeaderLabel.PPR_IN_USDT, 2);
            }
            headerLabelsForSpaces.put(HeaderLabel.QUANTITY, 2);
            if (isCurrencyInEuro()) {
                headerLabelsForSpaces.put(HeaderLabel.AMOUNT_IN_EURO, 2);
            } else {
                headerLabelsForSpaces.put(HeaderLabel.AMOUNT_IN_USDT, 2);
            }
        }

        private synchronized void buildHeader() {
            TableLayout coinsTable = (TableLayout) fragment.getActivity().findViewById(R.id.coinsTableView);
            if (coinsTable.getChildAt(0) != null) {
                return;
            }
            for (Map.Entry<String, Integer> headerValue : headerLabelsForSpaces.entrySet()) {
                addHeaderColumn(headerValue.getKey(), headerValue.getValue());
            }
        }

        private int getIndexOfHeaderLabel(String label) {
            int index = 0;
            for (Map.Entry<String, Integer> headerValue : headerLabelsForSpaces.entrySet()) {
                if (headerValue.getKey().equals(label)) {
                    return index;
                }
                index++;
            }
            return -1;
        }

        private void setUnitPriceForCoinInDollar(String coinName, Double value) {
            setValueForCoin(coinName, value, getIndexOfHeaderLabel(HeaderLabel.UP_IN_USDT), fragment.numberFormatterWithFiveVariableDecimals);
        }

        private void setQuantityForCoin(String coinName, Double value) {
            setValueForCoin(coinName, value, getIndexOfHeaderLabel(HeaderLabel.QUANTITY), fragment.numberFormatterWithFiveVariableDecimals);
        }

        private void setAmountForCoin(String coinName, Double value) {
            int index = headerLabelsForSpaces.containsKey(HeaderLabel.AMOUNT_IN_EURO) ?
                getIndexOfHeaderLabel(HeaderLabel.AMOUNT_IN_EURO) : getIndexOfHeaderLabel(HeaderLabel.AMOUNT_IN_USDT);
            setValueForCoin(coinName, isCurrencyInEuro() ? value / getEuroValue() : value, index, fragment.numberFormatterWithTwoVariableDecimals);
        }

        private void setPPRForCoin(String coinName, Double value) {
            setValueForCoin(coinName, value, getIndexOfHeaderLabel(HeaderLabel.PPR_IN_USDT), fragment.numberFormatterWithFiveVariableDecimals, true);
        }

        private void setValueForCoin(String coinName, Double value, int columnIndex, DecimalFormat numberFormatter) {
            setValueForCoin(coinName, value, columnIndex, numberFormatter, false);
        }

        private void addHeaderColumn(String text) {
            addHeaderColumn(text, 0);
        }

        private void addHeaderColumn(String text, int emptySpaceCharCount) {
            fragment.runOnUIThread(() -> {
                TableLayout coinsTable = (TableLayout) fragment.getActivity().findViewById(R.id.coinsTableView);
                TableRow header = (TableRow) coinsTable.getChildAt(0);
                if (header == null) {
                    header = new TableRow(fragment.getActivity());
                    coinsTable.addView(header);
                }
                TextView textView = new TextView(fragment.getActivity());
                textView.setText(
                    text
                );
                textView.setTextSize(20F);
                textView.setPadding(20,0,0,0);
                textView.setTextColor(ResourcesCompat.getColor(fragment.getResources(), R.color.yellow, null));
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(null, Typeface.BOLD);
                header.addView(textView);
            });
        }

        private void setValueForCoin(String coinName, Double value, int columnIndex, DecimalFormat numberFormatter, boolean inverted) {
            fragment.runOnUIThread(() -> {
                TableLayout coinsTable = (TableLayout) fragment.getActivity().findViewById(R.id.coinsTableView);
                int childCount = coinsTable.getChildCount();
                TableRow row = getCoinRow(coinName);
                if (row == null) {
                    buildHeader();
                }
                if (row == null) {
                    row = new TableRow(fragment.getActivity());
                    TextView coinNameTextView = new TextView(fragment.getActivity());
                    coinNameTextView.setText(coinName);
                    coinNameTextView.setTextSize(16F);
                    coinNameTextView.setTextColor(Color.WHITE);
                    coinNameTextView.setGravity(Gravity.LEFT);
                    coinNameTextView.setTypeface(null, Typeface.BOLD);
                    row.addView(coinNameTextView);
                    coinsTable.addView(row);
                }
                TextView valueTextView = (TextView)row.getChildAt(columnIndex);
                if (valueTextView == null) {
                    for (int i = 1; i <= columnIndex; i++) {
                        valueTextView = (TextView)row.getChildAt(i);
                        if (valueTextView == null) {
                            valueTextView = new TextView(fragment.getActivity());
                            valueTextView.setTextSize(15F);
                            valueTextView.setGravity(Gravity.RIGHT);
                            valueTextView.setTextColor(Color.WHITE);
                            valueTextView.setPadding(20,0,0,0);
                            //((TextView)row.getChildAt(i-1)).setPadding(0,0,30,0);
                            row.addView(valueTextView);
                        }
                    }
                }
                fragment.setHighlightedValue(valueTextView, numberFormatter, value, inverted);
            });
        }

        private TableRow getCoinRow(String coinName) {
            TableLayout coinsTable = (TableLayout) fragment.getActivity().findViewById(R.id.coinsTableView);
            int childCount = coinsTable.getChildCount();
            if (childCount > 0) {
                for (int i = 0; i < childCount; i++) {
                    TableRow coinRow = (TableRow) coinsTable.getChildAt(i);
                    TextView coinNameTextView = (TextView) coinRow.getChildAt(0);
                    if (coinNameTextView.getText().equals(coinName)) {
                        return coinRow;
                    }
                }
            }
            return null;
        }

        private TableRow removeCoinRow(String coinName) {
            TableLayout coinsTable = (TableLayout) fragment.getActivity().findViewById(R.id.coinsTableView);
            TableRow coinRow = getCoinRow(coinName);
            if (coinRow != null) {
                coinsTable.removeView(coinRow);
            }
            return null;
        }

        public synchronized void activate() {
            if (retrievingCoinValuesTask == null) {
                System.out.println("Coin view manager " + this + " activated");
                retrievingCoinValuesTask = retrieveCoinValues();
            }
        }


        private void stop() {
            AsyncLooper retrievingCoinValuesTask = this.retrievingCoinValuesTask;
            synchronized (this) {
                if (retrievingCoinValuesTask == null) {
                    return;
                }
                this.retrievingCoinValuesTask = null;
            }
            retrievingCoinValuesTask.kill();
        }

        private AsyncLooper retrieveCoinValues() {
            Map<Wallet, CompletableFuture<Collection<String>>> coinToBeScannedSuppliers = new ConcurrentHashMap<>();
            launchCoinToBeScannedSuppliers(coinToBeScannedSuppliers);
            AsyncLooper coinsToBeScannedRetriever = new AsyncLooper(() -> {
                launchCoinToBeScannedSuppliers(coinToBeScannedSuppliers);
            }, fragment.getExecutorService()).atTheStartOfEveryIterationWaitFor(90000L);
            return new AsyncLooper(() -> {
                Collection<CompletableFuture<Collection<String>>> retrievingCoinValueTasks = new CopyOnWriteArrayList<>();
                for (Wallet wallet : fragment.wallets) {
                    retrievingCoinValueTasks.add(
                        CompletableFuture.supplyAsync(
                            () -> {
                                Collection<CompletableFuture<String>> innerTasks = new ArrayList<>();
                                for (String coinName : coinToBeScannedSuppliers.get(wallet).join()) {
                                    innerTasks.add(
                                        CompletableFuture.supplyAsync(
                                            () -> {
                                                Double unitPriceInDollar = wallet.getValueForCoin(coinName);
                                                Double quantity = wallet.getQuantityForCoin(coinName);
                                                Map<Wallet, Map<String, Double>> allCoinValues = currentCoinValues.computeIfAbsent(coinName, key -> new ConcurrentHashMap<>());
                                                Map<String, Double> coinValues = allCoinValues.computeIfAbsent(wallet, key -> new ConcurrentHashMap<>());
                                                coinValues.put("unitPrice", unitPriceInDollar);
                                                ((MainActivity)fragment.getActivity()).setLastUpdateTime();
                                                coinValues.put("quantity", quantity);
                                                ((MainActivity)fragment.getActivity()).setLastUpdateTime();
                                                return (String)null;
                                            },
                                            fragment.getExecutorService()
                                        ).exceptionally(exc -> {
                                            String exceptionMessage = wallet.getName() + " exception occurred: " + exc.getMessage();
                                            LoggerChain.getInstance().logError(exceptionMessage);
                                            return exceptionMessage;
                                        })
                                    );
                                }
                                return innerTasks.stream().map(CompletableFuture::join).filter(Objects::nonNull).collect(Collectors.toList());
                            },
                            fragment.getExecutorService()
                        )
                    );
                }
                this.retrievingCoinValueTasks = retrievingCoinValueTasks;
                retrievingCoinValueTasks.stream().forEach(CompletableFuture::join);
            }, fragment.getExecutorService())
            .whenStarted(coinsToBeScannedRetriever::activate)
            .whenKilled(coinsToBeScannedRetriever::kill)
            .atTheEndOfEveryIterationWaitFor(Long.valueOf(this.fragment.appPreferences.getString("intervalBetweenRequestGroups", "0")))
            .activate();
        }

        private void launchCoinToBeScannedSuppliers(Map<Wallet, CompletableFuture<Collection<String>>> coinSuppliers) {
            for (Wallet wallet : fragment.wallets) {
                CompletableFuture<Collection<String>> coinSupplier = coinSuppliers.get(wallet);
                if (coinSupplier == null) {
                    coinSuppliers.put(wallet, launchCoinToBeScannedSupplier(wallet));
                } else if (coinSupplier.isDone()) {
                    coinSupplier = launchCoinToBeScannedSupplier(wallet);
                    coinSupplier.join();
                    coinSuppliers.put(wallet, coinSupplier);
                }
            }
        }

        private CompletableFuture<Collection<String>> launchCoinToBeScannedSupplier(Wallet wallet) {
            return CompletableFuture.supplyAsync(() -> {
                while (true) {
                    try {
                        Collection<String> coinsToBeScanned = getCoinsToBeScanned(wallet);
                        //LoggerChain.getInstance().logInfo("Retrieved coins to be scanned for " + wallet.getName());
                        return coinsToBeScanned;
                    } catch (Throwable exc) {
                        LoggerChain.getInstance().logError("Unable to retrieve coins to be scanned: " + exc.getMessage());
                    }
                }
            }, fragment.getExecutorService());
        }

        @NonNull
        private Collection<String> getCoinsToBeScanned(Wallet wallet) {
            Collection<String> coinsForWallet = wallet.getOwnedCoins();
            coinsForWallet.addAll(coinsToBeAlwaysDisplayed);
            if (fragment.isUseAlwaysTheDollarCurrencyForBalancesDisabled()) {
                coinsForWallet.add("EUR");
            }
            return coinsForWallet;
        }

        public boolean refresh () {
            if (retrievingCoinValueTasks == null) {
                return false;
            }
            if (!canBeRefreshed) {
                if (retrievingCoinValueTasks.stream().map(CompletableFuture::join).filter(excMsgs -> !excMsgs.isEmpty()).count() > 0) {
                    setToNaNValuesIfNulls();
                    return false;
                }
            }
            Map<String, Map<Wallet, Map<String, Double>>> currentCoinValuesSnapshot = getCurrentCoinValuesSnapshot();
            Integer unitPriceRetrievingMode = Integer.valueOf(fragment.appPreferences.getString("unitPriceRetrievingMode", "3"));
            Supplier<Double> euroValueSupplier = null;
            Function<Map.Entry<String, Map<Wallet, Map<String, Double>>>, Map<String, Double>> valuesRetriever = null;
            if (unitPriceRetrievingMode == 1 || unitPriceRetrievingMode == 2) {
                BiPredicate<Double, Double> unitPriceTester = unitPriceRetrievingMode == 1 ?
                    (valueOne, valueTwo) -> valueOne < valueTwo :
                    (valueOne, valueTwo) -> valueOne > valueTwo;
                euroValueSupplier = () -> retrieveValuesWithMinMaxUnitPrice(currentCoinValuesSnapshot.get("EUR").values(), unitPriceTester).get("unitPrice");
                valuesRetriever = allCoinValues -> retrieveValuesWithMinMaxUnitPrice(allCoinValues.getValue().values(), unitPriceTester);
            } else if (unitPriceRetrievingMode == 3) {
                euroValueSupplier = () -> retrieveValuesWithAvgUnitPrice(currentCoinValuesSnapshot.get("EUR").values()).get("unitPrice");
                valuesRetriever = allCoinValues -> retrieveValuesWithAvgUnitPrice(allCoinValues.getValue().values());
            }
            Double euroValue = null;
            if (fragment.isUseAlwaysTheDollarCurrencyForBalancesDisabled() && currentCoinValuesSnapshot.get("EUR") != null) {
                setEuroValue(euroValue = euroValueSupplier.get());
            } else if (!fragment.isUseAlwaysTheDollarCurrencyForBalancesDisabled()) {
                setEuroValue(euroValue);
            }
            if (headerLabelsForSpaces.isEmpty()) {
                setUpHeaderLabelsForSpaces();
            }
            Double amount = 0D;
            Map<String, Map<String, Double>> allCoinsValues = new TreeMap<>();
            for (Map.Entry<String, Map<Wallet, Map<String, Double>>> allCoinValues : currentCoinValuesSnapshot.entrySet()) {
                Map<String, Double> values = valuesRetriever.apply(allCoinValues);
                Double coinQuantity = values.get("coinQuantity");
                Double coinAmount = values.get("coinAmount");
                Double unitPrice = values.get("unitPrice");
                if ((!coinAmount.isNaN() && (coinAmount > 0 || coinsToBeAlwaysDisplayed.contains(allCoinValues.getKey()))) ||
                    (fragment.appPreferences.getBoolean("showNaNAmounts", true) && coinQuantity != 0D)) {
                    setQuantityForCoin(allCoinValues.getKey(), coinQuantity);
                    setAmountForCoin(allCoinValues.getKey(), coinAmount);
                    amount += (!coinAmount.isNaN() ? coinAmount : 0D);
                    setUnitPriceForCoinInDollar(allCoinValues.getKey(), unitPrice);
                    allCoinsValues.put(allCoinValues.getKey(), values);
                } else if (coinAmount.isNaN()) {
                    removeCoinRow(allCoinValues.getKey());
                }
            }
            setAmount(amount);
            if (totalInvestment != null) {
                Double pureAmount = getPureAmountInDollar();
                Double currencyValue = isCurrencyInEuro() ? euroValue : 1D;
                for (Map.Entry<String, Map<String, Double>> allCoinValues : allCoinsValues.entrySet()) {
                    Map<String, Double> values = allCoinValues.getValue();
                    Double coinQuantity = values.get("coinQuantity");
                    Double coinAmount = values.get("coinAmount");
                    setPPRForCoin(allCoinValues.getKey(), coinAmount.isNaN() || coinAmount == 0D? Double.NaN :
                        (((((((totalInvestment + 1D) * 100D) / 99.9D) + 1D) * 100D) / 99.6) - ((amount - coinAmount) / currencyValue)) / coinQuantity
                    );
                }
            }
            return canBeRefreshed = true;
        }

        private Map<String, Double> retrieveValuesWithMinMaxUnitPrice(Collection<Map<String, Double>> allCoinValues, BiPredicate<Double, Double> unitPriceTester) {
            Double coinQuantity = 0D;
            Double coinAmount = 0D;
            Double unitPrice = null;
            for (Map<String, Double> coinValues : allCoinValues) {
                Double coinQuantityForCoinInWallet = coinValues.get("quantity");
                Double unitPriceForCoinInWallet = coinValues.get("unitPrice");
                if (unitPrice == null || unitPrice.isNaN() || unitPriceTester.test(unitPriceForCoinInWallet, unitPrice)) {
                    unitPrice = unitPriceForCoinInWallet;
                }
                coinQuantity = sum(coinQuantity, coinQuantityForCoinInWallet);
            }
            coinAmount += coinQuantity * unitPrice;
            if (coinAmount != 0D && coinQuantity != 0D) {
                unitPrice = coinAmount / coinQuantity;
            } else if (unitPrice == 0D) {
                coinAmount = unitPrice = Double.NaN;
            }
            Map<String, Double> values = new HashMap<>();
            values.put("coinQuantity", coinQuantity);
            values.put("coinAmount", coinAmount);
            values.put("unitPrice", unitPrice);
            return values;
        }

        private Map<String, Map<Wallet, Map<String, Double>>> getCurrentCoinValuesSnapshot() {
            Map<String, Map<Wallet, Map<String, Double>>> currentCoinValuesSnapshot =
                !canBeRefreshed ?
                    new TreeMap<>() :
                    new ConcurrentHashMap<>();
            for (Map.Entry<String, Map<Wallet, Map<String, Double>>> allCoinValues : currentCoinValues.entrySet()) {
                Map<Wallet, Map<String, Double>> coinValuesForWallets = new HashMap<>();
                currentCoinValuesSnapshot.put(allCoinValues.getKey(), coinValuesForWallets);
                for (Map.Entry<Wallet, Map<String, Double>> coinValuesForWallet : allCoinValues.getValue().entrySet()) {
                    coinValuesForWallets.put(coinValuesForWallet.getKey(), new HashMap<>(coinValuesForWallet.getValue()));
                }
            }
            return currentCoinValuesSnapshot;
        }

        private Map<String, Double> retrieveValuesWithAvgUnitPrice(Collection<Map<String, Double>> allCoinValues) {
            Double coinQuantity = 0D;
            Double coinAmount = 0D;
            Double unitPrice = 0D;
            for (Map<String, Double> coinValues : allCoinValues) {
                Double coinQuantityForCoinInWallet = coinValues.get("quantity");
                Double unitPriceForCoinInWallet = coinValues.get("unitPrice");
                unitPrice = sum(unitPrice, unitPriceForCoinInWallet);
                coinQuantity = sum(coinQuantity, coinQuantityForCoinInWallet);
                coinAmount = sum(coinAmount, coinQuantityForCoinInWallet * unitPriceForCoinInWallet);
            }
            if (coinAmount != 0D && coinQuantity != 0D) {
                unitPrice = coinAmount / coinQuantity;
            } else if (unitPrice == 0D) {
                coinAmount = unitPrice = Double.NaN;
            } else {
                unitPrice /= allCoinValues.stream().filter(map -> map.get("unitPrice") > 0D).count();
            }
            Map<String, Double> values = new HashMap<>();
            values.put("coinQuantity", coinQuantity);
            values.put("coinAmount", coinAmount);
            values.put("unitPrice", unitPrice);
            return values;
        }

        private void setToNaNValuesIfNulls() {
            if (getAmountInDollar() == null) {
                setAmount(Double.NaN);
            }
            if (getEuroValue() == null) {
                setEuroValue(Double.NaN);
            }
        }

        private Double sum(Double a, Double b) {
            return a.isNaN()?
                b :
                b.isNaN()?
                    a :
                    a + b;
        }

        public Double getAmount() {
            Double amountInDollar = getAmountInDollar();
            Double euroValue = getEuroValue();
            if (isCurrencyInEuro()) {
                return amountInDollar / euroValue;
            }
            return amountInDollar;
        }

        public Double getAmountInDollar() {
            return (Double)currentValues.get("amount");
        }

        public Double getAmountInEuro() {
            Double euroValue = getEuroValue();
            if (euroValue != null && !euroValue.isNaN()) {
                return getAmountInDollar() / euroValue;
            }
            return null;
        }

        public Double getEuroValue() {
            return (Double)currentValues.get("euroValue");
        }

        public Double getPureAmountInDollar() {
            Double amount = getAmountInDollar();
            Double eurValue = getEuroValue();
            return ((((((amount * 99.6D) / 100D) - 1D) * 99.9D) / 100D) - (eurValue != null && !eurValue.isNaN() ? eurValue : 1D));
        }

        public Double getPureAmount() {
            Double amount = getAmountInDollar();
            Double eurValue = getEuroValue();
            Double currencyUnit = (eurValue != null && !eurValue.isNaN() ? eurValue : 1D);
            return ((((((amount * 99.6D) / 100D) - 1D) * 99.9D) / 100D) - currencyUnit) / currencyUnit;
        }

        public boolean isCurrencyInEuro() {
            Double eurValue = getEuroValue();
            return fragment.isUseAlwaysTheDollarCurrencyForBalancesDisabled() && eurValue != null && !eurValue.isNaN();
        }

        private void setEuroValue(Double value) {
            if (value != null) {
                currentValues.put("euroValue", value);
            } else {
                currentValues.remove("euroValue");
            }
        }

        private void setAmount(Double value) {
            currentValues.put("amount", value);
        }

    }

}