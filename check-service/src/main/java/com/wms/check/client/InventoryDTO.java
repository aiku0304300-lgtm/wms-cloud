package com.wms.check.client;

/**
 * check-service 这一侧用来接收 Feign 调用 inventory-service 返回的 JSON。
 * 字段名和结构要跟 inventory-service 的 InventoryDTO 保持一致，
 * Feign/Jackson 是按字段名匹配反序列化的，不要求是同一个 Java 类。
 */
public record InventoryDTO(String skuCode, int quantity, InventoryStatus status) {
}
