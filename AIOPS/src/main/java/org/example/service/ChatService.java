package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final DateTimeTools dateTimeTools;
    private final OrderQueryTool orderQueryTool;
    private final LogisticsTool logisticsTool;
    private final RefundTool refundTool;
    private final ReturnTool returnTool;
    private final PolicySearchTool policySearchTool;
    private final ComplaintTool complaintTool;
    private final VoucherTool voucherTool;
    private final NotificationTool notificationTool;
    private final ToolCallbackProvider tools;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    public ChatService(DateTimeTools dateTimeTools, OrderQueryTool orderQueryTool, LogisticsTool logisticsTool,
                       RefundTool refundTool, ReturnTool returnTool, PolicySearchTool policySearchTool,
                       ComplaintTool complaintTool, VoucherTool voucherTool, NotificationTool notificationTool,
                       @Autowired(required = false) ToolCallbackProvider tools) {
        this.dateTimeTools = dateTimeTools; this.orderQueryTool = orderQueryTool; this.logisticsTool = logisticsTool;
        this.refundTool = refundTool; this.returnTool = returnTool; this.policySearchTool = policySearchTool;
        this.complaintTool = complaintTool; this.voucherTool = voucherTool; this.notificationTool = notificationTool; this.tools = tools;
    }

    public DashScopeApi createDashScopeApi() { return DashScopeApi.builder().apiKey(dashScopeApiKey).build(); }

    public DashScopeChatModel createChatModel(DashScopeApi api, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder().dashScopeApi(api).defaultOptions(DashScopeChatOptions.builder()
                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME).withTemperature(temperature).withMaxToken(maxToken).withTopP(topP).build()).build();
    }

    public DashScopeChatModel createStandardChatModel(DashScopeApi api) { return createChatModel(api, 0.7, 2000, 0.9); }

    public String buildSystemPrompt(List<Map<String, String>> history, String userId) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是客悦电商平台的专业售后智能客服，当前用户ID为 ").append(userId == null ? "anonymous" : userId).append("。\n")
                .append("你负责订单查询、物流跟踪、退款、退货、投诉、补偿券和售后政策咨询。涉及规则时必须先调用 queryInternalDocs，不得凭空编造。\n")
                .append("操作前先核对订单和用户；退款超过5000元、补偿券超过50元、CRITICAL投诉必须说明需要人工审核或升级。\n")
                .append("需要查物流时可使用 queryLogistics 或 MCP 物流工具，需要当前时间时使用 getCurrentDateTime。\n\n");
        if (history != null && !history.isEmpty()) {
            prompt.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) prompt.append("user".equals(msg.get("role")) ? "用户: " : "客服: ").append(msg.get("content")).append("\n");
            prompt.append("--- 对话历史结束 ---\n\n");
        }
        return prompt.append("请直接、清晰、合规地解决用户的新问题；无法确认时如实说明并建议转人工。").toString();
    }

    public String buildSystemPrompt(List<Map<String, String>> history) { return buildSystemPrompt(history, "anonymous"); }

    public Object[] buildMethodToolsArray() {
        return new Object[]{dateTimeTools, orderQueryTool, logisticsTool, refundTool, returnTool,
                policySearchTool, complaintTool, voucherTool, notificationTool};
    }

    public ToolCallback[] getToolCallbacks() { return tools == null ? new ToolCallback[0] : tools.getToolCallbacks(); }

    public void logAvailableTools() {
        ToolCallback[] callbacks = getToolCallbacks();
        logger.info("可用 MCP 工具数量: {}", callbacks.length);
        for (ToolCallback callback : callbacks) logger.info(">>> {}", callback.getToolDefinition().name());
    }

    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder().name("after_sales_assistant").model(chatModel).systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray()).tools(getToolCallbacks()).build();
    }

    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        var response = agent.call(question); return response.getText();
    }
}
