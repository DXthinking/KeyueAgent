package org.example.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.model.ChatMessage;

@Mapper
public interface ChatMessageDao extends BaseMapper<ChatMessage> {}
