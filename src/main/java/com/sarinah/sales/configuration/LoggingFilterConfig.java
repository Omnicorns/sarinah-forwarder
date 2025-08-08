package com.sarinah.sales.configuration;

 // ganti sesuai package projectmu

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.UUID;

@Configuration
public class LoggingFilterConfig {

    @Bean
    public FilterRegistrationBean<ApiLoggingFilter> loggingFilterRegistration() {
        FilterRegistrationBean<ApiLoggingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ApiLoggingFilter());
        reg.setName("apiLoggingFilter");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE); // log duluan, walau 401 tetap jalan
        reg.addUrlPatterns("/*");
        reg.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC));
        return reg;
    }

    static class ApiLoggingFilter extends OncePerRequestFilter {
        private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);
        private static final ObjectMapper OM = new ObjectMapper();
        private static final int MAX = 4096; // batas panjang body yang ditampilkan

        @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
        @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {

            ContentCachingRequestWrapper req  = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

            String traceId = headerOrNew(req, "X-Request-ID");
            MDC.put("traceId", traceId);

            long start = System.currentTimeMillis();
            try {
                chain.doFilter(req, res);
            } finally {
                long dur = System.currentTimeMillis() - start;
                String method = req.getMethod();
                String uri    = req.getRequestURI();
                int status    = res.getStatus();

                String reqBody = sanitize(bytesToString(req.getContentAsByteArray()));
                String resBody = sanitize(bytesToString(res.getContentAsByteArray()));

                // 3 baris log: summary, reqBody, resBody
                log.info("API {} {} \u2192 status={} ({} ms) traceId={}", method, uri, status, dur, traceId);
                if (!reqBody.isBlank()) log.info("reqBody: {}", reqBody);
                if (!resBody.isBlank()) log.info("resBody: {}", resBody);

                res.copyBodyToResponse();
                MDC.remove("traceId");
            }
        }

        private static String headerOrNew(HttpServletRequest req, String name) {
            String v = req.getHeader(name);
            return (v == null || v.isBlank()) ? UUID.randomUUID().toString() : v;
        }

        private static String bytesToString(byte[] arr) {
            return (arr == null || arr.length == 0) ? "" : new String(arr, StandardCharsets.UTF_8);
        }

        private static String sanitize(String raw) {
            if (raw == null || raw.isBlank()) return "";
            String s = raw;
            // compact JSON jika valid
            try { s = OM.writeValueAsString(OM.readTree(raw)); } catch (Exception ignored) {}
            // rapihkan whitespace
            s = s.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ").trim();
            // batasi panjang
            if (s.length() > MAX) s = s.substring(0, MAX) + "...(truncated)";
            return s;
        }
    }
}
