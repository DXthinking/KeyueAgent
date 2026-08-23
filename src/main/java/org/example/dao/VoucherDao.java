package org.example.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.model.Voucher;

@Mapper
public interface VoucherDao extends BaseMapper<Voucher> {}
