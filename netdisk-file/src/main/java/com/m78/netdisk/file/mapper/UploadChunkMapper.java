package com.m78.netdisk.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m78.netdisk.file.domain.po.UploadChunk;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UploadChunkMapper extends BaseMapper<UploadChunk> {
}