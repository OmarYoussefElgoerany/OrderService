package com.screen.orders.order;

import com.screen.orders.order.dto.OrderRequest;
import com.screen.orders.order.dto.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {

  @Mapping(target = "createDate", ignore = true)
  @Mapping(target = "lastModified", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  Order toEntity(OrderRequest request);

  OrderResponse toResponse(Order order);

  List<OrderResponse> toResponses(List<Order> orders);
}
