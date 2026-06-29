package com.hms.scheduler;

import com.hms.entity.DoctorOrder;
import com.hms.repository.DoctorOrderRepository;
import com.hms.service.hospital.DoctorOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class NurseTaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NurseTaskScheduler.class);

    @Autowired private DoctorOrderRepository orderRepository;
    @Autowired private DoctorOrderService orderService;

    @Scheduled(cron = "0 0 6 * * *")
    public void generateDailyTasks() {
        logger.info("NurseTaskScheduler: generating daily tasks");
        // Intentionally fetches across all hospitals — scheduler runs outside request context.
        // hospitalId is preserved on each DoctorOrder and propagated to created NurseTask.
        List<DoctorOrder> recurringOrders = orderRepository
                .findByStatusAndFrequencyNot("ACTIVE", "SOS");
        for (DoctorOrder order : recurringOrders) {
            try {
                orderService.createTaskForOrder(order,
                    LocalDateTime.now().withHour(8).withMinute(0).withSecond(0).withNano(0));
            } catch (Exception e) {
                logger.warn("Failed to create task for order {}: {}", order.getId(), e.getMessage());
            }
        }
        logger.info("NurseTaskScheduler: done, processed {} orders", recurringOrders.size());
    }
}
