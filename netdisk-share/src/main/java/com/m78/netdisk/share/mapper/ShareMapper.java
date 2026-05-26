package com.m78.netdisk.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.share.domain.po.Share;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShareMapper extends BaseMapper<Share> {

    @Select("SELECT * FROM shares WHERE owner_id = #{ownerId} AND NOT is_canceled " +
            "AND (expire_at IS NULL OR expire_at > now()) ORDER BY created_at DESC")
    IPage<Share> selectActiveShares(Page<Share> page, @Param("ownerId") Long ownerId);

    @Select("SELECT * FROM shares WHERE share_token = #{token} AND NOT is_canceled " +
            "AND (expire_at IS NULL OR expire_at > now()) " +
            "AND (max_downloads IS NULL OR download_count < max_downloads)")
    Share selectValidShare(@Param("token") String token);

    @Update("UPDATE shares SET download_count = download_count + 1 WHERE id = #{id}")
    int incrementDownloadCount(@Param("id") Long id);
}