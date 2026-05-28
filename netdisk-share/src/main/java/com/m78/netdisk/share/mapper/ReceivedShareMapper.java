package com.m78.netdisk.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.share.domain.po.ReceivedShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReceivedShareMapper extends BaseMapper<ReceivedShare> {

    @Select("SELECT rs.*, s.share_token, s.permission, s.expire_at, s.created_at as share_created_at " +
            "FROM received_shares rs " +
            "JOIN shares s ON rs.share_id = s.id " +
            "WHERE rs.user_id = #{userId} " +
            "ORDER BY rs.accessed_at DESC")
    IPage<ReceivedShare> selectByUserId(Page<ReceivedShare> page, @Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM received_shares WHERE user_id = #{userId} AND share_id = #{shareId}")
    int countByUserAndShare(@Param("userId") Long userId, @Param("shareId") Long shareId);
}
