package com.back.admin.guestbook.mapper;

import com.back.guestbook.dto.GuestbookNoteDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 방명록 관리 — 글 숨김/복원 (소프트). 스티커는 작성자가 직접 그리므로 관리자가 등록할 것이 없다 (PM 10/10 밤) */
@Mapper
public interface AdminGuestbookMapper {

    /** 한 학기의 글 전부 (normal · hidden · deleted), 최신순 */
    List<GuestbookNoteDto> findNotesAllStates(@Param("semester") String semester);

    List<String> findSemestersAllStates();

    int updateNoteState(@Param("noteId") Long noteId, @Param("from") String from, @Param("to") String to);
}
