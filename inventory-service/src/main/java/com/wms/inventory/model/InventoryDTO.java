package com.wms.inventory.model;

/**
 * inventory-service 对外暴露的库存查询结果。
 * 用 record 是因为它只是一份不可变的数据快照，没有行为，不需要写一个完整的类。
 */
public record InventoryDTO(String skuCode, int quantity, InventoryStatus status) {
}
