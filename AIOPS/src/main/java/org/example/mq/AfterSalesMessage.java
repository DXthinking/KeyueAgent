package org.example.mq;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSalesMessage {
    private String event;
    private String orderId;
    private String userId;
    private String refundId;
    private String returnId;
    private String complaintId;
    private String voucherId;
    private String reason;
    private BigDecimal amount;
    private String level;
    private LocalDateTime timestamp;
}
