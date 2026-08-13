package org.example.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.example.config.RocketMqConfig;
import org.example.mq.AfterSalesMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RocketMQMessageListener(topic = RocketMqConfig.AFTER_SALES_EVENT, consumerGroup = RocketMqConfig.SEAT_GROUP)
public class SeatConsumer implements RocketMQListener<String> {
    private static final Logger logger = LoggerFactory.getLogger(SeatConsumer.class);
    private final ObjectMapper objectMapper;

    public SeatConsumer(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    public void onMessage(String body) {
        try {
            AfterSalesMessage message = objectMapper.readValue(body, AfterSalesMessage.class);
            boolean highValueRefund = "REFUND_CREATED".equals(message.getEvent())
                    && message.getAmount() != null
                    && message.getAmount().compareTo(new BigDecimal("5000")) > 0;
            boolean criticalComplaint = "COMPLAINT_CREATED".equals(message.getEvent())
                    && "CRITICAL".equalsIgnoreCase(message.getLevel());
            if (highValueRefund || criticalComplaint || "VOUCHER_REVIEW_REQUIRED".equals(message.getEvent())) {
                logger.warn("需要坐席跟进: event={}, userId={}, orderId={}", message.getEvent(), message.getUserId(), message.getOrderId());
            }
        } catch (Exception e) {
            logger.error("处理坐席消息失败", e);
        }
    }
}
