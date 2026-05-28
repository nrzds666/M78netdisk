package com.m78.netdisk.share.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.share.domain.dto.CreateShareDTO;
import com.m78.netdisk.share.domain.vo.ShareVO;

public interface IShareService {
    ShareVO createShare(Long ownerId, CreateShareDTO dto);
    void cancelShare(Long ownerId, Long shareId);
    IPage<ShareVO> listMyShares(Long ownerId, Integer pageNum, Integer size);
    ShareVO accessShare(String shareToken, String password);
    ShareVO downloadFromShare(String shareToken, String password);
    IPage<ShareVO> listReceivedShares(Long userId, Integer pageNum, Integer size);
}