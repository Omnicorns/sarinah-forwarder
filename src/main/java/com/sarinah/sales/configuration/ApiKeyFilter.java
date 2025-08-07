package com.sarinah.sales.configuration;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public class ApiKeyFilter extends OncePerRequestFilter {
    private static final String HEADER_NAME = "X-API-KEY";
    private final String validApiKey;

    public ApiKeyFilter(String validApiKey) {
        this.validApiKey = validApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(HEADER_NAME);
        if (validApiKey.equals(apiKey)) {
            // 1. Buat Authentication sederhana
            var auth = new PreAuthenticatedAuthenticationToken(
                    "apiKeyUser",      // principal (nama user dummy)
                    apiKey,            // credentials (boleh null)
                    List.of()          // authorities (kosong juga ok)
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            // 2. Lanjutkan filter chain
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            // Buat payload JSON sederhana
            ObjectNode body = JsonNodeFactory.instance.objectNode()
                    .put("timestamp", Instant.now().toString())
                    .put("status", HttpStatus.UNAUTHORIZED.value())
                    .put("error", "Unauthorized")
                    .put("message", "Invalid or missing X-API-KEY")
                    .put("path", request.getRequestURI());

            // Tulis JSON ke response
            response.getWriter()
                    .write(body.toString());
        }
    }

}
