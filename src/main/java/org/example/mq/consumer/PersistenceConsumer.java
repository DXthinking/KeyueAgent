package org.example.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.example.config.RocketMqConfig;
import org.example.dao.ChatMessageDao;
import org.example.dao.es.ChatMessageEsRepository;
import org.example.model.ChatMessage;
import org.example.model.es.ChatMessageDoc;
import org.example.mq.ChatMessagePersistDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RocketMQMessageListener(topic = RocketMqConfig.CHAT_MESSAGE_PERSIST, consumerGroup = RocketMqConfig.PERSISTENCE_GROUP)
public class PersistenceConsumer implements RocketMQListener<String> {
    private final ObjectMapper objectMapper;
    private final ChatMessageDao chatMessageDao;
    private final ChatMessageEsRepository chatMessageEsRepository;

    public PersistenceConsumer(ObjectMapper objectMapper, ChatMessageDao chatMessageDao,
                               ChatMessageEsRepository chatMessageEsRepository) {
        this.objectMapper = objectMapper;
        this.chatMessageDao = chatMessageDao;
        this.chatMessageEsRepository = chatMessageEsRepository;
    }

    @Override
    public void onMessage(String body) {
        try {
            ChatMessagePersistDTO dto = objectMapper.readValue(body, ChatMessagePersistDTO.class);
            LocalDateTime createdAt = dto.getTimestamp() == null ? LocalDateTime.now() : dto.getTimestamp();
            ChatMessage message = new ChatMessage();
            message.setSessionId(dto.getSessionId());
            message.setUserId(dto.getUserId());
            message.setRole(dto.getRole());
            message.setContent(dto.getContent());
            message.setCreatedAt(createdAt);
            chatMessageDao.insert(message);

            ChatMessageDoc doc = new ChatMessageDoc();
            doc.setId(UUID.randomUUID().toString());
            doc.setSessionId(dto.getSessionId());
            doc.setUserId(dto.getUserId());
            doc.setRole(dto.getRole());
            doc.setContent(dto.getContent());
            doc.setCreatedAt(createdAt);
            chatMessageEsRepository.save(doc);
        } catch (Exception e) {
            throw new IllegalStateException("持久化对话消息失败", e);
        }
    }
}
