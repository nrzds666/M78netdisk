package com.m78.netdisk.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m78.netdisk.file.domain.po.UploadTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UploadTaskMapper extends BaseMapper<UploadTask> {

    @Update("UPDATE upload_tasks SET received_chunks = received_chunks + 1 WHERE id = #{taskId}")
    int incrementReceivedChunks(@Param("taskId") Long taskId);
}