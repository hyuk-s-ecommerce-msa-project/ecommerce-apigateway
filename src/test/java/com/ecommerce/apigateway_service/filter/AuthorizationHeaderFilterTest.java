package com.ecommerce.apigateway_service.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationHeaderFilterTest {
    @Mock
    private Environment env;

    private AuthorizationHeaderFilter authorizationHeaderFilter;

    @Mock
    private GatewayFilterChain chain;

    private final String testSecret = "test-secret-key-more-than-512-bits-long-for-hmac-sha-algorithm-1234567890";

    @BeforeEach
    void setUp() {
        authorizationHeaderFilter = new AuthorizationHeaderFilter(env);
    }

    @Test
    @DisplayName("실패 : Config Server에서 secret을 읽어오지 못할 때 500 반환")
    void filterFailConfigMissing() {
        given(env.getProperty("token.secret")).willReturn(null);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build()
        );

        GatewayFilter gatewayFilter = authorizationHeaderFilter.apply(new AuthorizationHeaderFilter.Config());

        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("실패 : Authorization 헤더가 아예 없을 때 401 반환")
    void filterFailNoHeader() {
        given(env.getProperty("token.secret")).willReturn(testSecret);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build()
        );

        GatewayFilter gatewayFilter = authorizationHeaderFilter.apply(new AuthorizationHeaderFilter.Config());

        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("실패 : 유효하지 않은(변조된) 토큰일 때 401 반환")
    void filterFailInvalidToken() {
        given(env.getProperty("token.secret")).willReturn(testSecret);

        String invalidJwt = "Bearer wrong.token.value";

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header(HttpHeaders.AUTHORIZATION, invalidJwt)
                        .build()
        );

        GatewayFilter gatewayFilter = authorizationHeaderFilter.apply(new AuthorizationHeaderFilter.Config());

        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("성공 : 유효한 토큰일 때 userId 헤더를 추가하고 다음 필터로 진행")
    void filterSuccess() {
        given(env.getProperty("token.secret")).willReturn(testSecret);
        given(chain.filter(any())).willReturn(Mono.empty());

        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        String validJwt = "Bearer " + Jwts.builder()
                .subject("USER-1234")
                .expiration(new Date(System.currentTimeMillis() + 10000))
                .signWith(key)
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header(HttpHeaders.AUTHORIZATION, validJwt)
                        .build()
        );

        GatewayFilter gatewayFilter = authorizationHeaderFilter.apply(new AuthorizationHeaderFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange mutatedExchange = captor.getValue();
        assertEquals("USER-1234", mutatedExchange.getRequest().getHeaders().getFirst("userId"));
    }
}