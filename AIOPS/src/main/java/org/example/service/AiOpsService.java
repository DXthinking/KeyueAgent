package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AiOpsService {
    private static final Logger logger = LoggerFactory.getLogger(AiOpsService.class);
    private final ChatService chatService;

    public AiOpsService(ChatService chatService) { this.chatService = chatService; }

    public Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks, String userId) throws GraphRunnerException {
        ReactAgent planner = buildPlannerAgent(chatModel, toolCallbacks, userId);
        ReactAgent executor = buildExecutorAgent(chatModel, toolCallbacks, userId);
        SupervisorAgent supervisor = SupervisorAgent.builder().name("after_sales_supervisor")
                .description("负责售后分诊、处理建议与人工升级的多 Agent 控制器").model(chatModel)
                .systemPrompt(buildSupervisorSystemPrompt()).subAgents(List.of(planner, executor)).build();
        String task = "针对用户 " + userId + " 执行一次售后智能分诊巡检：查询其订单，识别退款、物流、投诉等异常，必要时给出合规处理建议或转人工，最终输出《售后诊断报告》。";
        logger.info("开始执行售后智能分诊流程, userId={}", userId);
        return supervisor.invoke(task);
    }

    public Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) throws GraphRunnerException {
        return executeAiOpsAnalysis(chatModel, toolCallbacks, "anonymous");
    }

    public Optional<String> extractFinalReport(OverAllState state) {
        return state.value("planner_plan").filter(AssistantMessage.class::isInstance).map(AssistantMessage.class::cast).map(AssistantMessage::getText);
    }

    private ReactAgent buildPlannerAgent(DashScopeChatModel model, ToolCallback[] callbacks, String userId) {
        return ReactAgent.builder().name("triage_planner_agent").description("规划售后问题分诊步骤")
                .model(model).systemPrompt(buildPlannerPrompt(userId)).methodTools(chatService.buildMethodToolsArray()).tools(callbacks).outputKey("planner_plan").build();
    }

    private ReactAgent buildExecutorAgent(DashScopeChatModel model, ToolCallback[] callbacks, String userId) {
        return ReactAgent.builder().name("after_sales_executor_agent").description("执行售后查询和处理步骤")
                .model(model).systemPrompt(buildExecutorPrompt(userId)).methodTools(chatService.buildMethodToolsArray()).tools(callbacks).outputKey("executor_feedback").build();
    }

    private String buildPlannerPrompt(String userId) {
        return """
                你是售后分诊 Planner，用户ID为 %s。
                先规划查询订单、政策和物流的步骤，再根据 Executor 反馈重新规划。严禁编造订单、政策或处理结果。
                退款金额超过5000元、补偿券超过50元、CRITICAL投诉必须标记人工审核/升级。
                decision=FINISH 时直接输出 Markdown《售后诊断报告》，包含用户诉求、已核实事实、政策依据、处理结果、风险与后续建议。
                """.formatted(userId);
    }

    private String buildExecutorPrompt(String userId) {
        return """
                你是售后执行 Agent，用户ID为 %s。只执行 Planner 指定的第一步，调用真实工具并整理结果。
                如果工具失败或没有数据，原样记录原因，不得自行补全。输出结构化的 SUCCESS/FAILED、证据和下一步建议。
                """.formatted(userId);
    }

    private String buildSupervisorSystemPrompt() {
        return """
                你是售后 Supervisor。先调用 triage_planner_agent，再在其 decision=EXECUTE 时调用 after_sales_executor_agent，依据反馈循环规划，直到 FINISH。
                最终报告必须是纯 Markdown，不能把未查询到的事实当成真实数据；高风险退款、补偿券和严重投诉必须明确转人工。
                """;
    }
}
