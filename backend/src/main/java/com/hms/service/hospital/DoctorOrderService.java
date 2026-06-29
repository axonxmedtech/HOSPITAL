package com.hms.service.hospital;

import com.hms.entity.DoctorOrder;
import com.hms.entity.NurseTask;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.DoctorOrderRepository;
import com.hms.repository.NurseTaskRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DoctorOrderService {

    @Autowired private DoctorOrderRepository orderRepository;
    @Autowired private NurseTaskRepository taskRepository;
    @Autowired private SecurityContextHelper securityHelper;

    @Transactional
    public DoctorOrder createOrder(Long admissionId, Map<String, Object> data) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        DoctorOrder order = new DoctorOrder();
        order.setIpdAdmissionId(admissionId);
        order.setHospitalId(hospitalId);
        order.setOrderType((String) data.get("orderType"));
        order.setDescription((String) data.get("description"));
        order.setFrequency((String) data.get("frequency"));
        order.setNotes((String) data.get("notes"));
        order.setStatus("ACTIVE");
        order.setCreatedByName(securityHelper.getCurrentUserEmail());
        order.setStartDate(LocalDate.now());
        if (data.get("endDate") != null) {
            order.setEndDate(LocalDate.parse(data.get("endDate").toString()));
        }
        DoctorOrder saved = orderRepository.save(order);

        // Create initial task for non-SOS orders
        if (!"SOS".equalsIgnoreCase(saved.getFrequency())) {
            createTaskForOrder(saved, LocalDateTime.now());
        }
        return saved;
    }

    public List<DoctorOrder> getOrders(Long admissionId) {
        return orderRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(admissionId);
    }

    @Transactional
    public DoctorOrder updateOrder(String publicId, Map<String, Object> data) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        DoctorOrder order = findOrder(publicId, hospitalId);
        if (data.containsKey("description") && data.get("description") != null)
            order.setDescription((String) data.get("description"));
        if (data.containsKey("notes"))
            order.setNotes((String) data.get("notes"));
        if (data.containsKey("endDate") && data.get("endDate") != null)
            order.setEndDate(LocalDate.parse(data.get("endDate").toString()));
        return orderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        DoctorOrder order = findOrder(publicId, hospitalId);
        order.setStatus("CANCELLED");
        orderRepository.save(order);
    }

    public void createTaskForOrder(DoctorOrder order, LocalDateTime scheduledAt) {
        // Skip if a PENDING task already exists for this order
        if (taskRepository.existsByDoctorOrderIdAndStatus(order.getId(), "PENDING")) return;

        NurseTask task = new NurseTask();
        task.setDoctorOrderId(order.getId());
        task.setIpdAdmissionId(order.getIpdAdmissionId());
        task.setHospitalId(order.getHospitalId());
        task.setScheduledAt(scheduledAt);
        task.setStatus("PENDING");
        taskRepository.save(task);
    }

    private DoctorOrder findOrder(String publicId, Long hospitalId) {
        DoctorOrder order = orderRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + publicId));
        if (!order.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied");
        return order;
    }
}
