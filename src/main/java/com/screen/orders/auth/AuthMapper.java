package com.screen.orders.auth;

import com.screen.orders.user.Role;
import com.screen.orders.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(imports = Role.class)
public interface AuthMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tokens", ignore = true)
  @Mapping(target = "password", source = "encodedPassword")
  @Mapping(target = "role", expression = "java(Role.USER)")
  User toNewUser(RegisterRequest request, String encodedPassword);
}
