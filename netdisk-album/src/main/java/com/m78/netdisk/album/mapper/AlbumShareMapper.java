package com.m78.netdisk.album.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m78.netdisk.album.domain.po.AlbumShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlbumShareMapper extends BaseMapper<AlbumShare> {

    @Select("SELECT * FROM album_shares WHERE share_token = #{token} AND is_active = 1 " +
            "AND (expire_at IS NULL OR expire_at > NOW())")
    AlbumShare selectByActiveToken(@Param("token") String token);
}
