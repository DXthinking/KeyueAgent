package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dao.ComplaintDao;
import org.example.model.Complaint;
import org.example.mq.AfterSalesMessage;
import org.example.mq.MqProducer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ComplaintTool {
    private final ComplaintDao complaintDao; private final MqProducer mqProducer; private final ObjectMapper objectMapper;
    public ComplaintTool(ComplaintDao complaintDao, MqProducer mqProducer, ObjectMapper objectMapper) { this.complaintDao = complaintDao; this.mqProducer = mqProducer; this.objectMapper = objectMapper; }

    @Tool(description = "创建售后投诉并根据级别触发人工升级")
    public String createComplaint(@ToolParam(description = "用户ID") String userId, @ToolParam(description = "投诉内容") String content, @ToolParam(description = "投诉级别NORMAL/HIGH/CRITICAL") String level) {
        try {
            Complaint item = new Complaint(); String id = "CP" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            item.setId(id); item.setUserId(userId); item.setContent(content); item.setLevel(level); item.setStatus("OPEN"); item.setCreatedAt(LocalDateTime.now()); complaintDao.insert(item);
            mqProducer.sendAfterSalesEvent(AfterSalesMessage.builder().event("COMPLAINT_CREATED").userId(userId).complaintId(id).level(level).reason(content).timestamp(LocalDateTime.now()).build());
            return objectMapper.writeValueAsString(item);
        } catch (Exception e) { return "创建投诉失败: " + e.getMessage(); }
    }

    @Tool(description = "查询投诉处理状态")
    public String queryComplaint(@ToolParam(description = "投诉ID") String complaintId) {
        Complaint item = complaintDao.selectById(complaintId); if (item == null) return "未找到投诉: " + complaintId;
        try { return objectMapper.writeValueAsString(item); } catch (Exception e) { return item.toString(); }
    }
}
