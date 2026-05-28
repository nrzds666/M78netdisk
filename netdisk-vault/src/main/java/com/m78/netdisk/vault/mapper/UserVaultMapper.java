package com.m78.netdisk.vault.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m78.netdisk.vault.domain.po.UserVault;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserVaultMapper extends BaseMapper<UserVault> {
}
