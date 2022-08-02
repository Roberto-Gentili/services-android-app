package org.rg.util;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

public class RestTemplateSupplier {
    private static RestTemplateSupplier sharedInstance;
    private RestTemplate restTemplate;
    private Consumer<HttpClientBuilder> httpClientBuilderSetter;

    private RestTemplateSupplier(Consumer<HttpClientBuilder> httpClientBuilderSetter) {
        this.httpClientBuilderSetter = httpClientBuilderSetter;
    }

    private RestTemplateSupplier() {}

    public RestTemplateSupplier create() {
        return new RestTemplateSupplier();
    }

    public RestTemplateSupplier create(Consumer<HttpClientBuilder> httpClientBuilderSetter) {
        return new RestTemplateSupplier(httpClientBuilderSetter);
    }

    public final static RestTemplateSupplier setupSharedInstance(Consumer<HttpClientBuilder> httpClientBuilderSetter) {
        if (sharedInstance == null) {
            synchronized (RestTemplateSupplier.class) {
                if (sharedInstance == null) {
                    sharedInstance = new RestTemplateSupplier(httpClientBuilderSetter);
                } else if (sharedInstance.httpClientBuilderSetter != httpClientBuilderSetter) {
                    throw new IllegalStateException("Could not initialize httpClientBuilderSetter twice");
                }
            }
        } else if (sharedInstance.httpClientBuilderSetter != httpClientBuilderSetter) {
            throw new IllegalStateException("Could not initialize httpClientBuilderSetter twice");
        }
        return sharedInstance;
    }

    public final static RestTemplateSupplier getSharedInstance() {
        if (sharedInstance == null) {
            synchronized (RestTemplateSupplier.class) {
                if (sharedInstance == null) {
                    sharedInstance = new RestTemplateSupplier();
                }
            }
        }
        return sharedInstance;
    }

    public RestTemplate get() {
        if (restTemplate == null) {
            synchronized(this) {
                if (restTemplate == null) {
                    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
                    if (httpClientBuilderSetter != null) {
                        httpClientBuilderSetter.accept(httpClientBuilder);
                    }
                    RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build()));
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
                    this.restTemplate = restTemplate;
                }
            }
        }
        return restTemplate;
    }

}
