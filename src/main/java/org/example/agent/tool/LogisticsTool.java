package org.example.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class LogisticsTool {
    @Tool(description = "查询订单或运单号的物流轨迹")
    public String queryLogistics(@ToolParam(description = "订单ID或物流单号") String orderIdOrTrackingNo) {
        return "物流查询结果：单号 " + orderIdOrTrackingNo + " 当前为运输中，最近节点为本地分拨中心，预计1-2天送达（演示数据）。";
    }
}
