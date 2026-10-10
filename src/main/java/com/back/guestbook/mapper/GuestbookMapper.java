package com.back.guestbook.mapper;

import com.back.guestbook.dto.GuestbookDrawingRow;
import com.back.guestbook.dto.GuestbookNoteDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 방명록 공개 조회·작성·수정 (guestbook_notes · guestbook_drawings) */
@Mapper
public interface GuestbookMapper {

    String findPolicySetting(@Param("code") String code);

    /** 글이 하나라도 있는 학기 라벨, 최신순 */
    List<String> findSemesters();

    /** 한 학기의 글, 최신순. includeHidden 이면 관리자가 숨긴 글도 (관리자 화면에서 바로 복원하려고) */
    List<GuestbookNoteDto> findNotes(@Param("semester") String semester, @Param("includeHidden") boolean includeHidden);

    GuestbookNoteDto findNoteById(@Param("noteId") Long noteId);

    List<GuestbookNoteDto.Drawing> findDrawingsOfNotes(@Param("noteIds") List<Long> noteIds);

    GuestbookDrawingRow findDrawingById(@Param("drawingId") Long drawingId);

    void insertNote(@Param("n") GuestbookNoteDto note, @Param("ipHash") String ipHash);

    /** 본인(로그인·user_id 일치) 글만 고친다 */
    int updateOwnNote(@Param("noteId") Long noteId, @Param("userId") Long userId, @Param("n") GuestbookNoteDto note);

    /** 본인 글만 지운다 (소프트) */
    int deleteOwnNote(@Param("noteId") Long noteId, @Param("userId") Long userId);

    /** 관리자 — 누구 글이든 고친다 (normal·hidden) */
    int updateNoteAsAdmin(@Param("noteId") Long noteId, @Param("n") GuestbookNoteDto note);

    /** 관리자 — 상태 전환 (normal→hidden 숨김, hidden→normal 복원) */
    int updateNoteState(@Param("noteId") Long noteId, @Param("from") String from, @Param("to") String to);

    void insertDrawing(@Param("d") GuestbookDrawingRow drawing);

    int updateDrawingPlacement(@Param("noteId") Long noteId, @Param("d") GuestbookDrawingRow drawing);

    /** 목록에 없는 그림은 뗀다 (소프트). keepIds 가 비면 전부 */
    int softDeleteDrawingsExcept(@Param("noteId") Long noteId, @Param("keepIds") List<Long> keepIds);
}
