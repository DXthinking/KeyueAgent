package org.example.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_complaint")
public class Complaint {
    @TableId
    private String id;
    private String userId;
    private String orderId;
    private String content;
    private String level;
    private String status;
    private LocalDateTime createdAt;
}
