package com.m78.netdisk.album.service;

import com.m78.netdisk.album.domain.vo.AlbumShareVO;
import com.m78.netdisk.album.domain.vo.AlbumVO;

public interface IAlbumShareService {

    /**
     * Create a share link for an album
     */
    AlbumShareVO createShare(Long userId, Long albumId, Integer expireDays);

    /**
     * Get album detail via share token (public, no auth)
     */
    AlbumVO getSharedAlbum(String token);

    /**
     * Stream a shared album file item by token + itemId (public, no auth)
     */
    void streamSharedAlbumFile(String token, Long itemId,
                                javax.servlet.http.HttpServletRequest request,
                                javax.servlet.http.HttpServletResponse response) throws java.io.IOException;
}
