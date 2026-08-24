package com.nexpay.payment.exception;

public class GatewayException extends RuntimeException {

    private final String gateway;
    private final int    statusCode;

    public GatewayException(String gateway, int statusCode, String message) {
        super("gateway error [" + gateway + "] status: " + statusCode + " message: " + message);
        this.gateway    = gateway;
        this.statusCode = statusCode;
    }

    public GatewayException(String gateway, String message) {
        super("gateway error [" + gateway + "]: " + message);
        this.gateway    = gateway;
        this.statusCode = 500;
    }

    public String getGateway()    { return gateway;    }
    public int    getStatusCode() { return statusCode; }
}