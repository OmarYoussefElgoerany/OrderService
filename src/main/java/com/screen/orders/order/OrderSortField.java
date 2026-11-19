package com.screen.orders.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderSortField {
  CREATE_DATE("createDate"),
  LAST_MODIFIED("lastModified"),
  TOTAL_AMOUNT("totalAmount"),
  STATUS("status"),
  ORDER_NUMBER("orderNumber"),
  ID("id");

  private final String field;
}
