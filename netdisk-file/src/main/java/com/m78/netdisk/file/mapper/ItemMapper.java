package com.m78.netdisk.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.file.domain.po.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {

    IPage<Item> selectRootItems(Page<Item> page, @Param("ownerId") Long ownerId);

    IPage<Item> selectChildren(Page<Item> page, @Param("ownerId") Long ownerId,
                               @Param("parentId") Long parentId);

    @Select("SELECT COUNT(*) FROM items WHERE owner_id = #{ownerId} AND parent_id = #{parentId} " +
            "AND name = #{name} AND NOT is_deleted AND NOT is_vaulted")
    int countByName(@Param("ownerId") Long ownerId, @Param("parentId") Long parentId,
                    @Param("name") String name);

    @Select("SELECT COALESCE(SUM(size), 0) FROM items WHERE owner_id = #{ownerId} " +
            "AND NOT is_directory AND NOT is_deleted")
    long sumUsedBytesByOwner(@Param("ownerId") Long ownerId);

    @Select("SELECT * FROM items WHERE owner_id = #{ownerId} AND is_vaulted " +
            "AND (#{parentId} IS NULL AND parent_id IS NULL OR parent_id = #{parentId}) " +
            "AND NOT is_deleted ORDER BY is_directory DESC, name ASC")
    IPage<Item> selectVaultItems(Page<Item> page, @Param("ownerId") Long ownerId,
                                 @Param("parentId") Long parentId);

    @Update("UPDATE items SET is_deleted = true, deleted_at = now() " +
            "WHERE id = #{id} AND owner_id = #{ownerId}")
    int softDelete(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Update("UPDATE items SET is_deleted = false, deleted_at = NULL " +
            "WHERE id = #{id} AND owner_id = #{ownerId} AND is_deleted")
    int restore(@Param("id") Long id, @Param("ownerId") Long ownerId);

    IPage<Item> selectTrash(Page<Item> page, @Param("ownerId") Long ownerId);

    @Select("SELECT * FROM items WHERE owner_id = #{ownerId} AND parent_id = #{parentId} AND NOT is_deleted ORDER BY is_directory DESC, name ASC")
    IPage<Item> selectChildrenByOwnerId(Page<?> page, @Param("ownerId") Long ownerId,
                                         @Param("parentId") Long parentId);

    @Select("SELECT * FROM items WHERE owner_id = #{ownerId} AND parent_id IS NULL AND NOT is_deleted ORDER BY is_directory DESC, name ASC")
    IPage<Item> selectRootItemsByOwnerId(Page<?> page, @Param("ownerId") Long ownerId);
}