package com.muckzi;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiClient {

    public record Tag(String label, String group, boolean avoid) {}

    public record Menu(String keyword, String name, String category, String cuisine, double score, String labels) {}

    record Tags(List<Tag> tags) {}

    public record Menus(String status, String reason, List<Menu> menus) {}

    private final RestClient http;

    public AiClient(@Value("${ai.url}") String url) {
        this.http = RestClient.create(url);
    }

    public List<Tag> tags(String text) {
        return http.post().uri("/parse").body(Map.of("text", text)).retrieve().body(Tags.class).tags();
    }

    public Menus menus(String text, int limit) {
        return http.post().uri("/recommend").body(Map.of("text", text, "limit", limit)).retrieve().body(Menus.class);
    }
}
