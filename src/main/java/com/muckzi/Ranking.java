package com.muckzi;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Ranking {

    public record Place(String id, String name, String category, String address, String phone, int distance,
                        double lat, double lng, String url, String image, String menu, String labels, int match) {

        Place withImage(String image) {
            return new Place(id, name, category, address, phone, distance, lat, lng, url, image, menu, labels, match);
        }
    }

    private static final Set<String> CUISINES = Set.of("한식", "중식", "일식", "양식");

    private Ranking() {}

    /** 키워드 검색이 느슨해 국밥에 중식당이 걸리므로 메뉴와 다른 나라 음식점은 뺀다 */
    static boolean fits(AiClient.Menu menu, KakaoClient.Doc d) {
        String[] parts = d.category_name().split(" > ");
        String top = parts.length > 1 ? parts[1] : "";
        return !CUISINES.contains(menu.cuisine()) || !CUISINES.contains(top) || top.equals(menu.cuisine());
    }

    /** 일치도 = 메뉴 점수 80 + 거리 20, 같은 가게는 가장 높은 메뉴 하나만 남긴다 */
    public static List<Place> rank(Map<AiClient.Menu, List<KakaoClient.Doc>> found, int radius, int limit) {
        double top = found.keySet().stream().mapToDouble(AiClient.Menu::score).max().orElse(1);
        Map<String, Place> byId = new LinkedHashMap<>();
        found.forEach((menu, docs) -> docs.stream().filter(d -> fits(menu, d)).forEach(d -> {
            int distance = d.distance() == null || d.distance().isEmpty() ? radius : Integer.parseInt(d.distance());
            double near = 1 - Math.min(distance, radius) / (double) radius;
            int match = (int) Math.round(100 * (0.8 * menu.score() / top + 0.2 * near));
            String category = d.category_name().replace("음식점 > ", "").replace(" > ", " · ");
            String address = d.road_address_name().isEmpty() ? d.address_name() : d.road_address_name();
            Place p = new Place(d.id(), d.place_name(), category, address, d.phone(), distance,
                    Double.parseDouble(d.y()), Double.parseDouble(d.x()), d.place_url(), null,
                    menu.name(), menu.labels(), match);
            byId.merge(p.id(), p, (a, b) -> a.match() >= b.match() ? a : b);
        }));
        return byId.values().stream()
                .sorted(Comparator.comparingInt(Place::match).reversed().thenComparingInt(Place::distance))
                .limit(limit)
                .toList();
    }
}
