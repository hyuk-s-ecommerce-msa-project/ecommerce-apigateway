package com.ecommerce.apigateway_service.filter;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class AuthorizationHeaderFilter extends AbstractGatewayFilterFactory<AuthorizationHeaderFilter.Config> {
    private final Environment env;

    public AuthorizationHeaderFilter(Environment env) {
        super(Config.class);
        this.env = env;
    }

    public static class Config {}

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String secret = env.getProperty("token.secret");

            if (secret == null) {
                log.error("Can't load token.secret from Config Server");

                return onError(exchange, "Ineternal Server Error : Config Missing", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            ServerHttpRequest request = exchange.getRequest();

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "No Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String authorizationHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
            String jwt = authorizationHeader.replace("Bearer ", "");

            String userId = getSubjectIfValid(jwt);
            log.info("검증 결과 userId: [{}]", userId);

            if (userId == null) {
                log.error("필터에서 401 반환: userId가 null임");
                return onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
            }
            log.info("검증 성공, 다음 필터로 진행");
            ServerHttpRequest newRequest = request.mutate().header("userId", userId).build();

            return chain.filter(exchange.mutate().request(newRequest).build());
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        log.error(err);

        byte[] bytes = "The requested token is invalid".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);

        return response.writeWith(Flux.just(buffer));
    }

    private String getSubjectIfValid(String jwt) {
        try {
            String secret = env.getProperty("token.secret");

            byte[] secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
            SecretKey signingKey = Keys.hmacShaKeyFor(secretKeyBytes);

            JwtParser parser = Jwts.parser().verifyWith(signingKey).build();
            return parser.parseSignedClaims(jwt).getPayload().getSubject();

        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("비교 결과: 서명 불일치 (토큰 생성 키와 다름)");
            return null;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.error("비교 결과: 토큰 만료");
            return null;
        } catch (Exception e) {
            log.error("기타 오류: {}", e.getMessage());
            return null;
        }
    }
}
