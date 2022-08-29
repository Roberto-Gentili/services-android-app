package org.rg.services.ui.main;

import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.rg.services.R;
import org.rg.util.AsyncLooper;
import org.rg.util.LoggerChain;

class BalanceUpdater {
    private final MainFragment fragment;
    private AsyncLooper updateTask;

    BalanceUpdater(MainFragment fragment) {
        this.fragment = fragment;
    }

    synchronized void activate() {
        if (updateTask != null && updateTask.isAlive()) {
            return;
        }
        System.out.println("Wallet updater " + this + " activated");
        LinearLayout mainLayout = (LinearLayout) fragment.getView().findViewById(R.id.balancesTable);
        LinearLayout cryptoAmountBar = (LinearLayout) fragment.getView().findViewById(R.id.cryptoAmountBar);
        TextView cryptoAmount = (TextView) fragment.getView().findViewById(R.id.cryptoAmount);
        TextView cryptoAmountCurrency = (TextView) fragment.getView().findViewById(R.id.cryptoAmountCurrency);
        LinearLayout clearedCryptoAmountBar = (LinearLayout) fragment.getView().findViewById(R.id.clearedCryptoAmountBar);
        TextView clearedCryptoAmount = (TextView) fragment.getView().findViewById(R.id.clearedCryptoAmount);
        TextView clearedCryptoBalanceCurrency = (TextView) fragment.getView().findViewById(R.id.clearedCryptoAmountCurrency);
        LinearLayout balanceBar = (LinearLayout) fragment.getView().findViewById(R.id.balanceBar);
        TextView clearedBalance = (TextView) fragment.getView().findViewById(R.id.clearedBalance);
        TextView clearedBalanceCurrency = (TextView) fragment.getView().findViewById(R.id.clearedBalanceCurrency);
        LinearLayout lastUpdateBar = (LinearLayout) fragment.getView().findViewById(R.id.lastUpdateBar);
        TextView lastUpdate = (TextView) fragment.getView().findViewById(R.id.lastUpdate);
        LinearLayout reportBar = (LinearLayout) fragment.getView().findViewById(R.id.reportBar);
        TextView loadingDataAdvisor = (TextView) fragment.getView().findViewById(R.id.loadingDataAdvisor);
        TextView linkToReport = (TextView) fragment.getView().findViewById(R.id.linkToReport);
        Button updateReportButton = (Button) fragment.getView().findViewById(R.id.updateReportButton);
        ProgressBar progressBar = (ProgressBar) fragment.getView().findViewById(R.id.loadingProgressBar);
        View coinsView = ((View) fragment.getView().findViewById(R.id.coinsView));
        updateTask = new AsyncLooper(() -> {
            CoinViewManager coinViewManager = fragment.coinViewManager;
            if (coinViewManager != null) {
                if (coinViewManager.refresh()) {
                    this.fragment.runOnUIThread(() -> {
                        fragment.setHighlightedValue(cryptoAmount, fragment.numberFormatterWithTwoDecimals, fragment.coinViewManager.getAmount());
                        Double clearedAmount = fragment.coinViewManager.getClearedAmount();
                        fragment.setHighlightedValue(clearedCryptoAmount, fragment.numberFormatterWithTwoDecimals, clearedAmount);
                        Double totalInvestment = coinViewManager.getTotalInvestment();
                        if (totalInvestment != null) {
                            fragment.setFixedHighlightedValue(clearedBalance, fragment.numberFormatterWithSignAndTwoDecimals, clearedAmount - totalInvestment);
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
        }, fragment.getExecutorService()).atTheEndOfEveryIterationWaitFor(750L).whenAnExceptionIsThrown((looper, exc) -> {
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
        }
        updateTask.kill();
    }
}
