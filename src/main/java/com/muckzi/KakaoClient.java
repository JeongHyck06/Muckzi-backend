package com.muckzi;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoClient {

    public record Doc(String id, String place_name, String category_name, String phone, String address_name,
                      String road_address_name, String x, String y, String place_url, String distance) {}

    record Docs(List<Doc> documents) {}

    record Image(String image_url, String thumbnail_url, int width, int height) {}

    record Images(List<Image> documents) {}

    record Region(String region_type, String region_3depth_name) {}

    record Regions(List<Region> documents) {}

    /** 카카오맵 가게 상세에서 필요한 메뉴, 영업시간, 사진만 추린 값 */
    public record Panel(List<Ranking.Dish> dishes, String photo, String hours, String today, Boolean open) {}

    record Item(String name, Integer price) {}

    record Items(List<Item> items) {}

    record MenuBox(Items menus) {}

    record Headline(String code, String display_text, String display_text_info) {}

    record OnDays(String start_end_time_desc) {}

    record Day(boolean is_highlight, OnDays on_days) {}

    record Period(List<Day> days) {}

    record Week(List<Period> week_periods) {}

    record OpenHours(Headline headline, Week week_from_today) {}

    record Photo(String url) {}

    record Photos(List<Photo> photos) {}

    record RawPanel(MenuBox menu, OpenHours open_hours, Photos photos) {}

    private final RestClient http;
    private final RestClient place;

    public KakaoClient(@Value("${kakao.rest-key}") String key) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder()
                .requestFactory(timeouts)
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + key)
                .build();
        this.place = RestClient.builder()
                .requestFactory(timeouts)
                .baseUrl("https://place-api.map.kakao.com")
                .defaultHeader("pf", "web")
                .defaultHeader("Referer", "https://place.map.kakao.com/")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build();
    }

    public List<Doc> restaurants(String keyword, double lat, double lng, int radius) {
        return http.get().uri(u -> u.path("/v2/local/search/keyword.json")
                        .queryParam("query", keyword).queryParam("x", lng).queryParam("y", lat)
                        .queryParam("radius", radius).queryParam("category_group_code", "FD6")
                        .queryParam("size", 15).build())
                .retrieve().body(Docs.class).documents();
    }

    public List<Doc> places(String query, double lat, double lng) {
        return http.get().uri(u -> u.path("/v2/local/search/keyword.json")
                        .queryParam("query", query).queryParam("x", lng).queryParam("y", lat)
                        .queryParam("size", 10).build())
                .retrieve().body(Docs.class).documents();
    }

    /** 사진은 없어도 추천은 보여줘야 하므로 실패하면 null, 블로그 이모티콘 같은 작은 이미지는 건너뛴다 */
    public String image(String query) {
        try {
            return http.get().uri(u -> u.path("/v2/search/image")
                            .queryParam("query", query).queryParam("size", 10).build())
                    .retrieve().body(Images.class).documents().stream()
                    .filter(img -> img.width() >= 300 && img.height() >= 200 && !img.image_url().contains("storep-phinf"))
                    .map(img -> img.image_url().startsWith("https://") ? img.image_url() : img.thumbnail_url())
                    .findFirst().orElse(null);
        } catch (RestClientException e) {
            return null;
        }
    }

    /**
     * 공개 API에 메뉴가 없어 카카오맵 웹이 쓰는 내부 API를 읽는다
     * ponytail: 비공개 API라 구조가 바뀌거나 막히면 null을 돌려 메뉴 확인 없이 동작, 공식 메뉴 API가 생기면 교체
     */
    public Panel panel(String id) {
        try {
            RawPanel raw = place.get().uri("/places/panel3/{id}", id).retrieve().body(RawPanel.class);
            if (raw == null) {
                return null;
            }
            List<Ranking.Dish> dishes = raw.menu() == null || raw.menu().menus() == null || raw.menu().menus().items() == null
                    ? List.of()
                    : raw.menu().menus().items().stream().filter(i -> i.name() != null)
                            .map(i -> new Ranking.Dish(i.name(), i.price() == null || i.price() <= 0 ? null : i.price())).toList();
            String photo = raw.photos() == null || raw.photos().photos() == null ? null
                    : raw.photos().photos().stream().map(Photo::url)
                            .filter(u -> u != null && u.startsWith("https://")).findFirst().orElse(null);
            Headline head = raw.open_hours() == null ? null : raw.open_hours().headline();
            String hours = head == null || head.display_text() == null ? null
                    : head.display_text_info() == null ? head.display_text()
                    : head.display_text() + " · " + head.display_text_info();
            String today = raw.open_hours() == null || raw.open_hours().week_from_today() == null
                    || raw.open_hours().week_from_today().week_periods() == null ? null
                    : raw.open_hours().week_from_today().week_periods().stream()
                            .flatMap(p -> p.days() == null ? java.util.stream.Stream.empty() : p.days().stream())
                            .filter(d -> d.is_highlight() && d.on_days() != null)
                            .map(d -> d.on_days().start_end_time_desc()).findFirst().orElse(null);
            return new Panel(dishes, photo, hours, today, head == null ? null : "OPEN".equals(head.code()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String region(double lat, double lng) {
        return http.get().uri(u -> u.path("/v2/local/geo/coord2regioncode.json")
                        .queryParam("x", lng).queryParam("y", lat).build())
                .retrieve().body(Regions.class).documents().stream()
                .filter(r -> "H".equals(r.region_type()))
                .map(Region::region_3depth_name)
                .findFirst().orElse("");
    }
}
