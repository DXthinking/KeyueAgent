package org.example.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dao.OrderDao;
import org.example.dao.RefundDao;
import org.example.model.Order;
import org.example.model.Refund;
import org.example.mq.AfterSalesMessage;
import org.example.mq.MqProducer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class RefundTool {
    private final OrderDao orderDao;
    private final RefundDao refundDao;
    private final MqProducer mqProducer;
    private final ObjectMapper objectMapper;
    private final BigDecimal threshold;

    public RefundTool(OrderDao orderDao, RefundDao refundDao, MqProducer mqProducer, ObjectMapper objectMapper,
                      @Value("${after-sales.refund.threshold:5000}") BigDecimal threshold) {
        this.orderDao = orderDao; this.refundDao = refundDao; this.mqProducer = mqProducer;
        this.objectMapper = objectMapper; this.threshold = threshold;
    }

    @Tool(description = "为订单创建退款申请。金额超过5000元时状态为PENDING_REVIEW并通知人工审核")
    public String createRefund(@ToolParam(description = "订单ID") String orderId,
                               @ToolParam(description = "退款原因") String reason,
                               @ToolParam(description = "退款金额") String amount) {
        try {
            Order order = orderDao.selectById(orderId);
            if (order == null) return "未找到订单: " + orderId;
            BigDecimal refundAmount = new BigDecimal(amount);
            String refundId = "RF" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            String status = refundAmount.compareTo(threshold) > 0 ? "PENDING_REVIEW" : "PENDING";
            Refund refund = new Refund();
            refund.setId(refundId); refund.setOrderId(orderId); refund.setUserId(order.getUserId());
            refund.setAmount(refundAmount); refund.setReason(reason); refund.setStatus(status); refund.setCreatedAt(LocalDateTime.now());
            refundDao.insert(refund);
            mqProducer.sendAfterSalesEvent(AfterSalesMessage.builder().event("REFUND_CREATED")
                    .orderId(orderId).userId(order.getUserId()).refundId(refundId).reason(reason)
                    .amount(refundAmount).timestamp(LocalDateTime.now()).build());
            return objectMapper.writeValueAsString(refund);
        } catch (Exception e) { return "创建退款失败: " + e.getMessage(); }
    }

    @Tool(description = "查询退款申请状态")
    public String queryRefund(@ToolParam(description = "退款申请ID") String refundId) {
        Refund refund = refundDao.selectById(refundId);
        if (refund == null) return "未找到退款申请: " + refundId;
        try { return objectMapper.writeValueAsString(refund); } catch (Exception e) { return refund.toString(); }
    }
}
