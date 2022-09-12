package org.rg.services.ui.main;

import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class CoinViewManager {
    private final MainFragment fragment;
    private Map<Wallet, CompletableFuture<Collection<String>>> ownedCoinsSuppliers;
    private Collection<CompletableFuture<Collection<String>>> retrievingCoinValueTasks;
    private Collection<String> coinsToBeAlwaysDisplayed;
    private AsyncLooper retrievingCoinValuesTask;
    private List<String> headerLabels;
    private boolean canBeRefreshed;

    CoinViewManager(MainFragment fragment) {
        this.fragment = fragment;
        this.coinsToBeAlwaysDisplayed = Arrays.stream(fragment.appPreferences.getString("coinsToBeAlwaysDisplayed", "BTC, ETH").toUpperCase().replace(" ", "").split(",")).filter(fragment::isStringNotEmpty).collect(Collectors.toList());
        headerLabels = new ArrayList<>();
        ownedCoinsSuppliers = new ConcurrentHashMap<>();
    }

    private void setUpHeaderLabelsForSpaces() {
        headerLabels.add(fragment.getResources().getString(R.string.coinLabelText));
        headerLabels.add(fragment.getResources().getString(R.string.unitPriceInUSDLabelText));
        headerLabels.add(fragment.getResources().getString(R.string.quantityLabelText));
        if (isCurrencyInEuro()) {
            headerLabels.add(fragment.getResources().getString(R.string.amountInEuroLabelText));
            headerLabels.add(fragment.getResources().getString(R.string.clearedAmountInEuroLabelText));
        } else {
            headerLabels.add(fragment.getResources().getString(R.string.amountInUSDLabelText));
            headerLabels.add(fragment.getResources().getString(R.string.clearedAmountInUSDLabelText));
        }
        if (getTotalInvestmentFromPreferences() != null) {
            if (fragment.appPreferences.getBoolean("showRUPEI", true)) {
                headerLabels.add(fragment.getResources().getString(R.string.rUPEIInUSDLabelText));
            }
            if (fragment.appPreferences.getBoolean("showDifferenceBetweenUPAndRUPEI", false)) {
                headerLabels.add(fragment.getResources().getString(R.string.differenceBetweenUnitPriceAndRUPEIInUSDLabelText));
            }
            if (fragment.appPreferences.getBoolean("showAUPEI", false)) {
                headerLabels.add(fragment.getResources().getString(R.string.aUPEIInUSDLabelText));
            }
            if (fragment.appPreferences.getBoolean("showDifferenceBetweenUPAndAUPEI", false)) {
                headerLabels.add(fragment.getResources().getString(R.string.differenceBetweenUnitPriceAndAUPEIInUSDLabelText));
            }
        }
        headerLabels.add(fragment.getResources().getString(R.string.lastUpdateForCoinLabelText));
    }

    private synchronized void buildHeader() {
        TableLayout coinsTable = fragment.getMainActivity().findViewById(R.id.coinTable);
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
        setValueForCoin(coinName, time, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.lastUpdateForCoinLabelText)), fragment.dateTimeFormatter);
    }

    private void setUnitPriceForCoinInDollar(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.unitPriceInUSDLabelText)), fragment.numberFormatterWithFiveVariableDecimals);
    }

    private void setQuantityForCoin(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.quantityLabelText)), fragment.numberFormatterWithFiveVariableDecimals, false, true);
    }

    private void setAmountForCoin(String coinName, Double value) {
        boolean isCurrencyInEuro = isCurrencyInEuro();
        int index = isCurrencyInEuro ?
                getIndexOfHeaderLabel(fragment.getResources().getString(R.string.amountInEuroLabelText)) :
                getIndexOfHeaderLabel(fragment.getResources().getString(R.string.amountInUSDLabelText));
        setValueForCoin(coinName, isCurrencyInEuro ? value / getEuroValue() : value, index, fragment.numberFormatterWithTwoVariableDecimals);
    }

    private void setClearedAmountForCoin(String coinName, Double value) {
        boolean isCurrencyInEuro = isCurrencyInEuro();
        int index = isCurrencyInEuro ?
                getIndexOfHeaderLabel(fragment.getResources().getString(R.string.clearedAmountInEuroLabelText)) :
                getIndexOfHeaderLabel(fragment.getResources().getString(R.string.clearedAmountInUSDLabelText));
        setValueForCoin(coinName, isCurrencyInEuro ? value / getEuroValue() : value, index, fragment.numberFormatterWithTwoVariableDecimals);
    }

    private void setRUPEIForCoin(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.rUPEIInUSDLabelText)), fragment.numberFormatterWithFiveVariableDecimals, true);
    }

    private void setDifferenceBetweenUPAndRUPEIForCoin(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.differenceBetweenUnitPriceAndRUPEIInUSDLabelText)), fragment.numberFormatterWithFiveVariableDecimals, false);
    }

    private void setAUPEIForCoin(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.aUPEIInUSDLabelText)), fragment.numberFormatterWithFiveVariableDecimals, true);
    }

    private void setDifferenceBetweenUPAndAUPEIForCoin(String coinName, Double value) {
        setValueForCoin(coinName, value, getIndexOfHeaderLabel(fragment.getResources().getString(R.string.differenceBetweenUnitPriceAndAUPEIInUSDLabelText)), fragment.numberFormatterWithFiveVariableDecimals, false);
    }

    private void setValueForCoin(String coinName, Double value, int columnIndex, DecimalFormat numberFormatter) {
        setValueForCoin(coinName, value, columnIndex, numberFormatter, false);
    }

    private void addHeaderColumn(String text) {
        fragment.runOnUIThread(() -> {
            TableLayout coinsTable = fragment.getMainActivity().findViewById(R.id.coinTable);
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
            textView.setTextColor(fragment.getColorFromResources(R.color.label_text_default_color));
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
            row = new TableRow(fragment.getMainActivity());
            TextView coinNameTextView = new TextView(mainActivity);
            coinNameTextView.setText(coinName);
            Float dimension = fragment.getResources().getDimension(R.dimen.coin_table_coin_name_column_text_size) / fragment.getResources().getDisplayMetrics().density;
            coinNameTextView.setTextSize(dimension);
            coinNameTextView.setTextColor(fragment.getColorFromResources(R.color.text_default_color));
            coinNameTextView.setGravity(Gravity.START);
            coinNameTextView.setTypeface(null, Typeface.BOLD);
            row.addView(coinNameTextView);
            for (int i = 1; i < headerLabels.size(); i++) {
                TextView valueTextView = new TextView(mainActivity);
                dimension = fragment.getResources().getDimension(R.dimen.coin_table_item_text_size) / fragment.getResources().getDisplayMetrics().density;
                valueTextView.setTextSize(dimension);
                if (headerLabels.get(i).equals(fragment.getResources().getString(R.string.lastUpdateForCoinLabelText))) {
                    valueTextView.setGravity(Gravity.CENTER_HORIZONTAL);
                } else {
                    valueTextView.setGravity(Gravity.END);
                }
                valueTextView.setTextColor(fragment.getColorFromResources(R.color.text_default_color));
                dimension = fragment.getResources().getDimension(R.dimen.coin_table_cell_padding_left_size) / fragment.getResources().getDisplayMetrics().density;
                valueTextView.setPadding(dimension.intValue(), 0, 0, 0);
                row.addView(valueTextView);
            }
            coinsTable.addView(row);
        }
        return row;
    }

    @Nullable
    private TableRow removeCoinRow(String coinName) {
        TableLayout coinsTable = fragment.getMainActivity().findViewById(R.id.coinTable);
        TableRow coinRow = getCoinRow(coinName);
        if (coinRow != null) {
            fragment.runOnUIThread(() ->
                coinsTable.removeView(coinRow)
            );
        }
        return null;
    }

    private void setValueForCoin(String coinName, LocalDateTime value, int columnIndex, DateTimeFormatter formatter) {
        fragment.runOnUIThread(() -> {
            MainActivity mainActivity = fragment.getMainActivity();
            TableLayout coinsTable = mainActivity.findViewById(R.id.coinTable);
            TableRow row = getCoinRow(mainActivity, coinsTable, coinName);
            TextView coinNameCell = ((TextView)row.getChildAt(getIndexOfHeaderLabel(fragment.getResources().getString(R.string.coinLabelText))));
            fragment.setHighlightedValue((TextView) row.getChildAt(columnIndex), formatter.format(value), coinNameCell.getCurrentTextColor());
        });
    }

    private void setValueForCoin(String coinName, Double value, int columnIndex, DecimalFormat numberFormatter, boolean inverted) {
        setValueForCoin(coinName, value, columnIndex, numberFormatter, inverted, false);
    }

    private void setValueForCoin(String coinName, Double value, int columnIndex, DecimalFormat numberFormatter, boolean inverted, boolean toDisabledRowIfNaNOrZero) {
        fragment.runOnUIThread(() -> {
            MainActivity mainActivity = fragment.getMainActivity();
            TableLayout coinsTable = mainActivity.findViewById(R.id.coinTable);
            TableRow row = getCoinRow(mainActivity, coinsTable, coinName);
            TextView coinNameCell = ((TextView)row.getChildAt(getIndexOfHeaderLabel(fragment.getResources().getString(R.string.coinLabelText))));
            if (toDisabledRowIfNaNOrZero) {
                int color = value == 0D || value.isNaN() ?
                    fragment.getColorFromResources(R.color.disabled_text_highlight_color) :
                    fragment.getColorFromResources(R.color.text_default_color);
                coinNameCell.setTextColor(color);
            }
            fragment.setHighlightedValue((TextView) row.getChildAt(columnIndex), numberFormatter, value, false, inverted, coinNameCell.getCurrentTextColor());
        });
    }

    private TableRow getCoinRow(String coinName) {
        TableLayout coinsTable = fragment.getMainActivity().findViewById(R.id.coinTable);
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
        TableLayout coinsTable = fragment.getMainActivity().findViewById(R.id.coinTable);
        int childCount = coinsTable.getChildCount();
        if (childCount > 1) {
            for (int i = 1; i < childCount; i++) {
                TableRow coinRow = (TableRow) coinsTable.getChildAt(i);
                if (coinRow != null) {
                    fragment.runOnUIThread(() ->
                        coinsTable.removeView(coinRow)
                    );
                }
            }
        }
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
            MainActivity mainActivity = fragment.getMainActivity();
            if (mainActivity != null) {
                mainActivity.storeCurrentCoinValues();
            }
        }
        retrievingCoinValuesTask.kill();
    }

    private AsyncLooper retrieveCoinValues() {
        Map<String, Map<String, Map<String, Object>>> currentCoinRawValues = MainActivity.Model.currentCoinRawValues;
        cleanUpCurrentCoinRawValues(currentCoinRawValues);
        launchOwnedCoinRetrievers(ownedCoinsSuppliers);
        AsyncLooper coinsToBeScannedRetriever = new AsyncLooper(() -> {
            launchOwnedCoinRetrievers(ownedCoinsSuppliers);
        }, fragment::getExecutorService).atTheStartOfEveryIterationWaitFor(30000L);
        MainActivity mainActivity = fragment.getMainActivity();
        return new AsyncLooper(() -> {
            Collection<CompletableFuture<Collection<String>>> retrievingCoinValueTasks = new CopyOnWriteArrayList<>();
            Map<Supplier<String>, Function<Throwable, String>> delayedTasks = new HashMap<>();
            for (Wallet wallet : fragment.wallets) {
                retrievingCoinValueTasks.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    Collection<CompletableFuture<String>> innerTasks = new ArrayList<>();
                                    Collection<String> ownedCoins = ownedCoinsSuppliers.get(wallet).join();
                                    Collection<String> coinsToBeScanned = new HashSet<>(ownedCoins);
                                    coinsToBeScanned.addAll(coinsToBeAlwaysDisplayed);
                                    //Cleaning up the coin collection
                                    for (Map.Entry<String, Map<String, Map<String, Object>>> currentCoinValues : currentCoinRawValues.entrySet()) {
                                        if (!coinsToBeScanned.contains(currentCoinValues.getKey()) && currentCoinValues.getValue().get(wallet.getName()) != null) {
                                            currentCoinValues.getValue().remove(wallet.getName());
                                        }
                                    }
                                    for (String coinName : coinsToBeScanned) {
                                        Supplier<String> task = () -> {
                                            Double unitPriceInDollar = wallet.getValueForCoin(coinName);
                                            Double quantity = wallet.getQuantityForCoin(coinName);
                                            Map<String, Map<String, Object>> allCoinValues = currentCoinRawValues.computeIfAbsent(coinName, key -> new ConcurrentHashMap<>());
                                            Map<String, Object> coinValues = allCoinValues.computeIfAbsent(wallet.getName(), key -> new ConcurrentHashMap<>());
                                            coinValues.put("unitPrice", unitPriceInDollar);
                                            Optional.ofNullable(mainActivity).map(mA -> MainActivity.Model.setLastUpdateTime()).ifPresent(lUT -> coinValues.put("lastUpdate", lUT));
                                            coinValues.put("quantity", quantity);
                                            return (String)null;
                                        };
                                        Function<Throwable, String> exceptionHandler = exc -> {
                                            String exceptionMessage = wallet.getName() + " exception occurred: " + exc.getMessage();
                                            LoggerChain.getInstance().logError(exceptionMessage);
                                            return exceptionMessage;
                                        };
                                        if (ownedCoins.contains(coinName)) {
                                            innerTasks.add(
                                                buildTask(task, exceptionHandler)
                                            );
                                        } else {
                                            delayedTasks.put(task, exceptionHandler);
                                        }
                                    }
                                    return innerTasks.stream().map(CompletableFuture::join).filter(Objects::nonNull).collect(Collectors.toList());
                                },
                                fragment.getExecutorService()
                        )
                );
            }
            this.retrievingCoinValueTasks = retrievingCoinValueTasks;
            retrievingCoinValueTasks.stream().forEach(CompletableFuture::join);
            Collection<CompletableFuture<String>> tasks = new ArrayList<>();
            for (Map.Entry<Supplier<String>, Function<Throwable, String>> task : delayedTasks.entrySet()) {
                tasks.add(buildTask(task.getKey(), task.getValue()));
            }
            tasks.stream().forEach(CompletableFuture::join);
        }, fragment::getExecutorService)
                .whenStarted(coinsToBeScannedRetriever::activate)
                .whenKilled(coinsToBeScannedRetriever::kill)
                .atTheEndOfEveryIterationWaitFor(fragment.getMainActivity().getLongValueFromAppPreferencesOrDefaultFromResources("intervalBetweenRequestGroups", R.integer.default_interval_between_request_groups_value))
                .activate();
    }

    private void cleanUpCurrentCoinRawValues(Map<String, Map<String, Map<String, Object>>> allCurrentCoinValues) {
        for (Map.Entry<String, Map<String, Map<String, Object>>> currentCoinValues : allCurrentCoinValues.entrySet()) {
            if (currentCoinValues.getValue().isEmpty()) {
                allCurrentCoinValues.remove(currentCoinValues.getKey());
            } else {
                for (Map.Entry<String, Map<String, Object>> currentCoinValuesForWallet : currentCoinValues.getValue().entrySet()) {
                    if (!fragment.wallets.stream().anyMatch(wallet -> wallet.getName().equals(currentCoinValuesForWallet.getKey()))) {
                        currentCoinValues.getValue().remove(currentCoinValuesForWallet.getKey());
                        MainActivity.Model.isReadyToBeShown = false;
                    }
                }
            }
        }
        Map<String, Map<String, Object>> euroValues = allCurrentCoinValues.get("EUR");
        if (euroValues != null) {
            boolean foundEuroValue = false;
            for (Map.Entry<String, Map<String, Object>> currentCoinValuesForWallet : euroValues.entrySet()) {
                if (!((Double)currentCoinValuesForWallet.getValue().get("unitPrice")).isNaN()) {
                    foundEuroValue = true;
                    break;
                }
            }
            if (!foundEuroValue) {
                setEuroValue(null);
                if (fragment.isUseAlwaysTheDollarCurrencyForBalancesDisabled()) {
                    MainActivity.Model.isReadyToBeShown = false;
                }
            }
        }
    }

    private CompletableFuture<String> buildTask(Supplier<String> task, Function<Throwable, String> exceptionHandler) {
        return CompletableFuture.supplyAsync(
            task,
            fragment.getExecutorService()
        ).exceptionally(exceptionHandler);
    }

    private void launchOwnedCoinRetrievers(Map<Wallet, CompletableFuture<Collection<String>>> coinSuppliers) {
        for (Wallet wallet : fragment.wallets) {
            CompletableFuture<Collection<String>> coinSupplier = coinSuppliers.get(wallet);
            if (coinSupplier == null) {
                coinSuppliers.put(wallet, launchOwnedCoinRetrievers(wallet, null));
            } else if (coinSupplier.isDone()) {
                coinSupplier = launchOwnedCoinRetrievers(wallet, coinSupplier.join());
                coinSupplier.join();
                coinSuppliers.put(wallet, coinSupplier);
            }
        }
    }

    private Collection<String> getAllOwnedCoins() {
        Collection<String> allOwnedCoins = new TreeSet<>();
        for (CompletableFuture<Collection<String>> ownedCoins : ownedCoinsSuppliers.values()) {
            allOwnedCoins.addAll(ownedCoins.join());
        }
        return allOwnedCoins;
    }

    private CompletableFuture<Collection<String>> launchOwnedCoinRetrievers(Wallet wallet, Collection<String> defaultValues) {
        return CompletableFuture.supplyAsync(() -> {
            while (true) {
                try {
                    Collection<String> coinsToBeScanned = getOwnedCoins(wallet);
                    //LoggerChain.getInstance().logInfo("Retrieved coins to be scanned for " + wallet.getName());
                    return coinsToBeScanned;
                } catch (Throwable exc) {
                    LoggerChain.getInstance().logError("Unable to retrieve owned coins: " + exc.getMessage());
                    if (defaultValues != null) {
                        return defaultValues;
                    }
                }
            }
        }, fragment.getExecutorService());
    }

    @NonNull
    private Collection<String> getOwnedCoins(Wallet wallet) {
        Collection<String> coinsForWallet = wallet.getOwnedCoins();
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
        if (!MainActivity.Model.isReadyToBeShown) {
            boolean waitForRetrievingCoinValueTasks =
                !(fragment.appPreferences.getBoolean("fastBootEnabled", true) &&
                MainActivity.Model.currentCoinRawValues.keySet().containsAll(getAllOwnedCoins()));
            if (waitForRetrievingCoinValueTasks && retrievingCoinValueTasks.stream().map(CompletableFuture::join).filter(excMsgs -> !excMsgs.isEmpty()).count() > 0) {
                setToNaNValuesIfNulls();
                return false;
            } else {
                MainActivity.Model.isReadyToBeShown = true;
            }
        }
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
        Double clearedAmount = 0D;
        Map<String, Map<String, Object>> allCoinsValues = new TreeMap<>();
        Collection<Runnable> coinTableUpdaters = new ArrayList<>();
        Double eurValue = getEuroValue();
        double currencyUnit =  (eurValue != null && !eurValue.isNaN() ? eurValue : 1D);
        for (Map.Entry<String, Map<String, Map<String, Object>>> allCoinValuesFromCurrentOrderedSnapshot : currentCoinValuesOrderedSnapshot.entrySet()) {
            Map<String, Object> values = valuesRetriever.apply(allCoinValuesFromCurrentOrderedSnapshot);
            Double coinQuantity = (Double)values.get("coinQuantity");
            Double coinAmount = (Double)values.get("coinAmount");
            Double coinClearedAmountRaw = (((((coinAmount * 99.6D) / 100D) - 1D) * 99.9D) / 100D) - currencyUnit;
            Double coinClearedAmount = (coinClearedAmountRaw / currencyUnit) >= 0 ? coinClearedAmountRaw : 0D;
            if ((!coinAmount.isNaN() && (coinAmount > 0 || coinsToBeAlwaysDisplayed.contains(allCoinValuesFromCurrentOrderedSnapshot.getKey()))) ||
                    (fragment.appPreferences.getBoolean("showNaNAmounts", true) && coinQuantity != 0D)) {
                coinTableUpdaters.add(() -> {
                    setQuantityForCoin(allCoinValuesFromCurrentOrderedSnapshot.getKey(), coinQuantity);
                    setAmountForCoin(allCoinValuesFromCurrentOrderedSnapshot.getKey(), coinAmount);
                    setClearedAmountForCoin(allCoinValuesFromCurrentOrderedSnapshot.getKey(), coinClearedAmount);
                    setUnitPriceForCoinInDollar(allCoinValuesFromCurrentOrderedSnapshot.getKey(), (Double)values.get("unitPrice"));
                    setLastUpdateForCoin(allCoinValuesFromCurrentOrderedSnapshot.getKey(), (LocalDateTime)values.get("lastUpdate"));
                });
                amount += (!coinAmount.isNaN() ? coinAmount : 0D);
                clearedAmount += (!coinClearedAmount.isNaN() ? coinClearedAmount : 0D);
                allCoinsValues.put(allCoinValuesFromCurrentOrderedSnapshot.getKey(), values);
            }
        }
        if (canBeRefreshed) {
            Collection<String> showedCoinsBeforeUpdate = getShowedCoins();
            if (!showedCoinsBeforeUpdate.isEmpty()) {
                Collection<String> allCoinNames = allCoinsValues.keySet();
                if (!allCoinNames.equals(showedCoinsBeforeUpdate)) {
                    clearCoinTable();
                }
            }
        }
        coinTableUpdaters.stream().forEach(Runnable::run);
        setAmount(amount);
        setClearedAmount(clearedAmount);
        boolean showRUPEI = fragment.appPreferences.getBoolean("showRUPEI", true);
        boolean showDifferenceBetweenUPAndRUPEI = fragment.appPreferences.getBoolean("showDifferenceBetweenUPAndRUPEI", false);
        boolean showAUPEI = fragment.appPreferences.getBoolean("showAUPEI", false);
        boolean showDifferenceBetweenUPAndAUPEI = fragment.appPreferences.getBoolean("showDifferenceBetweenUPAndAUPEI", false);
        Double totalInvestment = getTotalInvestmentFromPreferences();
        if (totalInvestment != null && (showRUPEI || showDifferenceBetweenUPAndRUPEI || showAUPEI || showDifferenceBetweenUPAndAUPEI)) {
            Double currencyValue = isCurrencyInEuro() ? euroValue : 1D;
            for (Map.Entry<String, Map<String, Object>> allCoinValues : allCoinsValues.entrySet()) {
                Map<String, Object> values = allCoinValues.getValue();
                Double coinQuantity = (Double)values.get("coinQuantity");
                Double coinAmount = (Double)values.get("coinAmount");
                Double rUPEI = coinAmount.isNaN() /*|| coinAmount == 0D*/ ? Double.NaN :
                        (((((((totalInvestment + 1D) * 100D) / 99.9D) + 1D) * 100D) / 99.6) - ((amount - coinAmount) / currencyValue)) / coinQuantity;
                Double aUPEI = coinAmount.isNaN() /*|| coinAmount == 0D*/ ? Double.NaN :
                        ((((((totalInvestment + 1D) * 100D) / 99.9D) + 1D) * 100D) / 99.6) / coinQuantity;
                if (showRUPEI) {
                    setRUPEIForCoin(allCoinValues.getKey(), rUPEI);
                }
                if (showDifferenceBetweenUPAndRUPEI) {
                    setDifferenceBetweenUPAndRUPEIForCoin(allCoinValues.getKey(), rUPEI.isNaN() ? Double.NaN :
                        (Double)values.get("unitPrice") - rUPEI
                    );
                }
                if (showAUPEI) {
                    setAUPEIForCoin(allCoinValues.getKey(), aUPEI);
                }
                if (showDifferenceBetweenUPAndAUPEI) {
                    setDifferenceBetweenUPAndAUPEIForCoin(allCoinValues.getKey(), aUPEI.isNaN() ? Double.NaN :
                        (Double)values.get("unitPrice") - aUPEI
                    );
                }

            }
        }
        setAllCoinValues(allCoinsValues);
        return canBeRefreshed = true;
    }

    private Map<String, Object> retrieveValuesWithMinMaxUnitPrice(Collection<Map<String, Object>> allCoinValues, BiPredicate<Double, Double> unitPriceTester) {
        Double coinQuantity = 0D;
        double coinAmount = 0D;
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
        for (Map.Entry<String, Map<String, Map<String, Object>>> allCoinValues : MainActivity.Model.currentCoinRawValues.entrySet()) {
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
            updateTime = updateTime == null? updateTimeForCoin : updateTimeForCoin.compareTo(updateTime) > 0 ? updateTimeForCoin : updateTime;
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

    public Double getClearedAmount() {
        Double amountInDollar = getClearedAmountInDollar();
        Double euroValue = getEuroValue();
        if (isCurrencyInEuro()) {
            return amountInDollar != null? amountInDollar / euroValue : null;
        }
        return amountInDollar;
    }

    public Double getAmountInDollar() {
        return (Double) MainActivity.Model.balancesValues.get("amount");
    }

    public Double getClearedAmountInDollar() {
        return (Double) MainActivity.Model.balancesValues.get("clearedAmount");
    }

    public Double getEuroValue() {
        return (Double) MainActivity.Model.balancesValues.get("euroValue");
    }

    public Double getPureAmountInDollar() {
        Double amount = getAmountInDollar();
        Double eurValue = getEuroValue();
        return ((((((amount * 99.6D) / 100D) - 1D) * 99.9D) / 100D) - (eurValue != null && !eurValue.isNaN() ? eurValue : 1D));
    }

    public boolean isCurrencyInEuro() {
        Double eurValue = getEuroValue();
        return fragment.isUseAlwaysTheDollarCurrencyForBalancesDisabled() && eurValue != null && !eurValue.isNaN();
    }

    public Double getTotalInvestmentFromPreferences() {
        String totalInvestmentAsString = fragment.appPreferences.getString("totalInvestment", null);
        if (fragment.isStringNotEmpty(totalInvestmentAsString)) {
            return Double.valueOf(totalInvestmentAsString);
        }
        return null;
    }

    private void setEuroValue(Double value) {
        if (value != null) {
            MainActivity.Model.balancesValues.put("euroValue", value);
        } else {
            MainActivity.Model.balancesValues.remove("euroValue");
        }
    }

    private void setAmount(Double value) {
        MainActivity.Model.balancesValues.put("amount", value);
    }

    private void setClearedAmount(Double value) {
        MainActivity.Model.balancesValues.put("clearedAmount", value);
    }

    public Map<String, Map<String, Object>> getAllCoinClearedValues() {
        return (Map<String, Map<String, Object>>)MainActivity.Model.balancesValues.get("allCoinValues");
    }

    private void setAllCoinValues(Map<String, Map<String, Object>> allCoinValues) {
        MainActivity.Model.balancesValues.put("allCoinValues", allCoinValues);
    }
}
