package org.rg.finance;

import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;


public interface Wallet {

	Collection<String> getAvailableCoins();

	Collection<String> getOwnedCoins();

	Double getBalance();

	Double getValueForCoin(String coinName);

	Double getQuantityForCoin(String coinName);

	Double getAmountForCoin(String coinName);

	String getCollateralForCoin(String coinName);

	abstract class Abst implements Wallet {

	    protected String apiKey;
	    protected String apiSecret;
	    protected Map<String, String> coinCollaterals;
	    protected RestTemplate restTemplate;
		protected ExecutorService executorService;

		public Abst(RestTemplate restTemplate, String apiKey, String apiSecret, Map<String, String> coinCollaterals) {
			this(restTemplate, null, apiKey, apiSecret, coinCollaterals);
		}

	    public Abst(RestTemplate restTemplate, ExecutorService executorService, String apiKey, String apiSecret, Map<String, String> coinCollaterals) {
			super();
			this.apiKey = apiKey;
			this.apiSecret = apiSecret;
			this.coinCollaterals = coinCollaterals;
			this.restTemplate = restTemplate;
			this.executorService = executorService != null ? executorService : ForkJoinPool.commonPool();
		}

	    @Override
		public Double getAmountForCoin(String coinName)  {
			return getQuantityForCoin(coinName) * getValueForCoin(coinName);
	    }

	    @Override
		public Double getBalance() {
			Collection<CompletableFuture<Double>> tasks = new ArrayList<>();
			for (String coinName : getOwnedCoins()) {
				tasks.add(CompletableFuture.supplyAsync(() -> this.getAmountForCoin(coinName), executorService));
			}
			return tasks.stream().mapToDouble(CompletableFuture::join).sum();
	    }

	    @Override
		public String getCollateralForCoin(String coinName) {
	    	return coinCollaterals.getOrDefault(coinName, coinCollaterals.get("DEFAULT"));
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
	        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1000L;
	    }

	}

}
