package com.sarinah.sales.configuration;

 // ganti sesuai paketmu

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.UUID;

@Configuration
public class LoggingFilterConfig {

    @Bean
    public FilterRegistrationBean<ApiLoggingFilter> loggingFilterRegistration() {
        FilterRegistrationBean<ApiLoggingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ApiLoggingFilter());                       // pasang filternya
        reg.setName("apiLoggingFilter");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);                    // PENTING: paling awal
        reg.addUrlPatterns("/*");                                    // semua path
        reg.setDispatcherTypes(EnumSet.of(
                DispatcherType.REQUEST,
                DispatcherType.ERROR,
                DispatcherType.ASYNC
        ));
        return reg;
    }

    // Jangan bikin file terpisah dengan nama sama. Biarkan jadi inner class di sini.
    static class ApiLoggingFilter extends OncePerRequestFilter {
        private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);

        @Override protected boolean shouldNotFilterErrorDispatch() { return false; }



        @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {

            ContentCachingRequestWrapper req  = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

            String traceId = request.getHeader("X-Request-ID");
            if (traceId == null || traceId.isBlank()) traceId = UUID.randomUUID().toString();

            long start = System.currentTimeMillis();
            try {
                log.debug("ApiLoggingFilter HIT {}", req.getRequestURI());
                chain.doFilter(req, res); // meski ApiKeyFilter balikin 401, finally tetap jalan
            } finally {
                long dur = System.currentTimeMillis() - start;
                String reqBody = new String(req.getContentAsByteArray(), StandardCharsets.UTF_8);
                String resBody = new String(res.getContentAsByteArray(), StandardCharsets.UTF_8);

                log.info("API {} {} -> status={} ({} ms) traceId={}\n  reqBody: {}\n  resBody: {}",
                        req.getMethod(), req.getRequestURI(), res.getStatus(), dur, traceId, reqBody, resBody);

                res.copyBodyToResponse(); // WAJIB: kirim balik body ke client
            }
        }
    }
}

