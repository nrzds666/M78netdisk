package com.m78.netdisk.share.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.share.domain.dto.CreateShareDTO;
import com.m78.netdisk.share.domain.vo.ShareVO;

import java.util.List;

public interface IShareService {
    ShareVO createShare(Long ownerId, CreateShareDTO dto);
    void cancelShare(Long ownerId, Long shareId);
    IPage<ShareVO> listMyShares(Long ownerId, Integer pageNum, Integer size);
    ShareVO accessShare(String shareToken, String password);
    ShareVO downloadFromShare(String shareToken, String password);
    IPage<ShareVO> listReceivedShares(Long userId, Integer pageNum, Integer size);

    // Browse files inside a shared folder
    IPage<ItemVO> listShareItems(String shareToken, String password, Long parentId, Integer page, Integer size);

    // Download a specific file from a share (HTTP stream)
    FileDownloadVO getShareDownloadInfo(String shareToken, String password, Long itemId);

    // Save shared files to current user's storage
    List<ItemVO> saveShareFiles(String shareToken, String password, List<Long> itemIds);
}