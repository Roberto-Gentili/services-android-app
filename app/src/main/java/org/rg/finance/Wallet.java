package org.rg.finance;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

import org.rg.util.LoggerChain;
import org.rg.util.RestTemplateSupplier;
import org.springframework.web.client.RestTemplate;


public interface Wallet {

	public void setExecutorServiceSupplier(Supplier<ExecutorService> executorServiceSupplier);

	public String getId();

	public String getName();

	public boolean setApiKey(String newValue);

	public boolean setApiSecret(String newValue);

	public Collection<String> getAvailableCoins();

	public Collection<String> getOwnedCoins();

	public Double getBalance();

	public Double getValueForCoin(String coinName);

	public Double getQuantityForCoin(String coinName);

	public Double getAmountForCoin(String coinName);

	public String getCollateralForCoin(String coinName);

	public String getCoinNameForAlias(String alias);

	abstract class Abst implements Wallet {
		protected String name;
	    protected String apiKey;
	    protected String apiSecret;
		protected Map<String, String> aliasesForCoinNames;
	    protected Map<String, String> coinCollaterals;
	    protected RestTemplate restTemplate;
		protected Supplier<ExecutorService> executorServiceSupplier;
		protected Long timeOffset;
		private final String id;

		public Abst(RestTemplate restTemplate, String apiKey, String apiSecret, Map<String, String> aliasesForCoinNames, Map<String, String> coinCollaterals) {
			this(restTemplate, null, apiKey, apiSecret, aliasesForCoinNames, coinCollaterals);
		}

	    public Abst(RestTemplate restTemplate, Supplier<ExecutorService> executorServiceSupplier, String apiKey, String apiSecret, Map<String, String> aliasesForCoinNames, Map<String, String> coinCollaterals) {
			this.apiKey = apiKey;
			this.apiSecret = apiSecret;
			this.aliasesForCoinNames = aliasesForCoinNames;
			this.coinCollaterals = coinCollaterals;
			this.restTemplate = Optional.ofNullable(restTemplate).orElseGet(RestTemplateSupplier.getSharedInstance()::get);
			setExecutorServiceSupplier(executorServiceSupplier);
			this.timeOffset = 0L;
			this.name = getClass().getSimpleName();
			this.id = getClass().getName() + "-" + UUID.randomUUID().toString();
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public void setExecutorServiceSupplier(Supplier<ExecutorService> executorServiceSupplier) {
			this.executorServiceSupplier = executorServiceSupplier != null ? executorServiceSupplier : () -> ForkJoinPool.commonPool();
		}

		@Override
		public boolean setApiKey(String newValue) {
			boolean changed = newValue != apiKey && apiKey == null || !newValue.equals(apiKey);
			this.apiKey = newValue;
			return changed;
		}

		@Override
		public boolean setApiSecret(String newValue) {
			boolean changed = newValue != apiSecret && apiSecret == null || !newValue.equals(apiSecret);
			this.apiSecret = newValue;
			return changed;
		}

		@Override
		public Double getValueForCoin(String coinName) {
			String collateral = getCollateralForCoin(coinName);
			if (collateral == null) {
				return Double.NaN;
			}
			String coinAlias = getCoinNameForAlias(coinName);
			try {
				Double valueForCoin = coinName.equals(collateral) ?
					1D :
					getValueForCoin(coinAlias, collateral);
				if (collateral.equals(coinCollaterals.get("DEFAULT"))) {
					return valueForCoin;
				} else {
					return valueForCoin * getValueForCoin(collateral);
				}
			} catch (Throwable exc) {
				if (checkExceptionForGetValueForCoin(exc)) {
					String coinNameAndAlias = coinName.equals(coinAlias)? coinName : coinName + "/" + coinAlias;
					LoggerChain.getInstance().logError("No collateral for coin " + coinNameAndAlias + " on " + getName());
					synchronized (coinCollaterals) {
						Map<String, String> coinCollateralsTemp = new LinkedHashMap<>();
						Map<String, String> oldCoinCollaterals = coinCollaterals;
						coinCollateralsTemp.putAll(oldCoinCollaterals);
						coinCollateralsTemp.put(coinName, null);
						coinCollaterals = coinCollateralsTemp;
						oldCoinCollaterals.clear();
					}
					return Double.NaN;
				}
				throw exc;
			}
		}

		protected abstract Double getValueForCoin(String coinName, String collateral);

		protected abstract boolean checkExceptionForGetValueForCoin(Throwable exception);

		protected abstract Double getQuantityForEffectiveCoinName(String coinName);

		protected abstract Collection<String> getAvailableCoinsWithEffectiveNames();

		protected abstract Collection<String> getOwnedCoinsWithEffectiveNames();

		@Override
		public Collection<String> getAvailableCoins() {
			List<String> coinNames = new ArrayList<>(getAvailableCoinsWithEffectiveNames());
			aliasesForCoinNames.entrySet().stream().forEach(entry -> Collections.replaceAll(coinNames, entry.getValue(), entry.getKey()));
			return new TreeSet<>(coinNames);
		}

		@Override
		public Collection<String> getOwnedCoins() {
			List<String> coinNames = new ArrayList<>(getOwnedCoinsWithEffectiveNames());
			aliasesForCoinNames.entrySet().stream().forEach(entry -> Collections.replaceAll(coinNames, entry.getValue(), entry.getKey()));
			return new TreeSet<>(coinNames);
		}

	    @Override
		public Double getAmountForCoin(String coinName)  {
			return getQuantityForCoin(coinName) * getValueForCoin(coinName);
	    }

	    @Override
		public Double getBalance() {
			Collection<CompletableFuture<Double>> tasks = new ArrayList<>();
			for (String coinName : getOwnedCoinsWithEffectiveNames()) {
				tasks.add(CompletableFuture.supplyAsync(() ->
					this.getAmountForCoin(coinName), executorServiceSupplier.get())
				);
			}
			return tasks.stream().mapToDouble(CompletableFuture::join).sum();
	    }

	    @Override
		public String getCollateralForCoin(String coinName) {
	    	return coinCollaterals.getOrDefault(coinName, coinCollaterals.get("DEFAULT"));
	    }

		@Override
		public String getCoinNameForAlias(String alias) {
			return aliasesForCoinNames.getOrDefault(alias, alias);
		}

		@Override
		public Double getQuantityForCoin(String coinName) {
			return getQuantityForEffectiveCoinName(getCoinNameForAlias(coinName));
		}

		protected String joinQueryParameters(Map<String, String> parameters) {
	        String queryString = "";
	        boolean isFirst = true;
	        for (Map.Entry<String, String> mapElement : parameters.entrySet()) {
	            if (isFirst) {
	                isFirst = false;
	                queryString += mapElement.getKey() + "=" + mapElement.getValue();
	            } else {
	                queryString += "&" + mapElement.getKey() + "=" + mapElement.getValue();
	            }
	        }
	        return queryString;
	    }

	    protected Long currentTimeMillis() {
	        return  retrieveCurrentTime() + this.timeOffset;
	    }

		protected Long retrieveCurrentTime() {
			return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		}

		public void setTimeOffset(Long timeOffset) {
			this.timeOffset = timeOffset;
		}

	}

}
