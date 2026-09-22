package com.wms.check.client;

/**
 * 与 inventory-service 的 InventoryStatus 字段值保持一致（NORMAL / OUT_OF_STOCK）。
 * 两边各自维护一份，是因为目前没有抽公共模块 —— 后面如果类似的共享 DTO 越来越多，
 * 可以考虑抽一个 wms-common 模块，但现在只有一两个字段，暂时不值得为此增加模块复杂度。
 */
public enum InventoryStatus {
    NORMAL,
    OUT_OF_STOCK
}
