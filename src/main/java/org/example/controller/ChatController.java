package org.example.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.example.mq.ChatMessagePersistDTO;
import org.example.mq.MqProducer;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final ChatService chatService; private final AiOpsService aiOpsService; private final MqProducer mqProducer;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public ChatController(ChatService chatService, AiOpsService aiOpsService, MqProducer mqProducer) { this.chatService = chatService; this.aiOpsService = aiOpsService; this.mqProducer = mqProducer; }

    @PostMapping("/chat")
    @SentinelResource(value = "chatStream", blockHandler = "chatBlockHandler")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        if (blank(request.getQuestion())) return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
        String sessionId = sessionId(request.getId()); SessionInfo session = sessions.computeIfAbsent(sessionId, SessionInfo::new);
        try {
            ReactAgent agent = chatService.createReactAgent(chatService.createStandardChatModel(chatService.createDashScopeApi()), chatService.buildSystemPrompt(session.history(), request.getUserId()));
            String answer = chatService.executeChat(agent, request.getQuestion()); session.add(request.getQuestion(), answer);
            persist(sessionId, request.getUserId(), "user", request.getQuestion()); persist(sessionId, request.getUserId(), "assistant", answer);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(answer)));
        } catch (Exception e) { return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage()))); }
    }

    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clear(@RequestBody ClearRequest request) { SessionInfo session = sessions.get(request.getId()); if (session == null) return ResponseEntity.ok(ApiResponse.error("会话不存在")); session.clear(); return ResponseEntity.ok(ApiResponse.success("会话历史已清空")); }

    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    @SentinelResource(value = "chatStream", blockHandler = "chatStreamBlockHandler")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);
        if (blank(request.getQuestion())) { sendAndComplete(emitter, SseMessage.error("问题内容不能为空")); return emitter; }
        executor.execute(() -> {
            String sessionId = sessionId(request.getId()); SessionInfo session = sessions.computeIfAbsent(sessionId, SessionInfo::new); StringBuilder answer = new StringBuilder();
            try {
                ReactAgent agent = chatService.createReactAgent(chatService.createStandardChatModel(chatService.createDashScopeApi()), chatService.buildSystemPrompt(session.history(), request.getUserId()));
                Flux<NodeOutput> stream = agent.stream(request.getQuestion());
                stream.subscribe(output -> {
                    if (output instanceof StreamingOutput streaming && streaming.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
                        String chunk = streaming.message().getText(); if (chunk != null && !chunk.isEmpty()) { answer.append(chunk); send(emitter, SseMessage.content(chunk)); }
                    }
                }, error -> { send(emitter, SseMessage.error(error.getMessage())); emitter.completeWithError(error); }, () -> {
                    String full = answer.toString(); session.add(request.getQuestion(), full); persist(sessionId, request.getUserId(), "user", request.getQuestion()); persist(sessionId, request.getUserId(), "assistant", full); sendAndComplete(emitter, SseMessage.done());
                });
            } catch (Exception e) { send(emitter, SseMessage.error(e.getMessage())); emitter.completeWithError(e); }
        });
        return emitter;
    }

    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    @SentinelResource(value = "aiOpsAnalyze", blockHandler = "aiOpsBlockHandler")
    public SseEmitter aiOps(@RequestBody(required = false) AnalyzeRequest request) {
        SseEmitter emitter = new SseEmitter(600000L); String userId = request == null || blank(request.getUserId()) ? "anonymous" : request.getUserId();
        executor.execute(() -> {
            try {
                DashScopeApi api = chatService.createDashScopeApi(); DashScopeChatModel model = chatService.createChatModel(api, 0.3, 8000, 0.9);
                Optional<OverAllState> state = aiOpsService.executeAiOpsAnalysis(model, chatService.getToolCallbacks(), userId);
                String report = state.flatMap(aiOpsService::extractFinalReport).orElse("未能生成售后诊断报告，请转人工处理。"); send(emitter, SseMessage.content(report)); sendAndComplete(emitter, SseMessage.done());
            } catch (Exception e) { send(emitter, SseMessage.error("售后分诊失败: " + e.getMessage())); emitter.completeWithError(e); }
        });
        return emitter;
    }

    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> session(@PathVariable String sessionId) { SessionInfo item = sessions.get(sessionId); if (item == null) return ResponseEntity.ok(ApiResponse.error("会话不存在")); return ResponseEntity.ok(ApiResponse.success(new SessionInfoResponse(sessionId, item.pairs(), item.createTime))); }

    public SseEmitter chatBlockHandler(ChatRequest request, BlockException ex) { return blockedEmitter("当前请求过多，请稍后重试"); }
    public SseEmitter chatStreamBlockHandler(ChatRequest request, BlockException ex) { return blockedEmitter("当前请求过多，请稍后重试"); }
    public SseEmitter aiOpsBlockHandler(AnalyzeRequest request, BlockException ex) { return blockedEmitter("当前分诊请求过多，请稍后重试"); }

    private void persist(String sessionId, String userId, String role, String content) { if (content != null && !content.isBlank()) mqProducer.sendChatMessagePersist(ChatMessagePersistDTO.builder().sessionId(sessionId).userId(blank(userId) ? "anonymous" : userId).role(role).content(content).timestamp(LocalDateTime.now()).build()); }
    private void send(SseEmitter emitter, SseMessage message) { try { emitter.send(SseEmitter.event().name("message").data(message, MediaType.APPLICATION_JSON)); } catch (IOException e) { emitter.completeWithError(e); } }
    private void sendAndComplete(SseEmitter emitter, SseMessage message) { send(emitter, message); emitter.complete(); }
    private SseEmitter blockedEmitter(String message) { SseEmitter emitter = new SseEmitter(); sendAndComplete(emitter, SseMessage.error(message)); return emitter; }
    private String sessionId(String id) { return blank(id) ? UUID.randomUUID().toString() : id; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    @Data public static class ChatRequest { private String id; @JsonProperty("UserId") private String userId; private String question; }
    @Data public static class AnalyzeRequest { @JsonProperty("UserId") private String userId; }
    @Data public static class ClearRequest { private String id; }
    @Data public static class ChatResponse { private boolean success; private String answer; private String error; static ChatResponse success(String answer) { ChatResponse r = new ChatResponse(); r.success = true; r.answer = answer; return r; } static ChatResponse error(String error) { ChatResponse r = new ChatResponse(); r.error = error; return r; } }
    @Data public static class SseMessage { private String type; private String data; static SseMessage content(String v) { SseMessage m = new SseMessage(); m.type = "content"; m.data = v; return m; } static SseMessage error(String v) { SseMessage m = new SseMessage(); m.type = "error"; m.data = v; return m; } static SseMessage done() { SseMessage m = new SseMessage(); m.type = "done"; return m; } }
    @Data public static class ApiResponse<T> { private int code; private String message; private T data; static <T> ApiResponse<T> success(T data) { ApiResponse<T> r = new ApiResponse<>(); r.code = 200; r.message = "success"; r.data = data; return r; } static <T> ApiResponse<T> error(String message) { ApiResponse<T> r = new ApiResponse<>(); r.code = 400; r.message = message; return r; } }
    @Data public static class SessionInfoResponse { private String sessionId; private int messagePairCount; private long createTime; public SessionInfoResponse() {} public SessionInfoResponse(String id, int count, long time) { sessionId = id; messagePairCount = count; createTime = time; } }

    private static class SessionInfo { private final List<Map<String, String>> messages = new ArrayList<>(); private final long createTime = System.currentTimeMillis(); private final ReentrantLock lock = new ReentrantLock();
        SessionInfo(String ignored) {}
        List<Map<String, String>> history() { lock.lock(); try { return new ArrayList<>(messages); } finally { lock.unlock(); } }
        void add(String question, String answer) { lock.lock(); try { messages.add(Map.of("role", "user", "content", question)); messages.add(Map.of("role", "assistant", "content", answer)); while (messages.size() > 12) { messages.remove(0); messages.remove(0); } } finally { lock.unlock(); } }
        void clear() { lock.lock(); try { messages.clear(); } finally { lock.unlock(); } } int pairs() { return messages.size() / 2; }
    }
}
