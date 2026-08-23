package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dao.VoucherDao;
import org.example.model.Voucher;
import org.example.mq.AfterSalesMessage;
import org.example.mq.MqProducer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class VoucherTool {
    private final VoucherDao voucherDao; private final MqProducer mqProducer; private final ObjectMapper objectMapper; private final BigDecimal threshold;
    public VoucherTool(VoucherDao voucherDao, MqProducer mqProducer, ObjectMapper objectMapper, @Value("${after-sales.voucher.threshold:50}") BigDecimal threshold) { this.voucherDao = voucherDao; this.mqProducer = mqProducer; this.objectMapper = objectMapper; this.threshold = threshold; }

    @Tool(description = "发放售后补偿券，金额超过50元进入人工审核")
    public String issueVoucher(@ToolParam(description = "用户ID") String userId, @ToolParam(description = "补偿金额") String amount, @ToolParam(description = "补偿原因") String reason) {
        try {
            BigDecimal value = new BigDecimal(amount); Voucher item = new Voucher(); String id = "VC" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            item.setId(id); item.setUserId(userId); item.setAmount(value); item.setReason(reason); item.setStatus(value.compareTo(threshold) > 0 ? "PENDING_REVIEW" : "ISSUED"); item.setValidUntil(LocalDate.now().plusMonths(3)); item.setCreatedAt(LocalDateTime.now()); voucherDao.insert(item);
            String event = "PENDING_REVIEW".equals(item.getStatus()) ? "VOUCHER_REVIEW_REQUIRED" : "VOUCHER_ISSUED";
            mqProducer.sendAfterSalesEvent(AfterSalesMessage.builder().event(event).userId(userId).voucherId(id).amount(value).reason(reason).timestamp(LocalDateTime.now()).build());
            return objectMapper.writeValueAsString(item);
        } catch (Exception e) { return "发放补偿券失败: " + e.getMessage(); }
    }
}
