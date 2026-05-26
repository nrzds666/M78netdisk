package com.m78.netdisk.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m78.netdisk.common.domain.po.StorageNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StorageNodeMapper extends BaseMapper<StorageNode> {
}
