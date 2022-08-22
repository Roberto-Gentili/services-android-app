package org.rg.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

public class RestTemplateSupplier {
    private static RestTemplateSupplier sharedInstance;
    private RestTemplate restTemplate;
    private Consumer<HttpClientBuilder> httpClientBuilderSetter;
    private boolean requestLoggerEnabled;

    private RestTemplateSupplier(Consumer<HttpClientBuilder> httpClientBuilderSetter) {
        this();
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
                            } catch (HttpClientErrorException | HttpServerErrorException exc) {
                                LoggerChain.getInstance().logError("Http response error: " + exc.getStatusCode().value() + " (" + exc.getStatusText() + "). Body: " + exc.getResponseBodyAsString());
                                throw exc;
                            } catch (Throwable exc) {
                                LoggerChain.getInstance().logError("Exception occurred: " + exc.getMessage());
                                throw exc;
                            }
                        }
                    });
                    this.restTemplate = restTemplate;
                    activateOrDeactivateRequestLogger();
                }
            }
        }
        return restTemplate;
    }

    public synchronized RestTemplateSupplier enableRequestLogger() {
        requestLoggerEnabled = true;
        activateOrDeactivateRequestLogger();
        return this;
    }

    public synchronized RestTemplateSupplier disableRequestLogger() {
        requestLoggerEnabled = false;
        activateOrDeactivateRequestLogger();
        return this;
    }

    private synchronized void activateOrDeactivateRequestLogger() {
        if (restTemplate == null) {
            return;
        }
        List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
        if (requestLoggerEnabled) {
            if (interceptors == null) {
                interceptors = new CopyOnWriteArrayList<>();
                restTemplate.setInterceptors(interceptors);
            }
            if (!interceptors.stream().filter(interceptor -> interceptor instanceof RequestLogger).findFirst().isPresent()) {
                interceptors.add(new RequestLogger());
            }
        } else {
            if (interceptors == null) {
                return;
            }
            ClientHttpRequestInterceptor requestLogger = interceptors.stream().filter(interceptor -> interceptor instanceof RequestLogger).findFirst().orElseGet(() -> null);
            if (requestLogger != null) {
                interceptors.remove(requestLogger);
            }
        }
    }

    private static class RequestLogger implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            String logString =
                "Method: " + request.getMethod().toString() + " - " +
                "URI: " + request.getURI() +
                (!request.getHeaders().isEmpty()? " - Headers: " + request.getHeaders().toString() : "") +
                (body.length > 0? " - body: " + new String(body, StandardCharsets.UTF_8) : "");
            LoggerChain.getInstance().logInfo(logString);
            return execution.execute(request, body);
        }
    }
}
