package com.back.guestbook.mapper;

import com.back.guestbook.dto.GuestbookNoteDto;
import com.back.guestbook.dto.GuestbookStickerDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 방명록 공개 조회·작성 (guestbook_notes · guestbook_note_stickers · guestbook_stickers) */
@Mapper
public interface GuestbookMapper {

    String findPolicySetting(@Param("code") String code);

    /** 회원 이름으로 남길 때의 표시 이름 */
    String findUserName(@Param("userId") Long userId);

    /** 글이 하나라도 있는 학기 라벨, 최신순 */
    List<String> findSemesters();

    /** 한 학기의 글 (state=normal), 최신순 */
    List<GuestbookNoteDto> findNotes(@Param("semester") String semester);

    GuestbookNoteDto findNoteById(@Param("noteId") Long noteId);

    List<GuestbookNoteDto.StickerOnNote> findNoteStickers(@Param("noteIds") List<Long> noteIds);

    /** 서랍에 보이는 스티커 (state=normal), 순서대로 */
    List<GuestbookStickerDto> findStickers();

    GuestbookStickerDto findStickerById(@Param("stickerId") Long stickerId);

    int countStickersByIds(@Param("stickerIds") List<Long> stickerIds);

    void insertNote(@Param("n") GuestbookNoteDto note, @Param("ipHash") String ipHash);

    void insertNoteStickers(@Param("noteId") Long noteId, @Param("stickerIds") List<Long> stickerIds);

    /** 작성자 본인 삭제 — user_id 가 맞을 때만 */
    int deleteOwnNote(@Param("noteId") Long noteId, @Param("userId") Long userId);
}
