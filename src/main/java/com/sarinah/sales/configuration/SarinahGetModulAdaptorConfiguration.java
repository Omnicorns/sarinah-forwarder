package com.sarinah.sales.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.PoolingHttpClientConnectionManagerMetricsBinder;
import lombok.RequiredArgsConstructor;


import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;

import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;


@Configuration
@RequiredArgsConstructor
public class SarinahGetModulAdaptorConfiguration {

    @Value("${http.client.max-connections}")
    private int maxTotalConnections;

    @Value("${http.client.max-per-route}")
    private int maxPerRoute;

    @Value("${http.client.connection-request-timeout}")
    private int connectionRequestTimeout;

    @Value("${http.client.connect-timeout}")
    private int connectTimeout;

    @Value("${http.client.read-timeout}")
    private int readTimeout;


    static class AntivirusRetryInterceptor implements ClientHttpRequestInterceptor {
        private final int maxExtraAttempts;   // 1 = coba ulang sekali
        private final long backoffMillis;     // jeda antar percobaan

        AntivirusRetryInterceptor(int maxExtraAttempts, long backoffMillis) {
            this.maxExtraAttempts = maxExtraAttempts;
            this.backoffMillis = backoffMillis;
        }

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

            final int total = 1 + maxExtraAttempts;

            for (int attempt = 1; attempt <= total; attempt++) {
                ClientHttpResponse resp = execution.execute(request, body);

                if (!isBlockedByAV(resp)) {
                    return resp; // OK, bukan halaman blokir → langsung return
                }

                // kalau diblok & masih ada jatah retry → tutup & ulangi
                if (attempt < total) {
                    try {
                        resp.close();
                    } catch (Exception ignored) {
                    }
                    try {
                        Thread.sleep(backoffMillis * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue; // retry
                }

                // Percobaan terakhir tetap dikembalikan apa adanya (tanpa throw)
                return resp;
            }
            // unreachable
            return execution.execute(request, body);
        }

        private boolean isBlockedByAV(ClientHttpResponse response) throws IOException {
            int code = response.getStatusCode().value();
            return code == 500;
        }

    }


    private HttpComponentsClientHttpRequestFactory getRequestFactory(MeterRegistry meterRegistry) throws Exception {
        // ✱: SSLContext “trust-all”
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true)
                .build();

        // ✱: Registry HTTP/HTTPS dengan bypass hostname
        var sfr = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https",
                        new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE)
                )
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .build();

        // ✱: Connection Manager
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(sfr);
        cm.setMaxTotal(maxTotalConnections);
        cm.setDefaultMaxPerRoute(maxPerRoute);
        cm.setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(Timeout.ofMilliseconds(readTimeout)).build());

        // ✱: Bind Micrometer metrics
        new PoolingHttpClientConnectionManagerMetricsBinder(cm, "httpClientPool")
                .bindTo(meterRegistry);

        // ✱: RequestConfig timeouts
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
                .build();

        // ✱: HttpClient (SSLContext & hostname sudah di registry)
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(rc)
                .build();

        // ✱: Wrap ke Spring factory (kalau butuh RestTemplate)
        return new HttpComponentsClientHttpRequestFactory(client);
    }

    // 1) Bean factory (jika butuh RestTemplate)


    // 3) Bean RestClient pakai insecureHttpClient
    @Bean(name = "defaultPointRestClient")
    public RestClient defaultPointRestClient(
            RestClient.Builder builder,
            MeterRegistry meterRegistry
    ) throws NoSuchAlgorithmException, KeyManagementException, Exception {

        HttpComponentsClientHttpRequestFactory factory = getRequestFactory(meterRegistry);
        return builder
                .requestFactory(factory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { /* no-op */ })
                .requestInterceptor(new AntivirusRetryInterceptor(1,300))
                .build();
    }




}
