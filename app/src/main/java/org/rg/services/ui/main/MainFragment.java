package org.rg.services.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
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
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class MainFragment extends Fragment {
    SharedPreferences appPreferences;
    final Collection<Wallet> wallets;
    final DecimalFormatSymbols decimalFormatSymbols;
    DecimalFormat numberFormatterWithTwoDecimals;
    DecimalFormat numberFormatterWithSignAndTwoDecimals;
    DecimalFormat numberFormatterWithTwoVariableDecimals;
    DecimalFormat numberFormatterWithFiveVariableDecimals;
    BalanceUpdater balanceUpdater;
    CoinViewManager coinViewManager;
    CompletableFuture<String> gitHubUsernameSupplier;


    public MainFragment() {
        try {
            wallets = new ArrayList<>();
            decimalFormatSymbols = new DecimalFormatSymbols();
            decimalFormatSymbols.setGroupingSeparator('.');
            decimalFormatSymbols.setDecimalSeparator(',');
            decimalFormatSymbols.setNaN("N.A.");
            numberFormatterWithTwoDecimals = new DecimalFormat("#,##0.00", decimalFormatSymbols);
            numberFormatterWithSignAndTwoDecimals = new DecimalFormat("+#,##0.00;-#", decimalFormatSymbols);
            numberFormatterWithTwoVariableDecimals = new DecimalFormat("#,##0.##", decimalFormatSymbols);
            numberFormatterWithFiveVariableDecimals = new DecimalFormat("#,##0.#####", decimalFormatSymbols);
        } catch (Throwable exc) {
            exc.printStackTrace();
            throw exc;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        getMainActivity().setTitle(getResources().getString(R.string.app_name));
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Toolbar toolbar = (Toolbar)getMainActivity().findViewById(androidx.appcompat.R.id.action_bar);
        toolbar.setTitleTextColor(getColorFromResources(R.color.action_bar_title_text_color));
        appPreferences = PreferenceManager.getDefaultSharedPreferences(getMainActivity());
        init();
    }

    @Override
    public void onDestroyView() {
        stop();
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        activate();
        super.onResume();
    }

    @Override
    public void onPause() {
        if (!appPreferences.getBoolean("alwaysActiveWhenInBackground", false)) {
            stop();
        }
        super.onPause();
    }

    private synchronized void init() {
        String binanceApiKey = appPreferences.getString("binanceApiKey", null);
        String binanceApiSecret = appPreferences.getString("binanceApiSecret", null);
        boolean binanceWalletEnabled = appPreferences.getBoolean("binanceWalletEnabled", true);
        String cryptoComApiKey = appPreferences.getString("cryptoComApiKey", null);
        String cryptoComApiSecret = appPreferences.getString("cryptoComApiSecret", null);
        boolean cryptoComWalletEnabled = appPreferences.getBoolean("cryptoComWalletEnabled", true);
        if (isStringNotEmpty(cryptoComApiKey) && isStringNotEmpty(cryptoComApiSecret) && cryptoComWalletEnabled) {
            CryptoComWallet wallet = MainActivity.Engine.getWallet(CryptoComWallet.class);
            if (wallet.setApiKey(cryptoComApiKey) | wallet.setApiSecret(cryptoComApiSecret)) {
                MainActivity.Model.isReadyToBeShown = false;
            }
            wallet.setTimeOffset(getMainActivity().getLongValueFromAppPreferencesOrDefault("cryptoComTimeOffset", R.integer.default_crypto_com_time_offset));
            wallets.add(wallet);
        }
        if (isStringNotEmpty(binanceApiKey) && isStringNotEmpty(binanceApiSecret) && binanceWalletEnabled) {
            BinanceWallet wallet = MainActivity.Engine.getWallet(BinanceWallet.class);
            if (wallet.setApiKey(binanceApiKey) | wallet.setApiSecret(binanceApiSecret)) {
                MainActivity.Model.isReadyToBeShown = false;
            }
            long currentTimeRetrievingMode = getMainActivity().getLongValueFromAppPreferencesOrDefault("binanceCurrentTimeRetrievingMode", R.integer.default_binance_current_time_retrieving_mode);
            if (currentTimeRetrievingMode == 1) {
                wallet.enableDefaultCurrentTimeMillisRetrieverRetriever();
            } else if (currentTimeRetrievingMode == 2) {
                wallet.enableCurrentTimeMillisFromBinanceServersRetriever();
            }
            wallet.setTimeOffset(getMainActivity().getLongValueFromAppPreferencesOrDefault("binanceTimeOffset", R.integer.default_binance_time_offset));
            wallets.add(wallet);
        }
        if (wallets.isEmpty()) {
            getMainActivity().goToSettingsView();
            return;
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


    boolean isStringNotEmpty(String value){
        return value != null && !value.trim().isEmpty();
    }

    ExecutorService getExecutorService() {
        return MainActivity.Engine.getExecutorService();
    }

    boolean isUseAlwaysTheDollarCurrencyForBalancesDisabled() {
        return !appPreferences.getBoolean("useAlwaysTheDollarCurrencyForBalances", false);
    }

    int getColorFromResources(@ColorRes int id) {
        return ResourcesCompat.getColor(getResources(), id, null);
    }

    boolean canRun() {
        String binanceApiKey = appPreferences.getString("binanceApiKey", null);
        String binanceApiSecret = appPreferences.getString("binanceApiSecret", null);
        boolean binanceWalletEnabled = appPreferences.getBoolean("binanceWalletEnabled", true);
        String cryptoComApiKey = appPreferences.getString("cryptoComApiKey", null);
        String cryptoComApiSecret = appPreferences.getString("cryptoComApiSecret", null);
        boolean cryptoComWalletEnabled = appPreferences.getBoolean("cryptoComWalletEnabled", true);
        return (isStringNotEmpty(binanceApiKey) && isStringNotEmpty(binanceApiSecret) && binanceWalletEnabled) ||
                (isStringNotEmpty(cryptoComApiKey) && isStringNotEmpty(cryptoComApiSecret) && cryptoComWalletEnabled);
    }

    void updateReport(Button updateButton) {
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
                        runOnUIThread(() -> updateButton.setEnabled(true));
                    }
                }
            }
        }
    }

    Supplier<Boolean> launchCryptoReportUpdate() {
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

    String retrieveGitHubUsername() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "token " + appPreferences.getString("gitHubAuthorizationToken", null));
        UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.github.com")
                .pathSegment("user")
                .build();
        ResponseEntity<Map> response = RestTemplateSupplier.getSharedInstance().get().exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return (String)response.getBody().get("login");
    }

    Supplier<Boolean> buildUpdateCryptoReportRunningChecker(String gitHubActionToken, Collection<String> workflowIds) {
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

    synchronized void activate() {
        if (this.balanceUpdater == null) {
            balanceUpdater = new BalanceUpdater(this);
            balanceUpdater.activate();
        }
        if (this.coinViewManager == null) {
            coinViewManager = new CoinViewManager(this);
            coinViewManager.activate();
        }
    }

    synchronized void stop() {
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

    void setHighlightedValue(TextView textView, DecimalFormat numberFormatter, Double newValue) {
        setHighlightedValue(textView, numberFormatter, newValue, false, false);
    }

    void setFixedHighlightedValue(TextView textView, DecimalFormat numberFormatter, Double newValue) {
        setHighlightedValue(textView, numberFormatter, newValue, true, false);
    }

    void setHighlightedValue(TextView textView, DecimalFormat numberFormatter, Double newValue, boolean fixed, boolean inverted) {
        setHighlightedValue(textView, numberFormatter, newValue, fixed, inverted, getColorFromResources(R.color.text_default_color));
    }

    void setHighlightedValue(TextView textView, DecimalFormat numberFormatter, Double newValue, boolean fixed, boolean inverted, int defaultColor) {
        synchronized (textView) {
            String zeroAsString= null;
            String previousValueAsString = !fixed ? String.valueOf(textView.getText()) : (zeroAsString = numberFormatter.format(0D));
            String currentValueAsString = numberFormatter.format(newValue);
            String naN = numberFormatter.format(Double.NaN);
            if (currentValueAsString.equals(naN)) {
                textView.setTextColor(getColorFromResources(R.color.disabled_text_highlight_color));
                textView.setTypeface(textView.getTypeface(), Typeface.ITALIC);
            } else if (fixed && currentValueAsString.equals(zeroAsString)) {
                textView.setTextColor(defaultColor);
            } else if (!previousValueAsString.isEmpty() && !previousValueAsString.equals(currentValueAsString)) {
                try {
                    Double previousValue = !previousValueAsString.equals(naN) ?
                        numberFormatter.parse(previousValueAsString).doubleValue() :
                        newValue - 1D;
                    if ((!inverted && newValue > previousValue) || (inverted && newValue < previousValue)) {
                        textView.setTextColor(getColorFromResources(R.color.text_value_increased_highlight_color));
                    } else {
                        textView.setTextColor(getColorFromResources(R.color.text_value_decreased_highlight_color));
                    }
                } catch (ParseException e) {}
            } else if (!fixed) {
                textView.setTextColor(defaultColor);
            }
            textView.setText(currentValueAsString);
        }
    }

    void setHighlightedValue(TextView textView, String newValue) {
        setHighlightedValue(textView, newValue, getColorFromResources(R.color.text_default_color));
    }

    void setHighlightedValue(TextView textView, String newValue, int defaultColor) {
        synchronized (textView) {
            String previousValueAsString = String.valueOf(textView.getText());
            if (!previousValueAsString.isEmpty() && !previousValueAsString.equals(newValue)) {
                textView.setTextColor(getColorFromResources(R.color.simple_text_highlight_color));
                textView.setText(newValue);
            } else {
                textView.setText(newValue);
                textView.setTextColor(defaultColor);
            }
        }
    }

    MainActivity getMainActivity(){
        MainActivity mainActivity = (MainActivity)getActivity();
        if (mainActivity == null) {
            stop();
        }
        return mainActivity;
    }

    void runOnUIThread(Runnable action) {
        MainActivity mainActivity = getMainActivity();
        if (mainActivity == null) {
            return;
        }
        mainActivity.runOnUiThread(() -> {
            try {
                action.run();
            } catch (Throwable exc) {
                LoggerChain.getInstance().logError(exc.getMessage());
            }
        });
    }

}