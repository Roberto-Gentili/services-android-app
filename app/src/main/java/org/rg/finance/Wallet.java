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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

import org.rg.util.LoggerChain;
import org.rg.util.RestTemplateSupplier;
import org.springframework.web.client.RestTemplate;


public interface Wallet {

	Collection<String> getAvailableCoins();

	Collection<String> getOwnedCoins();

	Double getBalance();

	Double getValueForCoin(String coinName);

	Double getQuantityForCoin(String coinName);

	Double getAmountForCoin(String coinName);

	String getCollateralForCoin(String coinName);

	String getCoinNameForAlias(String alias);

	abstract class Abst implements Wallet {
	    protected String apiKey;
	    protected String apiSecret;
		protected Map<String, String> aliasesForCoinNames;
	    protected Map<String, String> coinCollaterals;
	    protected RestTemplate restTemplate;
		protected ExecutorService executorService;
		protected Long timeOffset;

		public Abst(RestTemplate restTemplate, String apiKey, String apiSecret, Map<String, String> aliasesForCoinNames, Map<String, String> coinCollaterals) {
			this(restTemplate, null, apiKey, apiSecret, aliasesForCoinNames, coinCollaterals);
		}

	    public Abst(RestTemplate restTemplate, ExecutorService executorService, String apiKey, String apiSecret, Map<String, String> aliasesForCoinNames, Map<String, String> coinCollaterals) {
			this.apiKey = apiKey;
			this.apiSecret = apiSecret;
			this.aliasesForCoinNames = aliasesForCoinNames;
			this.coinCollaterals = coinCollaterals;
			this.restTemplate = Optional.ofNullable(restTemplate).orElseGet(RestTemplateSupplier.getSharedInstance()::get);
			this.executorService = executorService != null ? executorService : ForkJoinPool.commonPool();
			this.timeOffset = -1000L;
		}


		@Override
		public Double getValueForCoin(String coinName) {
			String collateral = getCollateralForCoin(coinName);
			if (collateral == null) {
				return Double.NaN;
			}
			String coinAlias = getCoinNameForAlias(coinName);
			try {
				return coinName.equals(collateral) ?
					1D :
					getValueForCoin(coinAlias, collateral);
			} catch (Throwable exc) {
				if (checkExceptionForGetValueForCoin(exc)) {
					String coinNameAndAlias = coinName.equals(coinAlias)? coinName : coinName + "/" + coinAlias;
					LoggerChain.getInstance().logError("No collateral for coin " + coinNameAndAlias + " on " + this.getClass().getSimpleName());
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
					this.getAmountForCoin(coinName), executorService)
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
	        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + this.timeOffset;
	    }

		public void setTimeOffset(Long timeOffset) {
			this.timeOffset = timeOffset;
		}

	}

}
