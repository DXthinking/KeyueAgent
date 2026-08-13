package org.example.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMqConfig {
    public static final String AFTER_SALES_EVENT = "after-sales-event";
    public static final String CHAT_MESSAGE_PERSIST = "chat-message-persist";
    public static final String AGENT_ESCALATION = "agent-escalation";
    public static final String NOTIFICATION_GROUP = "notification-srv";
    public static final String PERSISTENCE_GROUP = "persistence-srv";
    public static final String SEAT_GROUP = "seat-srv";
}
