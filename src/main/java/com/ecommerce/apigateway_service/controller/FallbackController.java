package com.ecommerce.apigateway_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @PostMapping("/login-service")
    public Mono<ResponseEntity<String>> loginFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("현재 로그인 서비스 이용이 원활하지 않습니다. 잠시 후 다시 시도해주세요."));
    }
}
