package com.screen.orders.auth;

import com.screen.orders.auth.dto.RegisterRequest;
import com.screen.orders.user.Role;
import com.screen.orders.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, imports = Role.class)
public interface AuthMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "password", source = "encodedPassword")
  @Mapping(target = "role", expression = "java(Role.USER)")
  User toNewUser(RegisterRequest request, String encodedPassword);
}
