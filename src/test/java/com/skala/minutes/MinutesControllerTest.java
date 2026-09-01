package com.skala.minutes;

import com.skala.minutes.minutes.MeetingReport;
import com.skala.minutes.minutes.MinutesService;
import com.skala.minutes.web.MinutesController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 모델은 부르지 않는다. 키가 없어도 돌아간다.
 * 확인하는 것은 "우리 코드가 요청을 제대로 받고 응답을 제대로 내보내는가" 뿐이다.
 */
@WebMvcTest(MinutesController.class)
class MinutesControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean MinutesService minutesService;

    @Test
    void 뼈대가_뜬다() throws Exception {
        mvc.perform(get("/api/minutes/ping"))
           .andExpect(status().isOk());
    }

    @Test
    void 빈_회의록은_400_이다() throws Exception {
        mvc.perform(post("/api/minutes/summary")
                        .contentType("application/json")
                        .content("{\"text\":\"\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void 정리_결과가_JSON_으로_나간다() throws Exception {
        given(minutesService.report(anyString())).willReturn(
                new MeetingReport("주간 개발 회의", "타임아웃을 줄이기로 했다",
                        List.of("타임아웃 10초"),
                        List.of(new MeetingReport.ActionItem("이도현", "타임아웃 반영", "수요일"))));

        mvc.perform(post("/api/minutes/report")
                        .contentType("application/json")
                        .content("{\"text\":\"회의 내용\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("주간 개발 회의"))
           .andExpect(jsonPath("$.actionItems[0].owner").value("이도현"));
    }
}
