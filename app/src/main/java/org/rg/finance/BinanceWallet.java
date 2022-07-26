package org.rg.finance;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.rg.util.Hex;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class BinanceWallet extends Wallet.Abst {

    public BinanceWallet(
            RestTemplate restTemplate,
            ExecutorService executorService,
            String apiKey,
            String apiSecret,
            Map<String, String> coinCollaterals
    ) {
        super(restTemplate, executorService, apiKey, apiSecret, coinCollaterals);
    }

    public BinanceWallet(
        RestTemplate restTemplate,
        String apiKey,
        String apiSecret,
        Map<String, String> coinCollaterals
    ) {
        super(restTemplate, apiKey, apiSecret, coinCollaterals);
    }

    @Override
    public Collection<String> getAvailableCoins() {
        Collection<Map<String, Object>> getAccountResponseBody = (Collection<Map<String, Object>>)getAccount().get("balances");
        Iterator<Map<String, Object>> iterator = getAccountResponseBody.iterator();
        Collection<String> coinNames = new TreeSet<>();
        while (iterator.hasNext()) {
            Map<String, Object> asset = iterator.next();
            coinNames.add((String)asset.get("asset"));
        }
        Collection<Map<String, Object>> getStakingPositionResponseBody = getStakingPosition();
        iterator = getStakingPositionResponseBody.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> asset = iterator.next();
            coinNames.add((String)asset.get("asset"));
        }
        Collection<Map<String, Object>> getLendingDailyTokenPositionResponseBody = getLendingDailyTokenPosition();
        iterator = getLendingDailyTokenPositionResponseBody.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> asset = iterator.next();
            coinNames.add((String)asset.get("asset"));
        }
        return coinNames;
    }

    @Override
    public Collection<String> getOwnedCoins() {
        Collection<Map<String, Object>> getAccountResponseBody = (Collection<Map<String, Object>>)getAccount().get("balances");
        Iterator<Map<String, Object>> iterator = getAccountResponseBody.iterator();
        Collection<String> coinNames = new TreeSet<>();
        while (iterator.hasNext()) {
            Map<String, Object> asset = iterator.next();
            Double amount = Double.valueOf((String) asset.get("free"));
            amount += Double.valueOf((String) asset.get("locked"));
            if (amount > 0) {
                coinNames.add((String)asset.get("asset"));
            }
        }
        Collection<Map<String, Object>> getStakingPositionResponseBody = getStakingPosition();
        iterator = getStakingPositionResponseBody.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> asset = iterator.next();
            Double amount = Double.valueOf((String)asset.get("amount"));
            if (amount > 0) {
                coinNames.add((String)asset.get("asset"));
            }
        }
        Collection<Map<String, Object>> getLendingDailyTokenPositionResponseBody = getLendingDailyTokenPosition();
        iterator = getLendingDailyTokenPositionResponseBody.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> asset = iterator.next();
            Double amount = Double.valueOf((String)asset.get("totalAmount"));
            if (amount > 0) {
                coinNames.add((String)asset.get("asset"));
            }
        }
        return coinNames;
    }

    @Override
    public Double getValueForCoin(String coinName) {
        Long currentTimeMillis = currentTimeMillis();
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("timestamp", String.valueOf(currentTimeMillis));
        UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.binance.com")
                .pathSegment("api")
                .pathSegment("v3")
                .pathSegment("ticker")
                .pathSegment("price")
                .queryParam(
                        "symbol",
                        coinName + getCollateralForCoin(coinName)
                ).build();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-MBX-APIKEY", apiKey);
        ResponseEntity<Map> response = restTemplate.exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<String>(headers), Map.class);
        Map<String, String> body = response.getBody();
        return Double.valueOf(body.get("price"));
    }

    @Override
    public Double getQuantityForCoin(String coinName) {
        Collection<Map<String, Object>> balances = (Collection<Map<String, Object>>)getAccount().get("balances");
        Iterator<Map<String, Object>> iterator = balances.iterator();
        Double amount = null;
        while (iterator.hasNext()) {
            Map<String, Object> asset = iterator.next();
            if (coinName.equals(asset.get("asset"))) {
                if (amount == null) {
                    amount = 0D;
                }
                amount += Double.valueOf((String) asset.get("free"));
                amount += Double.valueOf((String) asset.get("locked"));
            }
        }
        iterator = getStakingPosition(coinName).iterator();
        while (iterator.hasNext()) {
            Map<String, Object> asset = iterator.next();
            if (coinName.equals(asset.get("asset"))) {
                if (amount == null) {
                    amount = 0D;
                }
                amount += Double.valueOf((String) asset.get("amount"));
            }
        }
        iterator = getLendingDailyTokenPosition(coinName).iterator();
        while (iterator.hasNext()) {
            Map<String, Object> asset = iterator.next();
            if (coinName.equals(asset.get("asset"))) {
                if (amount == null) {
                    amount = 0D;
                }
                amount += Double.valueOf((String) asset.get("totalAmount"));
            }
        }
        return amount;
    }

    @Override
    public Double getAmountForCoin(String coinName) {
        try {
            return getQuantityForCoin(coinName) * getValueForCoin(coinName);
        } catch (HttpClientErrorException exc) {
            String responseBodyAsString = exc.getResponseBodyAsString();
            if (exc.getStatusCode().series() == HttpStatus.Series.CLIENT_ERROR &&
                responseBodyAsString.toLowerCase().contains("invalid symbol")) {
                return 0D;
            }
            throw exc;
        }
    }


    @Override
    protected Long currentTimeMillis() {
        UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.binance.com")
                .pathSegment("api")
                .pathSegment("v3")
                .pathSegment("time").build();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-MBX-APIKEY", apiKey);
        ResponseEntity<Map> response = restTemplate.exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<String>(headers), Map.class);
        return (Long)response.getBody().get("serverTime");
    }

    private Map<Object, Object> getAccount() {
        Long currentTimeMillis = currentTimeMillis();
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("timestamp", String.valueOf(currentTimeMillis));
        String signature = Signer.accept(joinQueryParameters(queryParams), apiSecret);
        UriComponents uriComponents = UriComponentsBuilder.newInstance().scheme("https").host("api.binance.com")
                .pathSegment("api")
                .pathSegment("v3")
                .pathSegment("account")
                .queryParam("timestamp", String.valueOf(currentTimeMillis))
                .queryParam("signature", signature).build();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-MBX-APIKEY", apiKey);
        ResponseEntity<Map> response = restTemplate.exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<String>(headers), Map.class);
        return response.getBody();
    }

    private Collection<Map<String, Object>> getStakingPosition() {
        return getStakingPosition(null);
    }

    private Collection<Map<String, Object>> getStakingPosition(String coinName) {
        Long currentTimeMillis = currentTimeMillis();
        Map<String, String> queryParams = new HashMap<>();
        queryParams = new HashMap<>();
        queryParams.put("product", "STAKING");
        if (coinName != null) {
            queryParams.put("asset", coinName);
        }
        queryParams.put("timestamp", String.valueOf(currentTimeMillis));
        String signature = Signer.accept(joinQueryParameters(queryParams), apiSecret);
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.newInstance().scheme("https").host("api.binance.com")
                .pathSegment("sapi")
                .pathSegment("v1")
                .pathSegment("staking")
                .pathSegment("position")
                .queryParam("product", "STAKING");
        if (coinName != null) {
            uriComponentsBuilder = uriComponentsBuilder.queryParam("asset", coinName);
        }
        UriComponents uriComponents = uriComponentsBuilder
                .queryParam("timestamp", String.valueOf(currentTimeMillis))
                .queryParam("signature", signature).build();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-MBX-APIKEY", apiKey);
        return restTemplate.exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<String>(headers), Collection.class)
                .getBody();
    }

    private Collection<Map<String, Object>> getLendingDailyTokenPosition() {
        return getLendingDailyTokenPosition(null);
    }

    private Collection<Map<String, Object>> getLendingDailyTokenPosition(String coinName) {
        Long currentTimeMillis = currentTimeMillis();
        Map<String, String> queryParams = new HashMap<>();
        if (coinName != null) {
            queryParams.put("asset", coinName);
        }
        queryParams.put("timestamp", String.valueOf(currentTimeMillis));
        String signature = Signer.accept(joinQueryParameters(queryParams), apiSecret);
        UriComponentsBuilder uriComponentsBuilder =  UriComponentsBuilder.newInstance().scheme("https").host("api.binance.com")
                .pathSegment("sapi")
                .pathSegment("v1")
                .pathSegment("lending")
                .pathSegment("daily")
                .pathSegment("token")
                .pathSegment("position");
        if (coinName != null) {
            uriComponentsBuilder = uriComponentsBuilder.queryParam("asset", coinName);
        }
        UriComponents uriComponents = uriComponentsBuilder
                .queryParam("timestamp", String.valueOf(currentTimeMillis))
                .queryParam("signature", signature).build();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-MBX-APIKEY", apiKey);
        return restTemplate
                .exchange(uriComponents.toString(), HttpMethod.GET, new HttpEntity<String>(headers), Collection.class)
                .getBody();
    }

    private static class Signer {
        final static String HMAC_SHA256 = "HmacSHA256";

        private static String accept(String data, String secret) {
            byte[] hmacSha256 = null;
            try {
                SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), HMAC_SHA256);
                Mac mac = Mac.getInstance(HMAC_SHA256);
                mac.init(secretKeySpec);
                hmacSha256 = mac.doFinal(data.getBytes());
            } catch (Exception e) {
                throw new RuntimeException("Failed to calculate hmac-sha256", e);
            }
            return Hex.encode(hmacSha256, true);
        }

    }

}