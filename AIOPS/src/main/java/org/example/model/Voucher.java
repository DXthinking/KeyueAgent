package org.example.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_voucher")
public class Voucher {
    @TableId
    private String id;
    private String userId;
    private BigDecimal amount;
    private String reason;
    private String status;
    private LocalDate validUntil;
    private LocalDateTime createdAt;
}
