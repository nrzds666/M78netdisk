package com.m78.netdisk.album.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m78.netdisk.album.domain.po.AlbumItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AlbumItemMapper extends BaseMapper<AlbumItem> {

    @Select("SELECT item_id FROM album_items WHERE album_id = #{albumId} ORDER BY added_at DESC")
    List<Long> selectItemIdsByAlbumId(@Param("albumId") Long albumId);

    @Select("SELECT item_id FROM album_items WHERE album_id = #{albumId} ORDER BY added_at DESC LIMIT 1")
    Long selectLatestItemId(@Param("albumId") Long albumId);

    @Select("SELECT i.thumbnail_key FROM album_items ai " +
            "JOIN items i ON i.id = ai.item_id " +
            "WHERE ai.album_id = #{albumId} " +
            "ORDER BY ai.added_at DESC LIMIT 1")
    String selectLatestThumbnailKey(@Param("albumId") Long albumId);
}
