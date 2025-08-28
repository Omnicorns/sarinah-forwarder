package com.sarinah.sales.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.PoolingHttpClientConnectionManagerMetricsBinder;
import lombok.RequiredArgsConstructor;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.*;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;

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

    /**
     * Interceptor untuk retry bila halaman diblokir AV/proxy.
     * Tidak akan melempar exception ke caller; pada percobaan terakhir,
     * akan mengembalikan response (asli atau sintetis 502) supaya caller bisa handle sendiri.
     */
    static class AntivirusRetryInterceptor implements ClientHttpRequestInterceptor {
        private final int maxExtraAttempts;   // 1 = total 2x percobaan
        private final long backoffMillis;     // jeda antar percobaan
        private static final Set<Integer> AV_STATUS = Set.of(499, 403, 451, 502); // 499 khas proxy/AV

        AntivirusRetryInterceptor(int maxExtraAttempts, long backoffMillis) {
            this.maxExtraAttempts = maxExtraAttempts;
            this.backoffMillis = backoffMillis;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            final int total = 1 + maxExtraAttempts;
            IOException lastIo = null;

            for (int attempt = 1; attempt <= total; attempt++) {
                ClientHttpResponse resp = null;
                try {
                    resp = execution.execute(request, body);

                    // BufferingClientHttpRequestFactory membuat body bisa dibaca berkali-beri
                    if (!isBlockedByAV(resp)) {
                        return resp; // OK
                    }

                    // Halaman diblokir AV → retry jika masih ada jatah
                    resp.close();
                } catch (IOException ioe) {
                    lastIo = ioe; // simpan error untuk dilaporkan di percobaan terakhir
                }

                if (attempt < total) {
                    try { Thread.sleep(backoffMillis * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }

                // Percobaan terakhir: jangan throw. Kembalikan response sintetis 502 jika pure I/O error,
                // atau response terakhir (jika ada) agar caller bisa melihat body halaman blokirnya.
                if (lastIo != null) {
                    String msg = "Retry gagal: " + lastIo.getMessage();
                    return new ByteArrayClientHttpResponse(HttpStatus.BAD_GATEWAY, new HttpHeaders(),
                            msg.getBytes(StandardCharsets.UTF_8));
                }
                if (resp != null) {
                    return resp;
                }
                // fallback kalau resp null dan tidak ada exception (jarang terjadi)
                return new ByteArrayClientHttpResponse(HttpStatus.BAD_GATEWAY, new HttpHeaders(),
                        "Retry gagal tanpa response".getBytes(StandardCharsets.UTF_8));
            }

            // unreachable
            return execution.execute(request, body);
        }

        private boolean isBlockedByAV(ClientHttpResponse resp) throws IOException {
            int code = resp.getStatusCode().value();
            if (AV_STATUS.contains(code)) return true;

            MediaType ct = resp.getHeaders().getContentType();
            if (ct != null && MediaType.TEXT_HTML.isCompatibleWith(ct)) {
                String s = StreamUtils.copyToString(resp.getBody(), StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
                // Ciri khas halaman blokir Kaspersky/antivirus gateway
                return s.contains("kaspersky") || s.contains("detected and blocked") || s.contains("antivirus");
            }
            return false;
        }

        /** Response wrapper sederhana berbasis byte-array. */
        static final class ByteArrayClientHttpResponse implements ClientHttpResponse {
            private final HttpStatusCode status;
            private final HttpHeaders headers;
            private final byte[] body;

            ByteArrayClientHttpResponse(HttpStatusCode status, HttpHeaders headers, byte[] body) {
                this.status = status;
                this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
                this.body = (body != null) ? body : new byte[0];
            }

            @Override public HttpStatusCode getStatusCode() { return status; }
            @Override public int getRawStatusCode() { return status.value(); }
            @Override public String getStatusText() { return status.toString(); }
            @Override public HttpHeaders getHeaders() { return headers; }
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public void close() { }
        }
    }

    private HttpComponentsClientHttpRequestFactory getRequestFactory(MeterRegistry meterRegistry) throws Exception {
        // SSLContext “trust-all” (umum saat AV melakukan MITM cert)
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true)
                .build();

        var sfr = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(sfr);
        cm.setMaxTotal(maxTotalConnections);
        cm.setDefaultMaxPerRoute(maxPerRoute);
        cm.setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(Timeout.ofMilliseconds(readTimeout)).build());

        // Micrometer metrics
        new PoolingHttpClientConnectionManagerMetricsBinder(cm, "httpClientPool").bindTo(meterRegistry);

        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
                .build();

        // Retry di level Apache HttpClient (tambahan selain interceptor)
        var strategy = new DefaultHttpRequestRetryStrategy(1, TimeValue.ofMilliseconds(300)) {
            @Override
            public boolean retryRequest(HttpResponse response, int execCount, org.apache.hc.core5.http.protocol.HttpContext context) {
                int code = response.getCode();
                // Retry untuk status yang sering muncul saat AV/proxy memblokir
                if (execCount <= 2 && (code == 499 || code == 403 || code == 451 || code == 502)) {
                    return true;
                }
                return super.retryRequest(response, execCount, context);
            }
        };

        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(rc)
                .setRetryStrategy(strategy)
                .build();

        return new HttpComponentsClientHttpRequestFactory(client);
    }

    @Bean(name = "defaultPointRestClient")
    public RestClient defaultPointRestClient(RestClient.Builder builder,
                                             MeterRegistry meterRegistry)
            throws NoSuchAlgorithmException, KeyManagementException, Exception {

        // Penting: Buffering supaya interceptor bisa mengintip body tanpa menghabiskan stream
        ClientHttpRequestFactory base = getRequestFactory(meterRegistry);
        ClientHttpRequestFactory buffering = new BufferingClientHttpRequestFactory(base);

        return builder
                .requestFactory(buffering)
                // Matikan exception untuk semua status error (biar nggak ngethrow)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* no-op */ })
                // Retry khusus halaman/response AV
                .requestInterceptor(new AntivirusRetryInterceptor(1, 300))
                .build();
    }
}
