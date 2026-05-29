package com.m78.netdisk.album.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.album.domain.dto.AddItemsDTO;
import com.m78.netdisk.album.domain.dto.CreateAlbumDTO;
import com.m78.netdisk.album.domain.dto.UpdateAlbumDTO;
import com.m78.netdisk.album.domain.vo.AlbumVO;
import com.m78.netdisk.album.service.IAlbumService;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final IAlbumService albumService;

    @PostMapping
    public R<AlbumVO> createAlbum(@Valid @RequestBody CreateAlbumDTO dto) {
        return R.ok(albumService.createAlbum(UserContext.getUserId(), dto));
    }

    @GetMapping
    public R<IPage<AlbumVO>> listAlbums(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(albumService.listAlbums(UserContext.getUserId(), page, size));
    }

    @GetMapping("/{id}")
    public R<AlbumVO> getAlbumDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(albumService.getAlbumDetail(UserContext.getUserId(), id, page, size));
    }

    @PutMapping("/{id}")
    public R<AlbumVO> updateAlbum(@PathVariable Long id,
                                   @Valid @RequestBody UpdateAlbumDTO dto) {
        return R.ok(albumService.updateAlbum(UserContext.getUserId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteAlbum(@PathVariable Long id) {
        albumService.deleteAlbum(UserContext.getUserId(), id);
        return R.ok();
    }

    @PostMapping("/{id}/items")
    public R<Void> addItems(@PathVariable Long id,
                             @Valid @RequestBody AddItemsDTO dto) {
        albumService.addItems(UserContext.getUserId(), id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}/items")
    public R<Void> removeItems(@PathVariable Long id,
                                @RequestParam List<Long> itemIds) {
        albumService.removeItems(UserContext.getUserId(), id, itemIds);
        return R.ok();
    }

    @PutMapping("/{id}/cover")
    public R<AlbumVO> setCover(@PathVariable Long id,
                                @RequestParam Long itemId) {
        return R.ok(albumService.setCover(UserContext.getUserId(), id, itemId));
    }
}
