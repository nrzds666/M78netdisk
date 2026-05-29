package com.m78.netdisk.album.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.album.domain.dto.AddItemsDTO;
import com.m78.netdisk.album.domain.dto.CreateAlbumDTO;
import com.m78.netdisk.album.domain.dto.UpdateAlbumDTO;
import com.m78.netdisk.album.domain.vo.AlbumVO;

import java.util.List;

public interface IAlbumService {

    AlbumVO createAlbum(Long userId, CreateAlbumDTO dto);

    void deleteAlbum(Long userId, Long albumId);

    AlbumVO updateAlbum(Long userId, Long albumId, UpdateAlbumDTO dto);

    IPage<AlbumVO> listAlbums(Long userId, Integer page, Integer size);

    AlbumVO getAlbumDetail(Long userId, Long albumId, Integer page, Integer size);

    void addItems(Long userId, Long albumId, AddItemsDTO dto);

    void removeItems(Long userId, Long albumId, List<Long> itemIds);

    AlbumVO setCover(Long userId, Long albumId, Long itemId);
}
