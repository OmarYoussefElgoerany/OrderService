package com.screen.orders.order;

import com.screen.orders.order.dto.OrderItemRequest;
import com.screen.orders.order.dto.OrderItemResponse;
import com.screen.orders.order.dto.OrderResponse;
import com.screen.orders.order.item.OrderItem;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
interface OrderMapper {

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "userEmail", source = "user.email")
  OrderResponse toResponse(Order order);

  OrderItemResponse toItemResponse(OrderItem item);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "order", ignore = true)
  @Mapping(target = "subtotal", ignore = true)
  OrderItem toItemEntity(OrderItemRequest request);

  List<OrderItem> toItemEntities(List<OrderItemRequest> requests);

  @AfterMapping
  default void computeItemSubtotal(@MappingTarget OrderItem item) {
    item.calculateSubtotal();
  }
}
