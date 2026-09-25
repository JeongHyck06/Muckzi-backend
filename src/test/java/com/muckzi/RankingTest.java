package com.muckzi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RankingTest {

    static KakaoClient.Doc doc(String id, String distance) {
        return new KakaoClient.Doc(id, "가게" + id, "음식점 > 한식 > 국밥", "", "서울 구로구 항동 1", "",
                "126.8", "37.4", "https://place.map.kakao.com/" + id, distance);
    }

    @Test
    void ranksByMenuScoreThenDistanceAndDedups() {
        var gukbap = new AiClient.Menu("국밥", "국밥 순대국밥", "밥류", "한식", 1.2, "국물요리");
        var pajeon = new AiClient.Menu("파전", "파전", "부침류", "한식", 0.6, "튀김");
        Map<AiClient.Menu, List<KakaoClient.Doc>> found = new LinkedHashMap<>();
        found.put(gukbap, List.of(doc("1", "400"), doc("2", "100")));
        found.put(pajeon, List.of(doc("2", "100"), doc("3", "0")));

        List<Ranking.Place> places = Ranking.rank(found, 500, 10);

        assertEquals(List.of("2", "1", "3"), places.stream().map(Ranking.Place::id).toList());
        assertEquals(96, places.get(0).match());
        assertEquals("국밥 순대국밥", places.get(0).menu());
        assertEquals("한식 · 국밥", places.get(0).category());
        assertEquals("서울 구로구 항동 1", places.get(0).address());
    }
}
