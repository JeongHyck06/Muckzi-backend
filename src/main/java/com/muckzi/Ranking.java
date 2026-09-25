package com.muckzi;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

public final class Ranking {

    public record Dish(String name, Integer price) {}

    public record Place(String id, String name, String category, String address, String phone, int distance,
                        double lat, double lng, String url, String image, String menu, String labels, int match,
                        Dish dish, List<Dish> dishes, String hours, String today, Boolean open) {

        Place withImage(String image) {
            return new Place(id, name, category, address, phone, distance, lat, lng, url, image, menu, labels, match,
                    dish, dishes, hours, today, open);
        }
    }

    public record Candidate(KakaoClient.Doc doc, AiClient.Menu menu, int distance, int match) {}

    /** 메뉴를 확인한 가게가 먼저, 그다음 일치도와 거리 순 */
    static final Comparator<Place> ORDER = Comparator.comparing((Place p) -> p.dish() == null)
            .thenComparing(Comparator.comparingInt(Place::match).reversed())
            .thenComparingInt(Place::distance);

    private static final Set<String> CUISINES = Set.of("한식", "중식", "일식", "양식");

    private Ranking() {}

    /** 키워드 검색이 느슨해 국밥에 중식당이 걸리므로 메뉴와 다른 나라 음식점은 뺀다 */
    static boolean fits(AiClient.Menu menu, KakaoClient.Doc d) {
        String[] parts = d.category_name().split(" > ");
        String top = parts.length > 1 ? parts[1] : "";
        return !CUISINES.contains(menu.cuisine()) || !CUISINES.contains(top) || top.equals(menu.cuisine());
    }

    /** 일치도 = 메뉴 점수 80 + 거리 20, 같은 가게는 가장 높은 메뉴 하나만 남긴다 */
    public static List<Candidate> rank(Map<AiClient.Menu, List<KakaoClient.Doc>> found, int radius, int limit) {
        double top = found.keySet().stream().mapToDouble(AiClient.Menu::score).max().orElse(1);
        Map<String, Candidate> byId = new LinkedHashMap<>();
        found.forEach((menu, docs) -> docs.stream().filter(d -> fits(menu, d)).forEach(d -> {
            int distance = d.distance() == null || d.distance().isEmpty() ? radius : Integer.parseInt(d.distance());
            double near = 1 - Math.min(distance, radius) / (double) radius;
            int match = (int) Math.round(100 * (0.8 * menu.score() / top + 0.2 * near));
            byId.merge(d.id(), new Candidate(d, menu, distance, match), (a, b) -> a.match() >= b.match() ? a : b);
        }));
        return byId.values().stream()
                .sorted(Comparator.comparingInt(Candidate::match).reversed().thenComparingInt(Candidate::distance))
                .limit(limit)
                .toList();
    }

    /** "소고기 국밥"은 소고기국밥과 국밥 둘 다 인정, 라면사리 같은 곁들임은 제외 */
    static boolean serves(String keyword, String item) {
        String name = item.replace(" ", "");
        if (name.contains("사리") || name.contains("추가")) {
            return false;
        }
        String[] words = keyword.split(" ");
        String last = words[words.length - 1];
        return name.contains(keyword.replace(" ", "")) || (last.length() >= 2 && name.contains(last));
    }

    /** 가게 메뉴가 있는데 추천 메뉴가 없으면 null, 메뉴 정보가 없으면 확인하지 못한 채로 남긴다 */
    static Place place(Candidate c, KakaoClient.Panel panel) {
        KakaoClient.Doc d = c.doc();
        List<Dish> items = panel == null ? List.of() : panel.dishes();
        Dish dish = items.stream().filter(i -> serves(c.menu().keyword(), i.name())).findFirst().orElse(null);
        if (!items.isEmpty() && dish == null) {
            return null;
        }
        List<Dish> dishes = Stream.concat(Stream.ofNullable(dish), items.stream().filter(i -> i != dish))
                .limit(4).toList();
        String category = d.category_name().replace("음식점 > ", "").replace(" > ", " · ");
        String address = d.road_address_name().isEmpty() ? d.address_name() : d.road_address_name();
        return new Place(d.id(), d.place_name(), category, address, d.phone(), c.distance(),
                Double.parseDouble(d.y()), Double.parseDouble(d.x()), d.place_url(),
                panel == null ? null : panel.photo(), c.menu().name(), c.menu().labels(), c.match(),
                dish, dishes, panel == null ? null : panel.hours(), panel == null ? null : panel.today(),
                panel == null ? null : panel.open());
    }

    public static List<Place> verify(List<Candidate> candidates, Function<String, KakaoClient.Panel> panels, int limit) {
        return candidates.parallelStream()
                .map(c -> place(c, panels.apply(c.doc().id())))
                .filter(Objects::nonNull)
                .sorted(ORDER)
                .limit(limit)
                .toList();
    }
}
