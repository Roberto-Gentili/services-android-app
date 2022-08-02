package org.rg.util;

import org.apache.http.impl.client.HttpClientBuilder;
import org.rg.finance.Wallet;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

public class RestTemplateSupplier {
    private static RestTemplate sharedRestTemplate;

    public final static RestTemplate getSharedRestTemplate() {
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
}
