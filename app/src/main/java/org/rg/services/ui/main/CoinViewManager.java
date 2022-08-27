package org.rg.services.ui.main;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.rg.finance.Wallet;
import org.rg.services.MainActivity;
import org.rg.services.R;
import org.rg.util.AsyncLooper;
import org.rg.util.LoggerChain;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class CoinViewManager {
    private final MainFragment fragment;
    private Collection<CompletableFuture<Collection<String>>> retrievingCoinValueTasks;
    private Collection<String> coinsToBeAlwaysDisplayed;
    private AsyncLooper retrievingCoinValuesTask;
    private Collection<String> headerLabels;
    private boolean canBeRefreshed;

    CoinViewManager(MainFragment fragment) {
        this.fragment = fragment;
        this.coinsToBeAlwaysDisplayed = Arrays.asList(fragment.appPreferences.getString("coinsToBeAlwaysDisplayed", "BTC, ETH").toUpperCase().replace(" ", "").split(",")).stream().filter(fragment::isStringNotEmpty).collect(Collectors.toList());
        headerLabels = new ArrayList<>();
        String totalInvestmentAsString = fragment.appPreferences.getString("totalInvestment", null);
        if (totalInvestmentAsString != null && !totalInvestmentAsString.isEmpty()) {
            MainActivity.Model.currentValues.put("totalInvestment", Double.valueOf(totalInvestmentAsString));
        }
    }

    private void setUpHeaderLabelsForSpaces() {
        headerLabels.add(fragment.getResources().getString(R.string.coinLabelText));
        headerLabels.add(fragment.getResources().getString(R.string.unitPriceInUSDLabelText));
        if (getTotalInvestment() != null) {
            if (fragment.appPreferences.getBoolean("showRUPEI", true)) {
                headerLabels.add(fragment.getResources().getString(R.string.rUPEIInUSDLabelText));
            }
            if (fragment.appPreferences.getBoolean("showDifferenceBetweenUPAndRUPEI", false)) {
                headerLabels.add(fragment.getResources().getString(R.string.differenceBetweenUnitPriceAndRUPEIInUSD));
            }
        }
        headerLabels.add(fragment.getResources().getString(R.string.quantityLabelText));
        if (isCurrencyInEuro()) {
            headerLabels.add(fragment.getResources().getString(R.string.amountInEuroLabelText));
        } else {
            headerLabels.add(fragment.getResources().getString(R.string.amountInUSDLabelText));
        }
        headerLabels.add(fragment.getResources().getString(R.string.lastUpdateForCoinLabelText));
    }

    private synchronized void buildHeader() {
        TableLayout coinsTable = (TableLayout) fragment.getMainActivity().findViewById(R.id.coinTable);
        if (coinsTable.getChildAt(0) != null) {
            return;
        }
        for (String headerValue : headerLabels) {
            addHeaderColumn(headerValue);
        }
    }

    private int getIndexOfHeaderLabel(String label) {
        int index = 0;
        for (String headerValue : headerLabels) {
            if (headerValue.equals(label)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private void setLastUpdateForCoin(String coinName, LocalDateTime time) {
        setValueForCoin(coinName, time, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.lastUpdateForCoinLabelText)), MainActivity.Model.getTimeFormatter());
    }

    private void setUnitPriceForCoinInDollar(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.unitPriceInUSDLabelText)), fragment.numberFormatterWithFiveVariableDecimals);
    }

    private void setQuantityForCoin(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.quantityLabelText)), fragment.numberFormatterWithFiveVariableDecimals);
    }

    private void setAmountForCoin(String coinName, Double value) {
        String amountInEuroLabelText = fragment.getResources().getString(R.string.amountInEuroLabelText);
        int index = headerLabels.contains(amountInEuroLabelText) ?
                getIndexOfHeaderLabel(amountInEuroLabelText) : getIndexOfHeaderLabel(fragment.getResources().getString(R.string.amountInUSDLabelText));
        setValueForCoin(coinName, isCurrencyInEuro() ? value / getEuroValue() : value, index, fragment.numberFormatterWithTwoVariableDecimals);
    }

    private void setRUPEIForCoin(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.rUPEIInUSDLabelText)), fragment.numberFormatterWithFiveVariableDecimals, true);
    }

    private void setDifferenceBetweenUPAndRUPEIForCoin(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.differenceBetweenUnitPriceAndRUPEIInUSD)), fragment.numberFormatterWithFiveVariableDecimals, false);
    }

    private void setValueForCoin(String coinName, Double value, int columnIndex, DecimalFormat numberFormatter) {
        setValueForCoin(coinName, value, columnIndex, numberFormatter, false);
    }

    private void addHeaderColumn(String text) {
        fragment.runOnUIThread(() -> {
            TableLayout coinsTable = (TableLayout) fragment.getMainActivity().findViewById(R.id.coinTable);
            TableRow header = (TableRow) coinsTable.getChildAt(0);
            if (header == null) {
                header = new TableRow(fragment.getMainActivity());
                coinsTable.addView(header);
            }
            TextView textView = new TextView(fragment.getMainActivity());
            textView.setText(
                    text
            );
            Float dimension = fragment.getResources().getDimension(R.dimen.coin_table_header_text_size) / fragment.getResources().getDisplayMetrics().density;
            textView.setTextSize(dimension);
            dimension = fragment.getResources().getDimension(R.dimen.coin_table_cell_padding_left_size) / fragment.getResources().getDisplayMetrics().density;
            textView.setPadding(dimension.intValue(), 0, 0, 0);
            textView.setTextColor(fragment.getColorFromResources(R.color.yellow));
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(null, Typeface.BOLD);
            header.addView(textView);
        });
    }

    @NonNull
    private TableRow getCoinRow(MainActivity mainActivity, TableLayout coinsTable, String coinName) {
        TableRow row = getCoinRow(coinName);
        if (row == null) {
            buildHeader();
        }
        if (row == null) {
            row = new TableRow(fragment.getMainActivity());
            TextView coinNameTextView = new TextView(mainActivity);
            coinNameTextView.setText(coinName);
            Float dimension = fragment.getResources().getDimension(R.dimen.coin_table_coin_name_column_text_size) / fragment.getResources().getDisplayMetrics().density;
            coinNameTextView.setTextSize(dimension);
            coinNameTextView.setTextColor(Color.WHITE);
            coinNameTextView.setGravity(Gravity.LEFT);
            coinNameTextView.setTypeface(null, Typeface.BOLD);
            row.addView(coinNameTextView);
            coinsTable.addView(row);
        }
        return row;
    }

    private void setValueForCoin(String coinName, LocalDateTime value, int columnIndex, DateTimeFormatter formatter) {
        fragment.runOnUIThread(() -> {
            MainActivity mainActivity = fragment.getMainActivity();
            TableLayout coinsTable = (TableLayout) mainActivity.findViewById(R.id.coinTable);
            int childCount = coinsTable.getChildCount();
            TableRow row = getCoinRow(mainActivity, coinsTable, coinName);
            TextView valueTextView = (TextView) row.getChildAt(columnIndex);
            if (valueTextView == null) {
                for (int i = 1; i <= columnIndex; i++) {
                    valueTextView = (TextView) row.getChildAt(i);
                    if (valueTextView == null) {
                        valueTextView = new TextView(mainActivity);
                        Float dimension = fragment.getResources().getDimension(R.dimen.coin_table_item_text_size) / fragment.getResources().getDisplayMetrics().density;
                        valueTextView.setTextSize(dimension);
                        valueTextView.setGravity(Gravity.RIGHT);
                        valueTextView.setTextColor(Color.WHITE);
                        dimension = fragment.getResources().getDimension(R.dimen.coin_table_cell_padding_left_size) / fragment.getResources().getDisplayMetrics().density;
                        valueTextView.setPadding(dimension.intValue(), 0, 0, 0);
                        row.addView(valueTextView);
                    }
                }
            }
            fragment.setHighlightedValue(valueTextView, formatter.format(value));
        });
    }

    private void setValueForCoin(String coinName, Double value, int columnIndex, DecimalFormat numberFormatter, boolean inverted) {
        fragment.runOnUIThread(() -> {
            MainActivity mainActivity = fragment.getMainActivity();
            TableLayout coinsTable = (TableLayout) mainActivity.findViewById(R.id.coinTable);
            TableRow row = getCoinRow(mainActivity, coinsTable, coinName);
            TextView valueTextView = (TextView) row.getChildAt(columnIndex);
            if (valueTextView == null) {
                for (int i = 1; i <= columnIndex; i++) {
                    valueTextView = (TextView) row.getChildAt(i);
                    if (valueTextView == null) {
                        valueTextView = new TextView(mainActivity);
                        Float dimension = fragment.getResources().getDimension(R.dimen.coin_table_item_text_size) / fragment.getResources().getDisplayMetrics().density;
                        valueTextView.setTextSize(dimension);
                        valueTextView.setGravity(Gravity.RIGHT);
                        valueTextView.setTextColor(Color.WHITE);
                        dimension = fragment.getResources().getDimension(R.dimen.coin_table_cell_padding_left_size) / fragment.getResources().getDisplayMetrics().density;
                        valueTextView.setPadding(dimension.intValue(), 0, 0, 0);
                        row.addView(valueTextView);
                    }
                }
            }
            fragment.setHighlightedValue(valueTextView, numberFormatter, value, false, inverted);
        });
    }

    private TableRow getCoinRow(String coinName) {
        TableLayout coinsTable = (TableLayout) fragment.getMainActivity().findViewById(R.id.coinTable);
        int childCount = coinsTable.getChildCount();
        if (childCount > 1) {
            String coinLabelText = fragment.getResources().getString(R.string.coinLabelText);
            for (int i = 1; i < childCount; i++) {
                TableRow coinRow = (TableRow) coinsTable.getChildAt(i);
                TextView coinNameTextView = (TextView) coinRow.getChildAt(getIndexOfHeaderLabel(coinLabelText));
                if (coinNameTextView.getText().equals(coinName)) {
                    return coinRow;
                }
            }
        }
        return null;
    }

    private void clearCoinTable() {
        TableLayout coinsTable = (TableLayout) fragment.getMainActivity().findViewById(R.id.coinTable);
        int childCount = coinsTable.getChildCount();
        if (childCount > 1) {
            String coinLabelText = fragment.getResources().getString(R.string.coinLabelText);
            for (int i = 1; i < childCount; i++) {
                TableRow coinRow = (TableRow) coinsTable.getChildAt(i);
                if (coinRow != null) {
                    fragment.runOnUIThread(() -> {
                        coinsTable.removeView(coinRow);
                    });
                }
            }
        }
    }

    private TableRow removeCoinRow(String coinName) {
        TableLayout coinsTable = (TableLayout) fragment.getMainActivity().findViewById(R.id.coinTable);
        TableRow coinRow = getCoinRow(coinName);
        if (coinRow != null) {
            fragment.runOnUIThread(() -> {
                coinsTable.removeView(coinRow);
            });
        }
        return null;
    }

    public synchronized void activate() {
        if (retrievingCoinValuesTask == null) {
            System.out.println("Coin view manager " + this + " activated");
            retrievingCoinValuesTask = retrieveCoinValues();
        }
    }


    void stop() {
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
        Map<String, Map<String, Map<String, Object>>> currentCoinValues = MainActivity.Model.currentCoinValues;
        Map<Wallet, CompletableFuture<Collection<String>>> coinToBeScannedSuppliers = new ConcurrentHashMap<>();
        launchCoinToBeScannedSuppliers(coinToBeScannedSuppliers);
        AsyncLooper coinsToBeScannedRetriever = new AsyncLooper(() -> {
            launchCoinToBeScannedSuppliers(coinToBeScannedSuppliers);
        }, fragment.getExecutorService()).atTheStartOfEveryIterationWaitFor(30000L);
        MainActivity mainActivity = fragment.getMainActivity();
        return new AsyncLooper(() -> {
            Collection<String> scannedCoins = ConcurrentHashMap.newKeySet();
            Collection<CompletableFuture<Collection<String>>> retrievingCoinValueTasks = new CopyOnWriteArrayList<>();
            for (Wallet wallet : fragment.wallets) {
                retrievingCoinValueTasks.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    Collection<CompletableFuture<String>> innerTasks = new ArrayList<>();
                                    Collection<String> coinsToBeScanned = coinToBeScannedSuppliers.get(wallet).join();
                                    scannedCoins.addAll(coinsToBeScanned);
                                    for (String coinName : coinsToBeScanned) {
                                        innerTasks.add(
                                                CompletableFuture.supplyAsync(
                                                        () -> {
                                                            Double unitPriceInDollar = wallet.getValueForCoin(coinName);
                                                            Double quantity = wallet.getQuantityForCoin(coinName);
                                                            Map<String, Map<String, Object>> allCoinValues = currentCoinValues.computeIfAbsent(coinName, key -> new ConcurrentHashMap<>());
                                                            Map<String, Object> coinValues = allCoinValues.computeIfAbsent(wallet.getId(), key -> new ConcurrentHashMap<>());
                                                            coinValues.put("unitPrice", unitPriceInDollar);
                                                            Optional.ofNullable(mainActivity).map(mA -> MainActivity.Model.setLastUpdateTime()).ifPresent(lUT -> coinValues.put("lastUpdate", lUT));
                                                            coinValues.put("quantity", quantity);
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
            currentCoinValues.keySet().stream().filter(coinName -> !scannedCoins.contains(coinName)).forEach(currentCoinValues::remove);
        }, fragment.getExecutorService())
                .whenStarted(coinsToBeScannedRetriever::activate)
                .whenKilled(coinsToBeScannedRetriever::kill)
                .atTheEndOfEveryIterationWaitFor(fragment.getMainActivity().getLongValueFromAppPreferencesOrDefault("intervalBetweenRequestGroups", R.integer.default_interval_between_request_groups_value))
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

    private Collection<String> getShowedCoins() {
        TableLayout coinsTable = (TableLayout) fragment.getMainActivity().findViewById(R.id.coinTable);
        Collection<String> showedCoins = new HashSet<>();
        String coinLabelText = fragment.getResources().getString(R.string.coinLabelText);
        for (int i = 1; i < coinsTable.getChildCount(); i++) {
            TableRow row = (TableRow) coinsTable.getChildAt(i);
            showedCoins.add(String.valueOf(((TextView) row.getChildAt(getIndexOfHeaderLabel(coinLabelText))).getText()));
        }
        return showedCoins;
    }

    public boolean refresh() {
        if (retrievingCoinValueTasks == null) {
            return false;
        }
        MainActivity mainActivity = fragment.getMainActivity();
        /*if (!MainActivity.Model.isReadyToBeShown) {
            if (retrievingCoinValueTasks.stream().map(CompletableFuture::join).filter(excMsgs -> !excMsgs.isEmpty()).count() > 0) {
                setToNaNValuesIfNulls();
                return false;
            }
        }*/
        Map<String, Map<String, Map<String, Object>>> currentCoinValuesOrderedSnapshot = getCurrentCoinValuesOrderedSnapshot();
        if (currentCoinValuesOrderedSnapshot.isEmpty()) {
            return false;
        }
        Integer unitPriceRetrievingMode = Integer.valueOf(fragment.appPreferences.getString("unitPriceRetrievingMode", "3"));
        Supplier<Double> euroValueSupplier = null;
        Function<Map.Entry<String, Map<String, Map<String, Object>>>, Map<String, Object>> valuesRetriever = null;
        if (unitPriceRetrievingMode == 1 || unitPriceRetrievingMode == 2) {
            BiPredicate<Double, Double> unitPriceTester = unitPriceRetrievingMode == 1 ?
                    (valueOne, valueTwo) -> valueOne < valueTwo :
                    (valueOne, valueTwo) -> valueOne > valueTwo;
            euroValueSupplier = () -> (Double)retrieveValuesWithMinMaxUnitPrice(currentCoinValuesOrderedSnapshot.get("EUR").values(), unitPriceTester).get("unitPrice");
            valuesRetriever = allCoinValues -> retrieveValuesWithMinMaxUnitPrice(allCoinValues.getValue().values(), unitPriceTester);
        } else if (unitPriceRetrievingMode == 3) {
            euroValueSupplier = () -> (Double)retrieveValuesWithAvgUnitPrice(currentCoinValuesOrderedSnapshot.get("EUR").values()).get("unitPrice");
            valuesRetriever = allCoinValues -> retrieveValuesWithAvgUnitPrice(allCoinValues.getValue().values());
        }
        Double euroValue = null;
        if (fragment.isUseAlwaysTheDollarCurrencyForBalancesDisabled() && currentCoinValuesOrderedSnapshot.get("EUR") != null) {
            setEuroValue(euroValue = euroValueSupplier.get());
        } else if (!fragment.isUseAlwaysTheDollarCurrencyForBalancesDisabled()) {
            setEuroValue(euroValue);
        }
        if (headerLabels.isEmpty()) {
            setUpHeaderLabelsForSpaces();
        }
        Double amount = 0D;
        Map<String, Map<String, Object>> allCoinsValues = new TreeMap<>();
        Collection<Runnable> coinTableUpdaters = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Object>>> allCoinValuesFromCurrentOrderedSnapshot : currentCoinValuesOrderedSnapshot.entrySet()) {
            Map<String, Object> values = valuesRetriever.apply(allCoinValuesFromCurrentOrderedSnapshot);
            Double coinQuantity = (Double)values.get("coinQuantity");
            Double coinAmount = (Double)values.get("coinAmount");
            if ((!coinAmount.isNaN() && (coinAmount > 0 || coinsToBeAlwaysDisplayed.contains(allCoinValuesFromCurrentOrderedSnapshot.getKey()))) ||
                    (fragment.appPreferences.getBoolean("showNaNAmounts", true) && coinQuantity != 0D)) {
                coinTableUpdaters.add(() -> {
                    setQuantityForCoin(allCoinValuesFromCurrentOrderedSnapshot.getKey(), coinQuantity);
                    setAmountForCoin(allCoinValuesFromCurrentOrderedSnapshot.getKey(), coinAmount);
                    setUnitPriceForCoinInDollar(allCoinValuesFromCurrentOrderedSnapshot.getKey(), (Double)values.get("unitPrice"));
                    setLastUpdateForCoin(allCoinValuesFromCurrentOrderedSnapshot.getKey(), (LocalDateTime)values.get("lastUpdate"));
                });
                amount += (!coinAmount.isNaN() ? coinAmount : 0D);
                allCoinsValues.put(allCoinValuesFromCurrentOrderedSnapshot.getKey(), values);
            } else if (coinAmount.isNaN()) {
                removeCoinRow(allCoinValuesFromCurrentOrderedSnapshot.getKey());
            }
        }
        if (canBeRefreshed) {
            Collection<String> showedCoinsBeforeUpdate = getShowedCoins();
            if (!showedCoinsBeforeUpdate.isEmpty()) {
                Collection<String> allCoinNames = allCoinsValues.keySet();
                if (allCoinNames.stream().filter(coinName -> !showedCoinsBeforeUpdate.contains(coinName)).count() > 0) {
                    clearCoinTable();
                } else {
                    Iterator<String> showedCoinsBeforeUpdateItr = showedCoinsBeforeUpdate.iterator();
                    while (showedCoinsBeforeUpdateItr.hasNext()) {
                        String coinName = showedCoinsBeforeUpdateItr.next();
                        if (!allCoinNames.contains(coinName)) {
                            removeCoinRow(coinName);
                            showedCoinsBeforeUpdateItr.remove();
                        }
                    }
                }
            }
        }
        coinTableUpdaters.stream().forEach(Runnable::run);
        setAmount(amount);
        boolean showRUPEI = fragment.appPreferences.getBoolean("showRUPEI", true);
        boolean showDifferenceBetweenUPAndRUPEI = fragment.appPreferences.getBoolean("showDifferenceBetweenUPAndRUPEI", true);
        Double totalInvestment = getTotalInvestment();
        if (totalInvestment != null && (showRUPEI || showDifferenceBetweenUPAndRUPEI)) {
            Double currencyValue = isCurrencyInEuro() ? euroValue : 1D;
            for (Map.Entry<String, Map<String, Object>> allCoinValues : allCoinsValues.entrySet()) {
                Map<String, Object> values = allCoinValues.getValue();
                Double coinQuantity = (Double)values.get("coinQuantity");
                Double coinAmount = (Double)values.get("coinAmount");
                Double RUPEI = coinAmount.isNaN() || coinAmount == 0D ? Double.NaN :
                        (((((((totalInvestment + 1D) * 100D) / 99.9D) + 1D) * 100D) / 99.6) - ((amount - coinAmount) / currencyValue)) / coinQuantity;
                if (showRUPEI) {
                    setRUPEIForCoin(allCoinValues.getKey(), RUPEI);
                }
                if (showDifferenceBetweenUPAndRUPEI) {
                    setDifferenceBetweenUPAndRUPEIForCoin(allCoinValues.getKey(), RUPEI.isNaN() ? Double.NaN :
                        (Double)values.get("unitPrice") - RUPEI
                    );
                }

            }
        }
        MainActivity.Model.isReadyToBeShown = true;
        return canBeRefreshed = true;
    }

    private Map<String, Object> retrieveValuesWithMinMaxUnitPrice(Collection<Map<String, Object>> allCoinValues, BiPredicate<Double, Double> unitPriceTester) {
        Double coinQuantity = 0D;
        Double coinAmount = 0D;
        Double unitPrice = null;
        LocalDateTime updateTime = null;
        for (Map<String, Object> coinValues : allCoinValues) {
            Double coinQuantityForCoinInWallet = (Double)coinValues.get("quantity");
            Double unitPriceForCoinInWallet = (Double)coinValues.get("unitPrice");
            if (unitPrice == null || unitPrice.isNaN() || unitPriceTester.test(unitPriceForCoinInWallet, unitPrice)) {
                unitPrice = unitPriceForCoinInWallet;
                updateTime = (LocalDateTime)coinValues.get("lastUpdate");
            }
            coinQuantity = sum(coinQuantity, coinQuantityForCoinInWallet);
        }
        coinAmount += coinQuantity * unitPrice;
        if (coinAmount != 0D && coinQuantity != 0D) {
            unitPrice = coinAmount / coinQuantity;
        } else if (unitPrice == 0D) {
            coinAmount = unitPrice = Double.NaN;
        }
        Map<String, Object> values = new HashMap<>();
        values.put("coinQuantity", coinQuantity);
        values.put("coinAmount", coinAmount);
        values.put("unitPrice", unitPrice);
        values.put("lastUpdate", updateTime);
        return values;
    }

    private Map<String, Map<String, Map<String, Object>>> getCurrentCoinValuesOrderedSnapshot() {
        Map<String, Map<String, Map<String, Object>>> currentCoinValuesSnapshot = new TreeMap<>();
        for (Map.Entry<String, Map<String, Map<String, Object>>> allCoinValues : MainActivity.Model.currentCoinValues.entrySet()) {
            Map<String, Map<String, Object>> coinValuesForWallets = new HashMap<>();
            currentCoinValuesSnapshot.put(allCoinValues.getKey(), coinValuesForWallets);
            for (Map.Entry<String, Map<String, Object>> coinValuesForWallet : allCoinValues.getValue().entrySet()) {
                coinValuesForWallets.put(coinValuesForWallet.getKey(), new HashMap<>(coinValuesForWallet.getValue()));
            }
        }
        return currentCoinValuesSnapshot;
    }

    private Map<String, Object> retrieveValuesWithAvgUnitPrice(Collection<Map<String, Object>> allCoinValues) {
        Double coinQuantity = 0D;
        Double coinAmount = 0D;
        Double unitPrice = 0D;
        LocalDateTime updateTime = null;
        for (Map<String, Object> coinValues : allCoinValues) {
            Double coinQuantityForCoinInWallet = (Double)coinValues.get("quantity");
            Double unitPriceForCoinInWallet = (Double)coinValues.get("unitPrice");
            LocalDateTime updateTimeForCoin = (LocalDateTime)coinValues.get("lastUpdate");
            unitPrice = sum(unitPrice, unitPriceForCoinInWallet);
            coinQuantity = sum(coinQuantity, coinQuantityForCoinInWallet);
            coinAmount = sum(coinAmount, coinQuantityForCoinInWallet * unitPriceForCoinInWallet);
            updateTime = updateTime == null? updateTimeForCoin : updateTimeForCoin.compareTo(updateTime) == 1 ? updateTimeForCoin : updateTime;
        }
        if (coinAmount != 0D && coinQuantity != 0D) {
            unitPrice = coinAmount / coinQuantity;
        } else if (unitPrice == 0D) {
            coinAmount = unitPrice = Double.NaN;
        } else {
            unitPrice /= allCoinValues.stream().filter(map -> (Double)map.get("unitPrice") > 0D).count();
        }
        Map<String, Object> values = new HashMap<>();
        values.put("coinQuantity", coinQuantity);
        values.put("coinAmount", coinAmount);
        values.put("unitPrice", unitPrice);
        values.put("lastUpdate", updateTime);
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
        return a.isNaN() ?
                b :
                b.isNaN() ?
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
        return (Double) MainActivity.Model.currentValues.get("amount");
    }

    public Double getAmountInEuro() {
        Double euroValue = getEuroValue();
        if (euroValue != null && !euroValue.isNaN()) {
            return getAmountInDollar() / euroValue;
        }
        return null;
    }

    public Double getEuroValue() {
        return (Double) MainActivity.Model.currentValues.get("euroValue");
    }

    public Double getPureAmountInDollar() {
        Double amount = getAmountInDollar();
        Double eurValue = getEuroValue();
        return ((((((amount * 99.6D) / 100D) - 1D) * 99.9D) / 100D) - (eurValue != null && !eurValue.isNaN() ? eurValue : 1D));
    }

    public Double getClearedAmount() {
        Double amount = getAmountInDollar();
        Double eurValue = getEuroValue();
        Double currencyUnit = (eurValue != null && !eurValue.isNaN() ? eurValue : 1D);
        Double clearedAmount = ((((((amount * 99.6D) / 100D) - 1D) * 99.9D) / 100D) - currencyUnit) / currencyUnit;
        return clearedAmount >= 0D ? clearedAmount : 0D;
    }

    public boolean isCurrencyInEuro() {
        Double eurValue = getEuroValue();
        return fragment.isUseAlwaysTheDollarCurrencyForBalancesDisabled() && eurValue != null && !eurValue.isNaN();
    }

    public Double getTotalInvestment() {
        return (Double) MainActivity.Model.currentValues.get("totalInvestment");
    }

    private void setEuroValue(Double value) {
        if (value != null) {
            MainActivity.Model.currentValues.put("euroValue", value);
        } else {
            MainActivity.Model.currentValues.remove("euroValue");
        }
    }

    private void setAmount(Double value) {
        MainActivity.Model.currentValues.put("amount", value);
    }

}