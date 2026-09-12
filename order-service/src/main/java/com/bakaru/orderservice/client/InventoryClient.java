package com.bakaru.orderservice.client;

import com.bakaru.common.dto.ReservationLine;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/api/inventory/reserve")
    void reserve(@RequestBody List<ReservationLine> items);

    @PostMapping("/api/inventory/release")
    void release(@RequestBody List<ReservationLine> items);
}
