package com.muckzi;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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

    private final RestClient http;

    public KakaoClient(@Value("${kakao.rest-key}") String key) {
        this.http = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + key)
                .build();
    }

    public List<Doc> restaurants(String keyword, double lat, double lng, int radius) {
        return http.get().uri(u -> u.path("/v2/local/search/keyword.json")
                        .queryParam("query", keyword).queryParam("x", lng).queryParam("y", lat)
                        .queryParam("radius", radius).queryParam("category_group_code", "FD6")
                        .queryParam("size", 5).build())
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

    public String region(double lat, double lng) {
        return http.get().uri(u -> u.path("/v2/local/geo/coord2regioncode.json")
                        .queryParam("x", lng).queryParam("y", lat).build())
                .retrieve().body(Regions.class).documents().stream()
                .filter(r -> "H".equals(r.region_type()))
                .map(Region::region_3depth_name)
                .findFirst().orElse("");
    }
}
