package org.rg.services.ui.main;

import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import org.rg.services.MainActivity;
import org.rg.services.R;
import org.rg.util.AsyncLooper;
import org.rg.util.LoggerChain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class BalanceUpdater {
    private final MainFragment fragment;
    private AsyncLooper updateTask;
    private PieChartManager pieChartManager;

    BalanceUpdater(MainFragment fragment) {
        this.fragment = fragment;
        pieChartManager = new PieChartManager(fragment.getView().findViewById(R.id.balancesChart), true);
    }

    synchronized void activate() {
        if (updateTask != null && updateTask.isAlive()) {
            return;
        }
        System.out.println("Wallet updater " + this + " activated");
        LinearLayout mainLayout = fragment.getView().findViewById(R.id.balancesTable);
        LinearLayout cryptoAmountBar = fragment.getView().findViewById(R.id.cryptoAmountBar);
        TextView cryptoAmount = fragment.getView().findViewById(R.id.cryptoAmount);
        TextView cryptoAmountCurrency = fragment.getView().findViewById(R.id.cryptoAmountCurrency);
        LinearLayout clearedCryptoAmountBar = fragment.getView().findViewById(R.id.clearedCryptoAmountBar);
        TextView clearedCryptoAmount = fragment.getView().findViewById(R.id.clearedCryptoAmount);
        TextView clearedCryptoBalanceCurrency = fragment.getView().findViewById(R.id.clearedCryptoAmountCurrency);
        LinearLayout balanceBar = fragment.getView().findViewById(R.id.balanceBar);
        TextView clearedBalance = fragment.getView().findViewById(R.id.clearedBalance);
        TextView clearedBalanceCurrency = fragment.getView().findViewById(R.id.clearedBalanceCurrency);
        LinearLayout lastUpdateBar = fragment.getView().findViewById(R.id.lastUpdateBar);
        TextView lastUpdate = fragment.getView().findViewById(R.id.lastUpdate);
        LinearLayout reportBar = fragment.getView().findViewById(R.id.reportBar);
        View chartsTable = fragment.getView().findViewById(R.id.chartsTable);
        TextView loadingDataAdvisor = fragment.getView().findViewById(R.id.loadingDataAdvisor);
        TextView linkToReport = fragment.getView().findViewById(R.id.linkToReport);
        Button updateReportButton = fragment.getView().findViewById(R.id.updateReportButton);
        ProgressBar progressBar = fragment.getView().findViewById(R.id.loadingProgressBar);
        View coinsView = fragment.getView().findViewById(R.id.coinsView);
        updateTask = new AsyncLooper(() -> {
            CoinViewManager coinViewManager = fragment.coinViewManager;
            if (coinViewManager != null) {
                if (coinViewManager.refresh()) {
                    this.fragment.runOnUIThread(() -> {
                        fragment.setHighlightedValue(cryptoAmount, fragment.numberFormatterWithTwoDecimals, fragment.coinViewManager.getAmount());
                        Double clearedAmount = fragment.coinViewManager.getClearedAmount();
                        fragment.setHighlightedValue(clearedCryptoAmount, fragment.numberFormatterWithTwoDecimals, clearedAmount);
                        Double totalInvestment = coinViewManager.getTotalInvestment();
                        if (totalInvestment != null && fragment.appPreferences.getBoolean("showClearedBalance", true)) {
                            Double clearedBalanceValue = clearedAmount - totalInvestment;
                            fragment.setFixedHighlightedValue(clearedBalance, fragment.numberFormatterWithSignAndTwoDecimals, clearedBalanceValue);
                            Map<String, Integer> labelsAndColors = new LinkedHashMap();
                            List<Float> data = new ArrayList<>();
                            if (clearedBalanceValue >= 0) {
                                labelsAndColors.put("Total investment", R.color.yellow);
                                labelsAndColors.put("Gain", R.color.green);
                                data.add(totalInvestment.floatValue());
                                data.add(clearedBalanceValue.floatValue());
                            } else {
                                labelsAndColors.put("Cleared coin amount", R.color.yellow);
                                labelsAndColors.put("Loss", R.color.red);
                                data.add(clearedAmount.floatValue());
                                data.add(clearedBalanceValue.floatValue());
                            }
                            pieChartManager.setup(labelsAndColors);
                            pieChartManager.setData(data);
                        } else {
                            mainLayout.removeView(chartsTable);
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
                                mainLayout.removeView(balanceBar);
                            }
                            lastUpdateBar.setVisibility(View.VISIBLE);
                            coinsView.setVisibility(View.VISIBLE);
                            pieChartManager.visible();
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
                                mainLayout.removeView(reportBar);
                            }
                        }
                    });
                }
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

}
