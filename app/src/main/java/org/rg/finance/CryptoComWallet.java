package org.rg.finance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.rg.util.Hex;
import org.rg.util.Throwables;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;


@SuppressWarnings({ "rawtypes", "unchecked", "unused"})
public class CryptoComWallet extends Wallet.Abst {

	public CryptoComWallet(
		RestTemplate restTemplate,
		Supplier<ExecutorService> executorServiceSupplier,
		String apiKey,
		String apiSecret,
		Map<String, String> aliasesForCoinNames,
		Map<String, String> coinCollaterals
	) {
		super(restTemplate, executorServiceSupplier, apiKey, apiSecret,
		Optional.ofNullable(aliasesForCoinNames).orElseGet(()-> {
			Map<String, String> valueMap = new LinkedHashMap<>();
			valueMap.put("LUNA", "LUNA2");
			return valueMap;
		}),
		Optional.ofNullable(coinCollaterals).orElseGet(()-> {
			Map<String, String> valueMap = new LinkedHashMap<>();
			valueMap.put("DEFAULT", "USDT");
			valueMap.put("LUNC", "USDC");
			valueMap.put("LUNA2", "USDC");
			valueMap.put("BUSD", "USDT");
			return valueMap;
		}));
		this.name = "Crypto.com wallet";
		this.timeOffset = -1000L;
	}

	public CryptoComWallet(
		RestTemplate restTemplate,
		Supplier<ExecutorService> executorServiceSupplier,
		String apiKey,
		String apiSecret
	) {
		this(restTemplate, executorServiceSupplier, apiKey, apiSecret, null, null);
	}

	public CryptoComWallet(
		RestTemplate restTemplate,
		String apiKey,
		String apiSecret,
		Map<String, String> aliasesForCoinNames,
		Map<String, String> coinCollaterals
	) {
		this(restTemplate, null, apiKey, apiSecret, aliasesForCoinNames, coinCollaterals);
	}

    public CryptoComWallet(
        RestTemplate restTemplate,
        String apiKey,
        String apiSecret
    ) {
        this(restTemplate, null, apiKey, apiSecret, null, null);
    }

	public CryptoComWallet(
		String apiKey,
		String apiSecret
	) {
		this(null, null, apiKey, apiSecret, null, null);
	}

	@Override
	protected Collection<String> getAvailableCoinsWithEffectiveNames() {
        Collection<Map<Object, Object>> coinBalances = ((Collection<Map<Object, Object>>)((Map<Object, Object>)getAccountSummary()
                .get("result")).get("accounts"));
        Collection<String> coinNames = new TreeSet<>();
        for (Map<Object, Object> coinBalance : coinBalances) {
        	coinNames.add((String)coinBalance.get("currency"));
        }
        return coinNames;
	}

	@Override
	protected Collection<String> getOwnedCoinsWithEffectiveNames() {
		Collection<Map<Object, Object>> coinBalances = ((Collection<Map<Object, Object>>)((Map<Object, Object>)getAccountSummary()
                .get("result")).get("accounts"));
        Collection<String> coinNames = new TreeSet<>();
        for (Map<Object, Object> coinBalance : coinBalances) {
            Number quantity = (Number)coinBalance.get("balance");
        	if (quantity.doubleValue() > 0) {
        		coinNames.add((String)coinBalance.get("currency"));
        	}
        }
        return coinNames;
	}

	@Override
	protected Double getValueForCoin(String coinName, String collateral) {
        Long currentTimeMillis = currentTimeMillis();
        UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.crypto.com")
            .pathSegment("v2").pathSegment("public").pathSegment("get-trades").queryParam("instrument_name", coinName + "_" + getCollateralForCoin(coinName))
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("currency", coinName);
        ApiRequest apiRequestJson = new ApiRequest();
        apiRequestJson.setId(currentTimeMillis);
        apiRequestJson.setApiKey(apiKey);
        apiRequestJson.setMethod(uriComponents.getPathSegments().get(1) + "/" + uriComponents.getPathSegments().get(2));
        apiRequestJson.setNonce(currentTimeMillis);
        apiRequestJson.setParams(params);
        ResponseEntity<Map> response = restTemplate.exchange(
                uriComponents.toString(), HttpMethod.GET,
                new HttpEntity<ApiRequest>(apiRequestJson, new HttpHeaders()), Map.class);
		Number value = Double.valueOf((String)((Collection<Map<Object, Object>>) ((Map<Object, Object>) response.getBody()
				.get("result")).get("data")).iterator().next().get("p"));
		return value.doubleValue();
	}

	@Override
	protected boolean checkExceptionForGetValueForCoin(Throwable exception) {
		return exception instanceof NoSuchElementException || exception instanceof ClassCastException ;
	}

	@Override
	protected Double getQuantityForEffectiveCoinName(String coinName) {
        Number value = (Number) ((Collection<Map<Object, Object>>)((Map<Object, Object>)getAccountSummary(coinName)
                .get("result")).get("accounts")).iterator().next().get("balance");
        return value.doubleValue();
	}

	private Map<Object, Object> getAccountSummary() {
		return getAccountSummary(null);
	}

	private Map<Object, Object> getAccountSummary(String coinName) {
        Long currentTimeMillis = currentTimeMillis();
        UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.crypto.com")
                .pathSegment("v2").pathSegment("private").pathSegment("get-account-summary").build();
        Map<String, Object> params = new HashMap<>();
        if (coinName != null) {
        	params.put("currency", coinName);
        }
        ApiRequest apiRequestJson = new ApiRequest();
        apiRequestJson.setId(currentTimeMillis);
        apiRequestJson.setApiKey(apiKey);
        apiRequestJson.setMethod(uriComponents.getPathSegments().get(1) + "/" + uriComponents.getPathSegments().get(2));
        apiRequestJson.setNonce(currentTimeMillis);
        apiRequestJson.setParams(params);
        try {
			Signer.sign(apiRequestJson, apiSecret);
		} catch (Throwable exc) {
			Throwables.sneakyThrow(exc);
		}
        return restTemplate.exchange(
            uriComponents.toString(), HttpMethod.POST,
            new HttpEntity<ApiRequest>(apiRequestJson, new HttpHeaders()), Map.class).getBody();
	}

	private static class Signer {
		private static final String HMAC_SHA256 = "HmacSHA256";
		private static final int MAX_LEVEL = 3;

		static boolean verify(ApiRequest apiRequestJson, String secret) {
			try {
				return genSignature(apiRequestJson, secret).equalsIgnoreCase(apiRequestJson.getSig());
			} catch (Exception e) {
				return false;
			}
		}


		private static String getParamString(final Object paramObject) {
			StringBuilder sb = new StringBuilder();
			appendParamString(sb, paramObject, 0);
			return sb.toString();
		}

		private static void appendParamString(final StringBuilder paramsStringBuilder, final Object paramObject,
				final int level) {
			if (level >= MAX_LEVEL) {
				paramsStringBuilder.append(paramObject.toString());
				return;
			}

			if (paramObject instanceof Map) {
				TreeMap<String, Object> params = new TreeMap<>((Map) paramObject);
				for (Map.Entry<String, Object> entry : params.entrySet()) {
					if (entry.getValue() instanceof Double) {
						paramsStringBuilder.append(entry.getKey())
								.append((new BigDecimal(entry.getValue().toString())).stripTrailingZeros().toPlainString());
					} else if ((entry.getValue() instanceof List) || (entry.getValue() instanceof Map)) {
						paramsStringBuilder.append(entry.getKey());
						appendParamString(paramsStringBuilder, entry.getValue(), level + 1);
					} else {
						paramsStringBuilder.append(entry.getKey()).append(entry.getValue());
					}
				}
			} else if (paramObject instanceof List) {
				List list = (List) paramObject;
				for (Object o : list) {
					appendParamString(paramsStringBuilder, o, level + 1);
				}
			} else {
				paramsStringBuilder.append(paramObject.toString());
			}
		}

		private static String genSignature(ApiRequest apiRequestJson, String secret)
				throws NoSuchAlgorithmException, InvalidKeyException {
			final byte[] byteKey = secret.getBytes(StandardCharsets.UTF_8);
			Mac mac = Mac.getInstance(HMAC_SHA256);
			SecretKeySpec keySpec = new SecretKeySpec(byteKey, HMAC_SHA256);
			mac.init(keySpec);

			String paramsString = "";

			if (apiRequestJson.getParams() != null) {
				paramsString += getParamString(apiRequestJson.getParams());
			}

			String sigPayload = apiRequestJson.getMethod() + apiRequestJson.getId() + apiRequestJson.getApiKey()
					+ paramsString + (apiRequestJson.getNonce() == null ? "" : apiRequestJson.getNonce());

			byte[] macData = mac.doFinal(sigPayload.getBytes(StandardCharsets.UTF_8));

			return Hex.encode(macData, true);
		}

		private static ApiRequest sign(ApiRequest apiRequestJson, String secret)
				throws InvalidKeyException, NoSuchAlgorithmException {
			apiRequestJson.setSig(genSignature(apiRequestJson, secret));
			return apiRequestJson;
		}
	}

	public static class ApiRequest {
		private Long id;
		private String method;
		private Map<String, Object> params;
		private String sig;

		@JsonProperty("api_key")
		private String apiKey;

		private Long nonce;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getMethod() {
			return method;
		}

		public void setMethod(String method) {
			this.method = method;
		}

		public Map<String, Object> getParams() {
			return params;
		}

		public void setParams(Map<String, Object> params) {
			this.params = params;
		}

		public String getSig() {
			return sig;
		}

		public void setSig(String sig) {
			this.sig = sig;
		}

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public Long getNonce() {
			return nonce;
		}

		public void setNonce(Long nonce) {
			this.nonce = nonce;
		}

	}

}
