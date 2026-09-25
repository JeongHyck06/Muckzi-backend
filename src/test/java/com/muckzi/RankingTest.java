package com.muckzi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RankingTest {

    static KakaoClient.Doc doc(String id, String distance) {
        return doc(id, distance, "음식점 > 한식 > 국밥");
    }

    static KakaoClient.Doc doc(String id, String distance, String category) {
        return new KakaoClient.Doc(id, "가게" + id, category, "", "서울 구로구 항동 1", "",
                "126.8", "37.4", "https://place.map.kakao.com/" + id, distance);
    }

    static KakaoClient.Panel panel(String... dishes) {
        return new KakaoClient.Panel(List.of(dishes).stream().map(d -> new Ranking.Dish(d, 5000)).toList(),
                null, "영업 중 · 21:00 까지", "11:00 ~ 21:00", true);
    }

    @Test
    void ranksByMenuScoreThenDistanceAndDedups() {
        var gukbap = new AiClient.Menu("국밥", "국밥 순대국밥", "밥류", "한식", 1.2, "국물요리");
        var pajeon = new AiClient.Menu("파전", "파전", "부침류", "한식", 0.6, "튀김");
        Map<AiClient.Menu, List<KakaoClient.Doc>> found = new LinkedHashMap<>();
        found.put(gukbap, List.of(doc("1", "400"), doc("2", "100")));
        found.put(pajeon, List.of(doc("2", "100"), doc("3", "0")));

        List<Ranking.Candidate> ranked = Ranking.rank(found, 500, 10);

        assertEquals(List.of("2", "1", "3"), ranked.stream().map(c -> c.doc().id()).toList());
        assertEquals(96, ranked.get(0).match());
        assertEquals(gukbap, ranked.get(0).menu());
    }

    @Test
    void dropsPlacesOfOtherCuisine() {
        var gukbap = new AiClient.Menu("국밥", "국밥", "밥류", "한식", 1.0, "국물요리");
        Map<AiClient.Menu, List<KakaoClient.Doc>> found = Map.of(gukbap,
                List.of(doc("1", "100", "음식점 > 중식 > 중국요리"), doc("2", "200"), doc("3", "300", "음식점 > 술집")));

        assertEquals(List.of("2", "3"), Ranking.rank(found, 500, 10).stream().map(c -> c.doc().id()).toList());
    }

    @Test
    void servesMatchesWithoutSpacesAndLastWord() {
        assertTrue(Ranking.serves("라면", "라면 (점심특식)"));
        assertTrue(Ranking.serves("소고기 국밥", "소고기국밥"));
        assertTrue(Ranking.serves("굴 국밥", "순대국밥"));
        assertFalse(Ranking.serves("라면", "우동 (점심특식)"));
        assertFalse(Ranking.serves("라면", "라면사리"));
    }

    @Test
    void keepsOnlyPlacesThatSellTheMenuAndPutsVerifiedFirst() {
        var ramen = new AiClient.Menu("라면", "라면 라면만", "면류", "분식", 0.9, "국물요리");
        Map<AiClient.Menu, List<KakaoClient.Doc>> found = Map.of(ramen,
                List.of(doc("sells", "300"), doc("nope", "100"), doc("unknown", "50")));
        Map<String, KakaoClient.Panel> panels = Map.of(
                "sells", panel("김밥", "라면 (점심특식)"),
                "nope", panel("우동", "김밥"));

        List<Ranking.Place> places = Ranking.verify(Ranking.rank(found, 500, 10), panels::get, 10);

        assertEquals(List.of("sells", "unknown"), places.stream().map(Ranking.Place::id).toList());
        Ranking.Place first = places.get(0);
        assertEquals("라면 (점심특식)", first.dish().name());
        assertEquals(List.of("라면 (점심특식)", "김밥"), first.dishes().stream().map(Ranking.Dish::name).toList());
        assertEquals("영업 중 · 21:00 까지", first.hours());
        assertEquals(null, places.get(1).dish());
    }
}
