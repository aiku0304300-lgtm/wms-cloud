package com.wms.inventory.controller;

import com.wms.inventory.model.InventoryDTO;
import com.wms.inventory.model.InventoryStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @GetMapping("/{skuCode}")
    public InventoryDTO getBySku(@PathVariable("skuCode") String skuCode) {
        // 硬编码模拟查询，不操作数据库
        return new InventoryDTO(skuCode, 100, InventoryStatus.NORMAL);
    }

}
