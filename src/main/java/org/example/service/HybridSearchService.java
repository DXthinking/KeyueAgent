package org.example.service;

import org.example.model.es.PolicyDoc;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HybridSearchService {
    private static final int TOP_K = 3;
    private static final int RRF_K = 60;
    private final VectorSearchService vectorSearchService;
    private final ElasticsearchOperations esOperations;

    public HybridSearchService(VectorSearchService vectorSearchService, ElasticsearchOperations esOperations) {
        this.vectorSearchService = vectorSearchService; this.esOperations = esOperations;
    }

    public List<VectorSearchService.SearchResult> hybridSearch(String query) {
        List<VectorSearchService.SearchResult> vectorResults;
        try { vectorResults = vectorSearchService.searchSimilarDocuments(query, TOP_K); }
        catch (Exception e) { vectorResults = List.of(); }
        List<VectorSearchService.SearchResult> bm25Results = bm25Search(query);
        return rrfFuse(vectorResults, bm25Results);
    }

    private List<VectorSearchService.SearchResult> bm25Search(String query) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.match(m -> m.field("content").query(query)))
                .withMaxResults(TOP_K)
                .build();
        List<VectorSearchService.SearchResult> results = new ArrayList<>();
        for (SearchHit<PolicyDoc> hit : esOperations.search(nativeQuery, PolicyDoc.class)) {
            PolicyDoc doc = hit.getContent();
            VectorSearchService.SearchResult result = new VectorSearchService.SearchResult();
            result.setId(doc.getId()); result.setContent(doc.getContent()); result.setScore(hit.getScore()); result.setMetadata(doc.getSource());
            results.add(result);
        }
        return results;
    }

    private List<VectorSearchService.SearchResult> rrfFuse(List<VectorSearchService.SearchResult> first, List<VectorSearchService.SearchResult> second) {
        Map<String, Double> scores = new HashMap<>(); Map<String, VectorSearchService.SearchResult> items = new HashMap<>();
        addRanked(first, scores, items); addRanked(second, scores, items);
        return scores.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(TOP_K).map(e -> { VectorSearchService.SearchResult r = items.get(e.getKey()); r.setScore(e.getValue().floatValue()); return r; }).collect(Collectors.toList());
    }

    private void addRanked(List<VectorSearchService.SearchResult> results, Map<String, Double> scores, Map<String, VectorSearchService.SearchResult> items) {
        for (int i = 0; i < results.size(); i++) { VectorSearchService.SearchResult result = results.get(i); String key = result.getId() == null ? result.getContent() : result.getId(); scores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum); items.putIfAbsent(key, result); }
    }

    public String formatResults(String query) {
        List<VectorSearchService.SearchResult> results = hybridSearch(query);
        if (results.isEmpty()) return "未检索到匹配的售后政策，请转人工确认。";
        StringBuilder builder = new StringBuilder("售后政策检索结果：\n");
        for (int i = 0; i < results.size(); i++) builder.append(i + 1).append(". ").append(results.get(i).getContent()).append("\n");
        return builder.toString();
    }
}
