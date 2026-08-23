package org.example.agent.tool;

import org.example.mq.MqProducer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class NotificationTool {
    private final MqProducer mqProducer;
    public NotificationTool(MqProducer mqProducer) { this.mqProducer = mqProducer; }

    @Tool(description = "发送售后通知或转人工通知")
    public String sendNotification(@ToolParam(description = "事件类型") String eventType, @ToolParam(description = "用户ID") String userId, @ToolParam(description = "订单ID，可为空") String orderId, @ToolParam(description = "通知内容") String detail) {
        mqProducer.sendAgentEscalation(String.format("event=%s,userId=%s,orderId=%s,detail=%s", eventType, userId, orderId, detail));
        return "通知已提交异步处理";
    }
}
