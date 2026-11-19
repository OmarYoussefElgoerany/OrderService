package com.screen.orders.common.security;

public interface SessionRevocationHandler {
  void revokeUserSession(Integer userId);
}
