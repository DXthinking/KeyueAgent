package org.example.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.model.Complaint;

@Mapper
public interface ComplaintDao extends BaseMapper<Complaint> {}
