package org.example.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.model.Order;

@Mapper
public interface OrderDao extends BaseMapper<Order> {}
