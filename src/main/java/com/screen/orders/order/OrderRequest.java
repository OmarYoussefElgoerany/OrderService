package com.screen.orders.order;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OrderRequest {

    private Integer id;
    private String name;
}
