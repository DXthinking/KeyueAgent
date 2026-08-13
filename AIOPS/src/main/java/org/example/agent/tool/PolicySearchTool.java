package org.example.agent.tool;

import org.example.service.HybridSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class PolicySearchTool {
    private final HybridSearchService hybridSearchService;
    public PolicySearchTool(HybridSearchService hybridSearchService) { this.hybridSearchService = hybridSearchService; }

    @Tool(description = "搜索售后政策、退换货规则、退款时效和投诉流程")
    public String queryInternalDocs(@ToolParam(description = "搜索关键词") String query) {
        return hybridSearchService.formatResults(query);
    }
}
