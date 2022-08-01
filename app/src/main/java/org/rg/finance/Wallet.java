package org.rg.finance;

import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
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
		private static RestTemplate sharedRestTemplate;
	    protected String apiKey;
	    protected String apiSecret;
	    protected Map<String, String> coinCollaterals;
	    protected RestTemplate restTemplate;
		protected ExecutorService executorService;

		public Abst(RestTemplate restTemplate, String apiKey, String apiSecret, Map<String, String> coinCollaterals) {
			this(restTemplate, null, apiKey, apiSecret, coinCollaterals);
		}

	    public Abst(RestTemplate restTemplate, ExecutorService executorService, String apiKey, String apiSecret, Map<String, String> coinCollaterals) {
			this.apiKey = apiKey;
			this.apiSecret = apiSecret;
			this.coinCollaterals = coinCollaterals;
			this.restTemplate = Optional.ofNullable(restTemplate).orElseGet(Wallet.Abst::getSharedRestTemplate);
			this.executorService = executorService != null ? executorService : ForkJoinPool.commonPool();
		}


		private static RestTemplate getSharedRestTemplate() {
			if (sharedRestTemplate == null) {
				synchronized(Wallet.Abst.class){
					if (sharedRestTemplate == null) {
						HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create().build());
						RestTemplate restTemplate = new RestTemplate(factory);
						restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
						restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
							@Override
							public void handleError(ClientHttpResponse httpResponse) throws IOException {
								try {
									super.handleError(httpResponse);
								} catch (HttpClientErrorException exc) {
									System.err.println("Http response error: " + exc.getStatusCode().value() + " (" + exc.getStatusText() + "). Body: " + exc.getResponseBodyAsString());
									throw exc;
								}
							}
						});
						sharedRestTemplate = restTemplate;
					}
				}
			}
			return sharedRestTemplate;
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
