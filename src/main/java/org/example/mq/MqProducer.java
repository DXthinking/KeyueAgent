package org.example.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.example.config.RocketMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqProducer {
    private static final Logger logger = LoggerFactory.getLogger(MqProducer.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public MqProducer(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendAfterSalesEvent(AfterSalesMessage message) {
        send(RocketMqConfig.AFTER_SALES_EVENT, message);
    }

    public void sendChatMessagePersist(ChatMessagePersistDTO message) {
        send(RocketMqConfig.CHAT_MESSAGE_PERSIST, message);
    }

    public void sendAgentEscalation(String message) {
        send(RocketMqConfig.AGENT_ESCALATION, message);
    }

    private void send(String topic, Object message) {
        try {
            rocketMQTemplate.convertAndSend(topic, message);
        } catch (Exception e) {
            logger.error("发送 RocketMQ 消息失败 topic={}", topic, e);
        }
    }
}
