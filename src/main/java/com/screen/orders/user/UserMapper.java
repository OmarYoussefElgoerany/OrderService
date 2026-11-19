package com.screen.orders.user;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {

  UserSummaryResponse toSummaryResponse(User user);

  List<UserSummaryResponse> toSummaryResponses(List<User> users);
}
