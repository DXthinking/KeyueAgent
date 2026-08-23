package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dao.OrderDao;
import org.example.dao.ReturnDao;
import org.example.model.Order;
import org.example.model.Return;
import org.example.mq.AfterSalesMessage;
import org.example.mq.MqProducer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ReturnTool {
    private final OrderDao orderDao; private final ReturnDao returnDao; private final MqProducer mqProducer; private final ObjectMapper objectMapper;
    public ReturnTool(OrderDao orderDao, ReturnDao returnDao, MqProducer mqProducer, ObjectMapper objectMapper) {
        this.orderDao = orderDao; this.returnDao = returnDao; this.mqProducer = mqProducer; this.objectMapper = objectMapper;
    }

    @Tool(description = "为订单创建退货申请")
    public String createReturn(@ToolParam(description = "订单ID") String orderId, @ToolParam(description = "退货原因") String reason) {
        try {
            Order order = orderDao.selectById(orderId);
            if (order == null) return "未找到订单: " + orderId;
            String id = "RT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            Return item = new Return(); item.setId(id); item.setOrderId(orderId); item.setUserId(order.getUserId());
            item.setReason(reason); item.setStatus("PENDING"); item.setCreatedAt(LocalDateTime.now()); returnDao.insert(item);
            mqProducer.sendAfterSalesEvent(AfterSalesMessage.builder().event("RETURN_CREATED").orderId(orderId)
                    .userId(order.getUserId()).returnId(id).reason(reason).timestamp(LocalDateTime.now()).build());
            return objectMapper.writeValueAsString(item);
        } catch (Exception e) { return "创建退货失败: " + e.getMessage(); }
    }

    @Tool(description = "查询退货申请状态")
    public String queryReturn(@ToolParam(description = "退货申请ID") String returnId) {
        Return item = returnDao.selectById(returnId);
        if (item == null) return "未找到退货申请: " + returnId;
        try { return objectMapper.writeValueAsString(item); } catch (Exception e) { return item.toString(); }
    }
}
