package org.example.mq.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.example.config.RocketMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(topic = RocketMqConfig.AGENT_ESCALATION, consumerGroup = RocketMqConfig.SEAT_GROUP)
public class EscalationConsumer implements RocketMQListener<String> {
    private static final Logger logger = LoggerFactory.getLogger(EscalationConsumer.class);

    @Override
    public void onMessage(String body) {
        logger.warn("创建转人工工单: {}", body);
    }
}
