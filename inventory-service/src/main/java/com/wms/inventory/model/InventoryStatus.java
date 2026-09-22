package com.wms.inventory.model;

/**
 * 库存状态。当前只是最简单的两态，后续如果有真实库存扣减逻辑，可以再扩展。
 */
public enum InventoryStatus {
    NORMAL,
    OUT_OF_STOCK
}
