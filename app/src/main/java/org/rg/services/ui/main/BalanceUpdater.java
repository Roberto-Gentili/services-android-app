package org.rg.services.ui.main;

import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import org.rg.services.MainActivity;
import org.rg.services.R;
import org.rg.util.AsyncLooper;
import org.rg.util.LoggerChain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class BalanceUpdater {
    private final MainFragment fragment;
    private AsyncLooper updateTask;
    private PieChartManager balancesChartManager;
    private PieChartManager coinsChartManager;

    BalanceUpdater(MainFragment fragment) {
        this.fragment = fragment;
    }

    synchronized void activate() {
        if (updateTask != null && updateTask.isAlive()) {
            return;
        }
        System.out.println("Wallet updater " + this + " activated");
        ViewGroup mainContainer = fragment.getView().findViewById(R.id.mainContainer);
        ViewGroup balancesTable = fragment.getView().findViewById(R.id.balancesTable);
        ViewGroup cryptoAmountBar = fragment.getView().findViewById(R.id.cryptoAmountBar);
        TextView cryptoAmount = fragment.getView().findViewById(R.id.cryptoAmount);
        TextView cryptoAmountCurrency = fragment.getView().findViewById(R.id.cryptoAmountCurrency);
        ViewGroup clearedCryptoAmountBar = fragment.getView().findViewById(R.id.clearedCryptoAmountBar);
        TextView clearedCryptoAmount = fragment.getView().findViewById(R.id.clearedCryptoAmount);
        TextView clearedCryptoBalanceCurrency = fragment.getView().findViewById(R.id.clearedCryptoAmountCurrency);
        ViewGroup balanceBar = fragment.getView().findViewById(R.id.balanceBar);
        TextView clearedBalance = fragment.getView().findViewById(R.id.clearedBalance);
        TextView clearedBalanceCurrency = fragment.getView().findViewById(R.id.clearedBalanceCurrency);
        ViewGroup lastUpdateBar = fragment.getView().findViewById(R.id.lastUpdateBar);
        TextView lastUpdate = fragment.getView().findViewById(R.id.lastUpdate);
        ViewGroup reportBar = fragment.getView().findViewById(R.id.reportBar);
        HorizontalScrollView chartsView = fragment.getView().findViewById(R.id.chartsView);
        ViewGroup chartsTable = fragment.getView().findViewById(R.id.chartsTable);
        View balancesChart = fragment.getView().findViewById(R.id.balancesChart);
        View coinsChart = fragment.getView().findViewById(R.id.coinsChart);
        TextView loadingDataAdvisor = fragment.getView().findViewById(R.id.loadingDataAdvisor);
        TextView linkToReport = fragment.getView().findViewById(R.id.linkToReport);
        Button updateReportButton = fragment.getView().findViewById(R.id.updateReportButton);
        ProgressBar progressBar = fragment.getView().findViewById(R.id.loadingProgressBar);
        View coinsView = fragment.getView().findViewById(R.id.coinsView);
        if (fragment.appPreferences.getBoolean("showClearedBalance", true)) {
            balancesChartManager = new PieChartManager(fragment.getView().findViewById(R.id.balancesChart), true);
            if (fragment.coinViewManager.getTotalInvestment() != null && fragment.coinViewManager.getClearedAmount() != null) {
                setBalancesChartData(fragment.coinViewManager.getTotalInvestment(), fragment.coinViewManager.getClearedAmount(), fragment.coinViewManager.getAllCoinClearedValues());
            }
        } else {
            chartsTable.removeView(balancesChart);
        }
        coinsChartManager = new PieChartManager(fragment.getView().findViewById(R.id.coinsChart), true);
        updateTask = new AsyncLooper(() -> {
            CoinViewManager coinViewManager = fragment.coinViewManager;
            if (coinViewManager == null) {
                return;
            }
            if (coinViewManager.refresh()) {
                this.fragment.runOnUIThread(() -> {
                    fragment.setHighlightedValue(cryptoAmount, fragment.numberFormatterWithTwoDecimals, fragment.coinViewManager.getAmount());
                    Double clearedAmount = fragment.coinViewManager.getClearedAmount();
                    fragment.setHighlightedValue(clearedCryptoAmount, fragment.numberFormatterWithTwoDecimals, clearedAmount);
                    Double totalInvestment = coinViewManager.getTotalInvestment();
                    if (totalInvestment != null && fragment.appPreferences.getBoolean("showClearedBalance", true)) {
                        fragment.setFixedHighlightedValue(clearedBalance, fragment.numberFormatterWithSignAndTwoDecimals, clearedAmount - totalInvestment);
                        setBalancesChartData(totalInvestment, clearedAmount, fragment.coinViewManager.getAllCoinClearedValues());
                    }
                    Map<String, Map<String, Object>> allCoinClearedValues = coinViewManager.getAllCoinClearedValues();
                    boolean allCoinClearedValuesIsNotEmpty = !allCoinClearedValues.isEmpty();
                    if (allCoinClearedValuesIsNotEmpty) {
                        setCoinsChartData(allCoinClearedValues, clearedAmount);
                        if (coinsChart.getParent() == null) {
                            chartsTable.addView(coinsChart);
                        }
                    } else if (chartsTable.findViewById(coinsChart.getId()) != null) {
                        chartsTable.removeView(coinsChart);
                    }
                    if (chartsTable.getChildCount() == 0) {
                        mainContainer.removeView(chartsView);
                    } else if (mainContainer.findViewById(chartsView.getId()) == null) {
                        mainContainer.removeView(coinsView);
                        mainContainer.addView(chartsView);
                        mainContainer.addView(coinsView);
                    }
                    fragment.setHighlightedValue(lastUpdate, fragment.getLastUpdateTimeAsString());
                    if (loadingDataAdvisor.getVisibility() != View.INVISIBLE) {
                        if (coinViewManager.isCurrencyInEuro()) {
                            cryptoAmountCurrency.setText("€");
                            clearedCryptoBalanceCurrency.setText("€");
                            clearedBalanceCurrency.setText("€");
                        } else {
                            cryptoAmountCurrency.setText("$");
                            clearedCryptoBalanceCurrency.setText("$");
                            clearedBalanceCurrency.setText("$");
                        }
                        loadingDataAdvisor.setVisibility(View.INVISIBLE);
                        progressBar.setVisibility(View.INVISIBLE);
                        cryptoAmountBar.setVisibility(View.VISIBLE);
                        clearedCryptoAmountBar.setVisibility(View.VISIBLE);
                        if (coinViewManager.getTotalInvestment() != null && fragment.appPreferences.getBoolean("showClearedBalance", true)) {
                            balanceBar.setVisibility(View.VISIBLE);
                        } else {
                            balancesTable.removeView(balanceBar);
                        }
                        lastUpdateBar.setVisibility(View.VISIBLE);
                        coinsView.setVisibility(View.VISIBLE);
                        if (balancesChartManager != null) {
                            balancesChartManager.visible();
                        }
                        if (fragment.gitHubUsernameSupplier.join() != null) {
                            linkToReport.setMovementMethod(LinkMovementMethod.getInstance());
                            String reportUrl = fragment.getResources().getString(R.string.reportUrl).replace(
                                    "${username}",
                                    fragment.gitHubUsernameSupplier.join()
                            );
                            linkToReport.setText(Html.fromHtml(String.valueOf(linkToReport.getText()).replace("&reportUrl;", reportUrl), Html.FROM_HTML_MODE_LEGACY));
                            updateReportButton.setOnClickListener(view -> fragment.updateReport((Button)view));
                            reportBar.setVisibility(View.VISIBLE);
                        } else {
                            balancesTable.removeView(reportBar);
                        }
                    }
                    if (allCoinClearedValuesIsNotEmpty) {
                        coinsChartManager.visible();
                    }
                });
            }
        }, fragment::getExecutorService).atTheEndOfEveryIterationWaitFor(750L).whenAnExceptionIsThrown((looper, exc) -> {
            boolean isActivityNotNull = fragment.getMainActivity() != null;
            if (isActivityNotNull) {
                exc.printStackTrace();
                LoggerChain.getInstance().logError("Exception occurred: " + exc.getMessage());
            }
            return isActivityNotNull;
        }).activate();
    }

    void stop() {
        AsyncLooper updateTask;
        synchronized (this) {
            updateTask = this.updateTask;
            if (updateTask == null) {
                return;
            }
            System.out.println("Wallet updater " + this + " stop requested");
            this.updateTask = null;
            MainActivity mainActivity = fragment.getMainActivity();
            if (mainActivity != null) {
                mainActivity.storeBalancesValues();
            }
        }
        updateTask.kill();
    }

    private void setBalancesChartData(Double totalInvestment, Double clearedAmount, Map<String, Map<String, Object>> allCoinClearedValues) {
        Double clearedBalanceValue = clearedAmount - totalInvestment;
        Map<String, Integer> labelsAndColors;
        List<Float> data;
        if (clearedBalanceValue >= 0 || allCoinClearedValues == null || allCoinClearedValues.isEmpty()) {
            labelsAndColors = new LinkedHashMap();
            data = new ArrayList<>();
            if (clearedBalanceValue >= 0) {
                labelsAndColors.put("Total inv.", ResourcesCompat.getColor(fragment.getResources(), R.color.yellow, null));
                labelsAndColors.put("Gain", ResourcesCompat.getColor(fragment.getResources(), R.color.green, null));
                data.add(totalInvestment.floatValue());
                data.add(clearedBalanceValue.floatValue());
            } else {
                labelsAndColors.put("Cl. coin am.", null);
                labelsAndColors.put("Loss", ResourcesCompat.getColor(fragment.getResources(), R.color.red, null));
                data.add(clearedAmount.floatValue());
                data.add(clearedBalanceValue.floatValue());
            }
        } else {
            Pair<Map<String, Integer>, List<Float>> valuesForPieChart = getValuesForPieChart(balancesChartManager, allCoinClearedValues, clearedAmount / 100);
            labelsAndColors = valuesForPieChart.first;
            data = valuesForPieChart.second;
            labelsAndColors.put("Loss", ResourcesCompat.getColor(fragment.getResources(), R.color.red, null));
            data.add(clearedBalanceValue.floatValue());
        }
        balancesChartManager.setup(labelsAndColors);
        balancesChartManager.setData(data);
    }

    private void setCoinsChartData(Map<String, Map<String, Object>> allCoinClearedValues, Double clearedAmount) {
        Pair<Map<String, Integer>, List<Float>> valuesForPieChart = getValuesForPieChart(coinsChartManager, allCoinClearedValues, clearedAmount / 100);
        coinsChartManager.setup(valuesForPieChart.first);
        coinsChartManager.setData(valuesForPieChart.second);
    }

    private Pair<Map<String, Integer>, List<Float>> getValuesForPieChart(PieChartManager chartManager, Map<String, Map<String, Object>> allCoinClearedValues, Double minValue) {
        Map<String, Integer> labelsAndColors = new LinkedHashMap();
        List<Float> data = new ArrayList<>();
        Pair<Map<String, Integer>, List<Float>> valuesForPieChart = new Pair<>(labelsAndColors, data);
        AtomicReference<Double> groupedValues = new AtomicReference<>(0D);
        allCoinClearedValues.entrySet().stream().filter(entry -> !((Double)entry.getValue().get("coinAmount")).isNaN())
        .sorted(Comparator.comparingDouble(entry ->
            (Double)((Map.Entry<String, Map<String, Object>>)entry).getValue().get("coinAmount")
        ))
        .forEach(entry -> {
            Double coinAmount = (Double)entry.getValue().get("coinAmount");
            if (coinAmount > minValue) {
                labelsAndColors.put(entry.getKey(), chartManager.getOrGenerateColorFor(entry.getKey()));
                data.add(coinAmount.floatValue());
            } else {
                groupedValues.updateAndGet(v -> v + coinAmount);
            }
        });
        if (groupedValues.get() > 0) {
            labelsAndColors.put("Others", chartManager.getOrGenerateColorFor("Others"));
            data.add(groupedValues.get().floatValue());
        }
        return valuesForPieChart;
    }
}
