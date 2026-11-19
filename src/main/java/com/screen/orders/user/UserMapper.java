package com.screen.orders.user;

import com.screen.orders.user.dto.UserSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

  UserSummaryResponse toSummaryResponse(User user);

  List<UserSummaryResponse> toSummaryResponses(List<User> users);
}
