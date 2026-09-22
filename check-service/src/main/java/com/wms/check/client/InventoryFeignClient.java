package com.wms.check.client;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "inventory-service")
public interface InventoryFeignClient {

    @GetMapping("/inventory/{skuCode}")
    InventoryDTO getBySku(@PathVariable("skuCode") String skuCode);

}
