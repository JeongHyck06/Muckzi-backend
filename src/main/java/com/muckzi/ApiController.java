package com.muckzi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    record TextIn(String text) {}

    record Result(List<Ranking.Place> places, String reason) {}

    private final AiClient ai;
    private final KakaoClient kakao;

    public ApiController(AiClient ai, KakaoClient kakao) {
        this.ai = ai;
        this.kakao = kakao;
    }

    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Map.of("ok", true);
    }

    @PostMapping("/tags")
    public List<AiClient.Tag> tags(@RequestBody TextIn in) {
        return ai.tags(clip(in.text()));
    }

    @PostMapping("/feedback")
    public Map<String, Boolean> feedback(@RequestBody AiClient.Feedback body) {
        ai.feedback(body);
        return Map.of("ok", true);
    }

    @GetMapping("/region")
    public Map<String, String> region(@RequestParam double lat, @RequestParam double lng) {
        return Map.of("name", kakao.region(lat, lng));
    }

    @GetMapping("/recommend")
    public Result recommend(@RequestParam String q, @RequestParam double lat, @RequestParam double lng,
                            @RequestParam(defaultValue = "500") int radius) {
        int r = Math.max(100, Math.min(radius, 3000));
        AiClient.Menus menus = ai.menus(clip(q), 5);
        Map<AiClient.Menu, List<KakaoClient.Doc>> found = new LinkedHashMap<>();
        menus.menus().parallelStream()
                .map(m -> Map.entry(m, kakao.restaurants(m.keyword(), lat, lng, r)))
                .toList()
                .forEach(e -> found.put(e.getKey(), e.getValue()));
        List<Ranking.Place> places = Ranking.verify(Ranking.rank(found, r, 30), kakao::panel, 12).parallelStream()
                .map(p -> p.image() != null ? p : p.withImage(kakao.image(p.name() + " " + district(p.address()))))
                .toList();
        return new Result(places, menus.reason());
    }

    private static String district(String address) {
        String[] parts = address.split(" ");
        return parts.length > 1 ? parts[1] : "";
    }

    private static String clip(String text) {
        String t = text == null ? "" : text.strip();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}
