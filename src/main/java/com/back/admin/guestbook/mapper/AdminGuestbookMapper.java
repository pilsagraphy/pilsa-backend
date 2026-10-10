package com.back.admin.guestbook.mapper;

import com.back.guestbook.dto.GuestbookNoteDto;
import com.back.guestbook.dto.GuestbookStickerDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 방명록 관리 — 글 숨김/복원, 스티커 등록/수정/삭제 (전부 소프트) */
@Mapper
public interface AdminGuestbookMapper {

    /** 한 학기의 글 전부 (normal · hidden · deleted), 최신순 */
    List<GuestbookNoteDto> findNotesAllStates(@Param("semester") String semester);

    List<String> findSemestersAllStates();

    int updateNoteState(@Param("noteId") Long noteId, @Param("from") String from, @Param("to") String to);

    void insertSticker(@Param("s") GuestbookStickerDto sticker, @Param("userId") Long userId);

    int updateSticker(@Param("stickerId") Long stickerId, @Param("name") String name, @Param("sortOrder") Integer sortOrder);

    int softDeleteSticker(@Param("stickerId") Long stickerId);

    Integer findMaxStickerOrder();
}
