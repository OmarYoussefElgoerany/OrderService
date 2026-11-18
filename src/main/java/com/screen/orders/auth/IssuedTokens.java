package com.screen.orders.auth;

/** Internal token pair. Only the access token is serialized in the API response. */
public record IssuedTokens(String accessToken, String refreshToken) {
}
