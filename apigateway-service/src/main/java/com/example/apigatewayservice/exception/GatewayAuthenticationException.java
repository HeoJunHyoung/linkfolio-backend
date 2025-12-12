package com.example.apigatewayservice.exception;

import lombok.Getter;

@Getter
public class GatewayAuthenticationException extends RuntimeException {

    private final ErrorCode errorCode;

    public GatewayAuthenticationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}