package com.bakaru.inventoryservice.repository;

import com.bakaru.inventoryservice.model.ProcessedOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEvent, Long> {

    boolean existsByTopicAndOrderId(String topic, Long orderId);
}
