package com.m78.netdisk.album;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.album.domain.dto.AddItemsDTO;
import com.m78.netdisk.album.domain.dto.CreateAlbumDTO;
import com.m78.netdisk.album.domain.dto.UpdateAlbumDTO;
import com.m78.netdisk.album.domain.po.Album;
import com.m78.netdisk.album.domain.po.AlbumItem;
import com.m78.netdisk.album.domain.vo.AlbumVO;
import com.m78.netdisk.album.mapper.AlbumItemMapper;
import com.m78.netdisk.album.mapper.AlbumMapper;
import com.m78.netdisk.album.service.impl.AlbumServiceImpl;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.mapper.ItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlbumServiceImplTest {

    @Mock private AlbumMapper albumMapper;
    @Mock private AlbumItemMapper albumItemMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private StorageService storageService;

    @InjectMocks
    private AlbumServiceImpl albumService;

    private static final Long OWNER_ID = 1L;
    private static final Long ALBUM_ID = 10L;

    // ==================== createAlbum ====================

    @Test
    void createAlbum_shouldCreateAlbum() {
        CreateAlbumDTO dto = new CreateAlbumDTO();
        dto.setName("旅行相册");

        when(albumMapper.insert(any(Album.class))).thenAnswer(invocation -> {
            Album a = invocation.getArgument(0);
            a.setId(1L);
            return 1;
        });
        when(albumMapper.countItems(1L)).thenReturn(0);

        AlbumVO vo = albumService.createAlbum(OWNER_ID, dto);

        assertNotNull(vo);
        assertEquals("旅行相册", vo.getName());
        assertEquals(Integer.valueOf(0), vo.getItemCount());
        verify(albumMapper).insert(any(Album.class));
    }

    @Test
    void createAlbum_shouldAddInitialItems() {
        CreateAlbumDTO dto = new CreateAlbumDTO();
        dto.setName("旅行相册");
        dto.setItemIds(Arrays.asList(101L, 102L));

        Item item1 = new Item()
                .setId(101L)
                .setOwnerId(OWNER_ID)
                .setMimeType("image/jpeg")
                .setIsDeleted(false);
        Item item2 = new Item()
                .setId(102L)
                .setOwnerId(OWNER_ID)
                .setMimeType("video/mp4")
                .setIsDeleted(false);

        when(albumMapper.insert(any(Album.class))).thenAnswer(invocation -> {
            Album a = invocation.getArgument(0);
            a.setId(ALBUM_ID);
            return 1;
        });
        when(albumItemMapper.selectLatestItemId(ALBUM_ID)).thenReturn(101L);
        when(itemMapper.selectBatchIds(Arrays.asList(101L, 102L))).thenReturn(Arrays.asList(item1, item2));
        when(albumMapper.countItems(ALBUM_ID)).thenReturn(2);

        AlbumVO vo = albumService.createAlbum(OWNER_ID, dto);

        assertNotNull(vo);
        assertEquals("旅行相册", vo.getName());
        assertEquals(Long.valueOf(101L), vo.getCoverItemId());
        assertEquals(Integer.valueOf(2), vo.getItemCount());
        verify(albumItemMapper, times(2)).insert(any(AlbumItem.class));
    }

    @Test
    void createAlbum_shouldRejectNonMediaItem() {
        CreateAlbumDTO dto = new CreateAlbumDTO();
        dto.setName("测试");
        dto.setItemIds(Collections.singletonList(101L));

        Item item = new Item()
                .setId(101L)
                .setOwnerId(OWNER_ID)
                .setMimeType("application/pdf")
                .setIsDeleted(false);

        when(albumMapper.insert(any(Album.class))).thenAnswer(invocation -> {
            Album a = invocation.getArgument(0);
            a.setId(ALBUM_ID);
            return 1;
        });
        when(itemMapper.selectBatchIds(Collections.singletonList(101L))).thenReturn(Collections.singletonList(item));

        BizException ex = assertThrows(BizException.class,
                () -> albumService.createAlbum(OWNER_ID, dto));
        assertTrue(ex.getMessage().contains("只能添加图片或视频文件"));
    }

    // ==================== deleteAlbum ====================

    @Test
    void deleteAlbum_shouldDelete() {
        Album album = new Album()
                .setId(ALBUM_ID)
                .setUserId(OWNER_ID);

        when(albumMapper.selectById(ALBUM_ID)).thenReturn(album);
        when(albumMapper.deleteById(ALBUM_ID)).thenReturn(1);

        albumService.deleteAlbum(OWNER_ID, ALBUM_ID);

        verify(albumMapper).deleteById(ALBUM_ID);
    }

    @Test
    void deleteAlbum_shouldRejectNonOwner() {
        Album album = new Album()
                .setId(ALBUM_ID)
                .setUserId(999L); // different owner

        when(albumMapper.selectById(ALBUM_ID)).thenReturn(album);

        BizException ex = assertThrows(BizException.class,
                () -> albumService.deleteAlbum(OWNER_ID, ALBUM_ID));
        assertTrue(ex.getMessage().contains("无权操作此相册"));
    }

    // ==================== updateAlbum ====================

    @Test
    void updateAlbum_shouldUpdateName() {
        Album album = new Album()
                .setId(ALBUM_ID)
                .setUserId(OWNER_ID)
                .setName("旧名字");

        UpdateAlbumDTO dto = new UpdateAlbumDTO();
        dto.setName("新名字");

        when(albumMapper.selectById(ALBUM_ID)).thenReturn(album);
        when(albumMapper.updateById(album)).thenReturn(1);
        when(albumMapper.countItems(ALBUM_ID)).thenReturn(0);

        AlbumVO vo = albumService.updateAlbum(OWNER_ID, ALBUM_ID, dto);

        assertEquals("新名字", vo.getName());
        assertEquals("新名字", album.getName());
        verify(albumMapper).updateById(album);
    }

    // ==================== addItems ====================

    @Test
    void addItems_shouldAddMediaItems() {
        Album album = new Album()
                .setId(ALBUM_ID)
                .setUserId(OWNER_ID);

        AddItemsDTO dto = new AddItemsDTO();
        dto.setItemIds(Arrays.asList(201L, 202L));

        Item item1 = new Item()
                .setId(201L)
                .setOwnerId(OWNER_ID)
                .setMimeType("image/png")
                .setIsDeleted(false)
                .setName("pic1.png");
        Item item2 = new Item()
                .setId(202L)
                .setOwnerId(OWNER_ID)
                .setMimeType("image/jpeg")
                .setIsDeleted(false)
                .setName("pic2.jpg");

        when(albumMapper.selectById(ALBUM_ID)).thenReturn(album);
        when(itemMapper.selectBatchIds(Arrays.asList(201L, 202L))).thenReturn(Arrays.asList(item1, item2));

        albumService.addItems(OWNER_ID, ALBUM_ID, dto);

        verify(albumItemMapper, times(2)).insert(any(AlbumItem.class));
    }

    // ==================== setCover ====================

    @Test
    void setCover_shouldVerifyItemInAlbum() {
        Album album = new Album()
                .setId(ALBUM_ID)
                .setUserId(OWNER_ID)
                .setCoverItemId(null);

        when(albumMapper.selectById(ALBUM_ID)).thenReturn(album);
        when(albumItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(albumMapper.countItems(ALBUM_ID)).thenReturn(1);

        AlbumVO vo = albumService.setCover(OWNER_ID, ALBUM_ID, 101L);

        assertEquals(Long.valueOf(101L), vo.getCoverItemId());
        assertEquals(Long.valueOf(101L), album.getCoverItemId());
        verify(albumMapper).updateById(album);
    }

    // ==================== listAlbums ====================

    @Test
    void listAlbums_shouldReturnPage() {
        Album album = new Album()
                .setId(ALBUM_ID)
                .setUserId(OWNER_ID)
                .setName("我的相册");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Album> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(Collections.singletonList(album));
        page.setTotal(1);

        when(albumMapper.selectByUserId(any(), eq(OWNER_ID))).thenReturn(page);
        when(albumMapper.countItems(ALBUM_ID)).thenReturn(3);

        IPage<AlbumVO> result = albumService.listAlbums(OWNER_ID, 1, 20);

        assertEquals(1, result.getRecords().size());
        assertEquals("我的相册", result.getRecords().get(0).getName());
        assertEquals(Integer.valueOf(3), result.getRecords().get(0).getItemCount());
    }

    // ==================== removeItems ====================

    @Test
    void removeItems_shouldRemoveItems() {
        Album album = new Album()
                .setId(ALBUM_ID)
                .setUserId(OWNER_ID)
                .setCoverItemId(101L);

        when(albumMapper.selectById(ALBUM_ID)).thenReturn(album);
        when(albumItemMapper.selectLatestItemId(ALBUM_ID)).thenReturn(202L);

        albumService.removeItems(OWNER_ID, ALBUM_ID, Collections.singletonList(101L));

        verify(albumItemMapper).delete(any(LambdaQueryWrapper.class));
        // Cover was auto-updated since the removed item was the cover
        assertEquals(Long.valueOf(202L), album.getCoverItemId());
        verify(albumMapper).updateById(album);
    }

    // ==================== getAlbumDetail ====================

    @Test
    void getAlbumDetail_shouldReturnDetailWithItems() {
        Album album = new Album()
                .setId(ALBUM_ID)
                .setUserId(OWNER_ID)
                .setName("详情相册");

        AlbumItem ai1 = new AlbumItem().setAlbumId(ALBUM_ID).setItemId(301L);
        AlbumItem ai2 = new AlbumItem().setAlbumId(ALBUM_ID).setItemId(302L);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AlbumItem> aiPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        aiPage.setRecords(Arrays.asList(ai1, ai2));
        aiPage.setTotal(2);

        Item item1 = new Item()
                .setId(301L)
                .setName("photo1.jpg")
                .setMimeType("image/jpeg")
                .setSize(1000L)
                .setIsDeleted(false);
        Item item2 = new Item()
                .setId(302L)
                .setName("photo2.jpg")
                .setMimeType("image/jpeg")
                .setSize(2000L)
                .setIsDeleted(false);

        when(albumMapper.selectById(ALBUM_ID)).thenReturn(album);
        when(albumItemMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(aiPage);
        when(itemMapper.selectBatchIds(Arrays.asList(301L, 302L))).thenReturn(Arrays.asList(item1, item2));
        when(albumMapper.countItems(ALBUM_ID)).thenReturn(2);

        AlbumVO vo = albumService.getAlbumDetail(OWNER_ID, ALBUM_ID, 1, 20);

        assertNotNull(vo);
        assertEquals("详情相册", vo.getName());
        assertNotNull(vo.getItems());
        assertEquals(2, vo.getItems().size());
        assertEquals("photo1.jpg", vo.getItems().get(0).getName());
        assertEquals("photo2.jpg", vo.getItems().get(1).getName());
    }

    // ==================== validateOwnership edge cases ====================

    @Test
    void deleteAlbum_shouldRejectNullAlbum() {
        when(albumMapper.selectById(ALBUM_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> albumService.deleteAlbum(OWNER_ID, ALBUM_ID));
        assertTrue(ex.getMessage().contains("相册不存在"));
    }
}
