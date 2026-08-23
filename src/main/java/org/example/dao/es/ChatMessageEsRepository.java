package org.example.dao.es;

import org.example.model.es.ChatMessageDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ChatMessageEsRepository extends ElasticsearchRepository<ChatMessageDoc, String> {
    List<ChatMessageDoc> findByUserIdAndContentContaining(String userId, String keyword);
}
