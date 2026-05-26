package com.m78.netdisk.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m78.netdisk.user.domain.po.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE users SET used_bytes = used_bytes + #{delta} WHERE id = #{userId} AND used_bytes + #{delta} <= quota_bytes")
    int tryAddUsedBytes(@Param("userId") Long userId, @Param("delta") Long delta);

    @Update("UPDATE users SET used_bytes = GREATEST(0, used_bytes - #{delta}) WHERE id = #{userId}")
    int subtractUsedBytes(@Param("userId") Long userId, @Param("delta") Long delta);
}