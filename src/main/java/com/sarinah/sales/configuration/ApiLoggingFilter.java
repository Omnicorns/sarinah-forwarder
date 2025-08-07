package com.sarinah.sales.configuration;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
public class ApiLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedReq = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedRes = new ContentCachingResponseWrapper(response);

        // 2) Generate atau baca traceId untuk korelasi log
        String traceId = request.getHeader("X-Request-ID");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put("traceId", traceId);

        long startTime = System.currentTimeMillis();
        try {
            chain.doFilter(wrappedReq, wrappedRes);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // 3) Ambil data yang dicache
            String path     = wrappedReq.getRequestURI();
            String method   = wrappedReq.getMethod();
            String reqBody  = getPayload(wrappedReq.getContentAsByteArray());
            int status      = wrappedRes.getStatus();
            String resBody  = getPayload(wrappedRes.getContentAsByteArray());

            // 4) Log dalam satu entry
            log.info("API {} {} → status={} ({} ms) traceId={}\n  reqBody: {}\n  resBody: {}",
                    method, path, status, duration, traceId, reqBody, resBody);

            // 5) Pastikan response body dikirim ke client
            wrappedRes.copyBodyToResponse();
            MDC.remove("traceId");
        }
    }
    private String getPayload(byte[] buf) {
        if (buf == null || buf.length == 0) {
            return "";
        }
        return new String(buf, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }
}
