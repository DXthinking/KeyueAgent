package org.example.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_return")
public class Return {
    @TableId
    private String id;
    private String orderId;
    private String userId;
    private String reason;
    private String returnLogisticsNo;
    private String status;
    private LocalDateTime createdAt;
}
