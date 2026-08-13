package org.example.mq;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagePersistDTO {
    private String sessionId;
    private String userId;
    private String role;
    private String content;
    private LocalDateTime timestamp;
}
