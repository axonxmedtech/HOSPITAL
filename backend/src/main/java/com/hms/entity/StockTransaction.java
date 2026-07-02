package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType; // GRN / ISSUE / TRANSFER / RETURN / ADJUSTMENT / WASTE

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "from_store", length = 50)
    private String fromStore;

    @Column(name = "to_store", length = 50)
    private String toStore;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "transaction_time", nullable = false)
    private LocalDateTime transactionTime = LocalDateTime.now();
}
