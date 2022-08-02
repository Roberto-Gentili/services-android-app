package org.rg.services.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.apache.http.impl.client.HttpClientBuilder;
import org.rg.finance.BinanceWallet;
import org.rg.finance.CryptoComWallet;
import org.rg.finance.Wallet;
import org.rg.services.MainActivity;
import org.rg.services.R;
import org.rg.util.RestTemplateSupplier;
import org.rg.util.Throwables;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private final DateTimeFormatter dateFormatter;
    private Supplier<Double> eurValueSupplier;

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
        ((TextView)getView().findViewById(R.id.loadingDataAdvisor)).setVisibility(View.VISIBLE);
        ((ProgressBar)getView().findViewById(R.id.progressBar)).setVisibility(View.VISIBLE);
        String binanceApiKey = appPreferences.getString("binanceApiKey", null);
        String binanceApiSecret = appPreferences.getString("binanceApiSecret", null);
        String cryptoComApiKey = appPreferences.getString("cryptoComApiKey", null);
        String cryptoComApiSecret = appPreferences.getString("cryptoComApiSecret", null);
        wallets.clear();
        if (isStringNotEmpty(cryptoComApiKey) && isStringNotEmpty(cryptoComApiSecret)) {
            wallets.add(new CryptoComWallet(
                RestTemplateSupplier.getSharedInstance().get(),
                executorService,
                cryptoComApiKey,
                cryptoComApiSecret
            ));
        }
        eurValueSupplier = null;
        if (isStringNotEmpty(binanceApiKey) && isStringNotEmpty(binanceApiSecret)) {
            Wallet wallet = new BinanceWallet(
                RestTemplateSupplier.getSharedInstance().get(),
                executorService,
                binanceApiKey,
                binanceApiSecret
            );
            wallets.add(wallet);
            eurValueSupplier = () -> wallet.getValueForCoin("EUR");
        }
        numberFormatter = eurValueSupplier != null?
            new DecimalFormat("#,##0.00 €", decimalFormatSymbols):
            new DecimalFormat("#,##0.00 $", decimalFormatSymbols);
        if (!wallets.isEmpty()) {
            startBalanceUpdating();
        } else {
            ((MainActivity)getActivity()).goToSettingsView();
        }
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
                                Toast.makeText(getActivity(), "Maximum number of attempts reached", Toast.LENGTH_LONG).show();
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
                        Toast.makeText(getActivity(), "Could not update report: " + exc.getMessage(), Toast.LENGTH_LONG).show();
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
            Button updateReportButton = (Button)fragment.getView().findViewById(R.id.updateReportButton);
            ProgressBar progressBar = (ProgressBar)fragment.getView().findViewById(R.id.progressBar);
            CompletableFuture.runAsync(() -> {
                System.out.println("Wallet updater " + this + " starting");
                while (isAlive) {
                    try {
                        Collection<CompletableFuture<Double>> tasks = new ArrayList<>();
                        for (Wallet wallet : fragment.wallets) {
                            tasks.add(
                                CompletableFuture.supplyAsync(
                                    () ->
                                        wallet.getBalance(),
                                    fragment.executorService
                                )
                            );
                        }
                        Double eurValue = fragment.eurValueSupplier != null? fragment.eurValueSupplier.get() : null;
                        Double summedCoinAmountInUSDT = tasks.stream().mapToDouble(CompletableFuture::join).sum();
                        Double amount = eurValue != null? summedCoinAmountInUSDT/eurValue : summedCoinAmountInUSDT;
                        Double pureAmount = ((((((summedCoinAmountInUSDT*99.6D)/100D) - 1)*99.9D)/100D)-eurValue)/eurValue;
                        tasks.stream().mapToDouble(CompletableFuture::join).sum();
                        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Rome"));
                        this.fragment.getActivity().runOnUiThread(()-> {
                            balance.setText(fragment.numberFormatter.format(amount));
                            pureBalance.setText(fragment.numberFormatter.format(pureAmount));
                            updateTime.setText("Last update: " + now.format(fragment.dateFormatter));
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
                        });
                    } catch (Throwable exc) {
                        Toast.makeText(fragment.getActivity(), "Could not refresh balances: " + exc.getMessage(), Toast.LENGTH_LONG).show();
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

        private void stop(){
            System.out.println("Wallet updater " + this + " stop requested");
            isAlive = false;
        }
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
}