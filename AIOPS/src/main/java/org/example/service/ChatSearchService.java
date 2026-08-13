package org.example.service;

import org.example.dao.es.ChatMessageEsRepository;
import org.example.model.es.ChatMessageDoc;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatSearchService {
    private final ChatMessageEsRepository repository;
    public ChatSearchService(ChatMessageEsRepository repository) { this.repository = repository; }

    public void indexMessage(String sessionId, String userId, String role, String content) {
        ChatMessageDoc doc = new ChatMessageDoc(); doc.setId(UUID.randomUUID().toString()); doc.setSessionId(sessionId); doc.setUserId(userId); doc.setRole(role); doc.setContent(content); doc.setCreatedAt(LocalDateTime.now()); repository.save(doc);
    }

    public List<ChatMessageDoc> searchByContent(String keyword, String userId) {
        return repository.findByUserIdAndContentContaining(userId, keyword);
    }
}
