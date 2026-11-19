package com.screen.orders.order;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrderMapper {

  @Mapping(target = "createDate", ignore = true)
  @Mapping(target = "lastModified", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  Order toEntity(OrderRequest request);
}
