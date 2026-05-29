package com.m78.netdisk.album.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.album.domain.po.Album;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlbumMapper extends BaseMapper<Album> {

    @Select("SELECT COUNT(*) FROM album_items WHERE album_id = #{albumId}")
    int countItems(@Param("albumId") Long albumId);

    @Select("SELECT a.* FROM albums a WHERE a.user_id = #{userId} ORDER BY a.sort_order ASC, a.created_at DESC")
    IPage<Album> selectByUserId(Page<?> page, @Param("userId") Long userId);
}
