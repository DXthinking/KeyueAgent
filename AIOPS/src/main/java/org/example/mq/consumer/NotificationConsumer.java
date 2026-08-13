package org.example.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.example.config.RocketMqConfig;
import org.example.mq.AfterSalesMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(topic = RocketMqConfig.AFTER_SALES_EVENT, consumerGroup = RocketMqConfig.NOTIFICATION_GROUP)
public class NotificationConsumer implements RocketMQListener<String> {
    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);
    private final ObjectMapper objectMapper;

    public NotificationConsumer(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    public void onMessage(String body) {
        try {
            AfterSalesMessage message = objectMapper.readValue(body, AfterSalesMessage.class);
            logger.info("模拟发送售后通知: event={}, userId={}, orderId={}", message.getEvent(), message.getUserId(), message.getOrderId());
        } catch (Exception e) {
            logger.error("处理售后通知消息失败", e);
        }
    }
}
