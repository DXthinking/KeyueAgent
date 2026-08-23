package org.example.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {
    @TableId
    private String id;
    private String userId;
    private String productName;
    private String productCategory;
    private BigDecimal amount;
    private String status;
    private String trackingNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
