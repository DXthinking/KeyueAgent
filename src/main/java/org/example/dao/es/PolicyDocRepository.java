package org.example.dao.es;

import org.example.model.es.PolicyDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PolicyDocRepository extends ElasticsearchRepository<PolicyDoc, String> {
}
