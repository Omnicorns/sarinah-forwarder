package com.sarinah.sales.configuration;


import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE); // paling awal
        reg.addUrlPatterns("/*");                 // semua path
        reg.setDispatcherTypes(EnumSet.of(
                DispatcherType.REQUEST,
                DispatcherType.ERROR,
                DispatcherType.ASYNC
        ));
        return reg;
    }

    static class ApiLoggingFilter extends OncePerRequestFilter {
        private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);
        private static final ObjectMapper om = new ObjectMapper();

        @Override
        protected boolean shouldNotFilterErrorDispatch() { return false; }

        @Override
        protected boolean shouldNotFilterAsyncDispatch() { return false; }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {

            ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

            String traceId = request.getHeader("X-Request-ID");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }

            long start = System.currentTimeMillis();
            try {
                chain.doFilter(req, res);
            } finally {
                long dur = System.currentTimeMillis() - start;
                String reqBody = sanitize(new String(req.getContentAsByteArray(), StandardCharsets.UTF_8));
                String resBody = sanitize(new String(res.getContentAsByteArray(), StandardCharsets.UTF_8));

                log.info("API {} {} -> status={} ({} ms) traceId={} reqBody={} resBody={}",
                        req.getMethod(), req.getRequestURI(), res.getStatus(), dur, traceId, reqBody, resBody);

                res.copyBodyToResponse(); // kirim kembali ke client
            }
        }

        private static String sanitize(String raw) {
            if (raw == null || raw.isBlank()) return "";
            String s = raw;
            // coba compact JSON jika valid
            try {
                s = om.writeValueAsString(om.readTree(raw));
            } catch (Exception ignore) {
                // bukan JSON valid, skip
            }
            // hilangkan newline/tab
            s = s.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ").trim();
            // batasi panjang
            int MAX = 4096;
            if (s.length() > MAX) s = s.substring(0, MAX) + "...(truncated)";
            return s;
        }
    }
}
