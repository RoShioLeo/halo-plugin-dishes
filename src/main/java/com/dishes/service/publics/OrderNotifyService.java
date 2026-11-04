package com.dishes.service.publics;

import com.dishes.domain.DishesStore;
import com.dishes.extension.DishesSettings;
import com.dishes.service.DishesSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

@Service
public class OrderNotifyService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifyService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter MONTH_DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final DishesSettingsService settingsService;

    public OrderNotifyService(DishesSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void tryPushNotify(
        DishesSettings.Spec settings,
        LocalDate orderDate,
        String mealPeriodCode,
        List<DishesStore.OrderItem> items,
        ServerHttpRequest request
    ) {
        if (settings == null) return;
        if (!(settings.getNotifyEnabled() != null && settings.getNotifyEnabled())) return;
        var isReservation = isReservationOrder(orderDate);
        var nowEnabled = settings.getNotifyOrderNowEnabled() == null || settings.getNotifyOrderNowEnabled();
        var reservationEnabled = settings.getNotifyOrderReservationEnabled() != null && settings.getNotifyOrderReservationEnabled();
        if (!isReservation && !nowEnabled) return;
        if (isReservation && !reservationEnabled) return;
        var webhookUrl = selectWebhookUrl(settings);
        if (webhookUrl.isBlank()) return;

        var periodName = periodName(mealPeriodCode);
        var itemCount = items == null ? 0 : items.size();
        var title = buildNotifyTitle(isReservation, orderDate, periodName);
        var description = "%s共%s道菜，点击查看菜品详情".formatted(periodName.isBlank() ? "本次" : periodName, itemCount);
        var pageUrl = resolvePublicPageUrl(settings, request);
        var picUrl = resolveLogoUrl(settings, request);

        var payload = Map.of(
            "msgtype", "news",
            "news", Map.of(
                "articles", List.of(Map.of("title", title, "description", description, "url", pageUrl, "picurl", picUrl))
            )
        );
        try {
            var req = HttpRequest.newBuilder(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
            HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString()).exceptionally(ex -> null);
        } catch (Exception ex) {
            log.warn("Failed to push notify message, webhook={}", webhookUrl, ex);
        }
    }

    private boolean isReservationOrder(LocalDate orderDate) {
        return orderDate.isAfter(LocalDate.now());
    }

    private String buildNotifyTitle(boolean isReservation, LocalDate orderDate, String periodName) {
        var period = periodName == null || periodName.isBlank() ? "用餐" : periodName;
        if (isReservation) return "%s 预约%s".formatted(orderDate.format(MONTH_DAY_FMT), period);
        return "今日%s已点".formatted(period);
    }

    private String selectWebhookUrl(DishesSettings.Spec settings) {
        var url = settings.getNotifyWebhookUrl() == null ? "" : settings.getNotifyWebhookUrl().trim();
        if (!url.isBlank()) return url;
        var channel = settings.getNotifyChannel() == null ? "" : settings.getNotifyChannel().trim();
        if (channel.startsWith("http://") || channel.startsWith("https://")) return channel;
        return "";
    }

    private String periodName(String code) {
        if ("breakfast".equals(code)) return "早餐";
        if ("lunch".equals(code)) return "午餐";
        if ("dinner".equals(code)) return "晚餐";
        return "";
    }

    private String resolvePublicPageUrl(DishesSettings.Spec settings, ServerHttpRequest request) {
        var origin = request.getURI().getScheme() + "://" + request.getURI().getAuthority();
        return origin + settingsService.normalizePublicPathForPage(settings.getPublicAccessUrl());
    }

    private String resolveLogoUrl(DishesSettings.Spec settings, ServerHttpRequest request) {
        var origin = request.getURI().getScheme() + "://" + request.getURI().getAuthority();
        var configured = settings == null ? "" : settingsService.normalizeLogoUrl(settings.getPublicLogoUrl());
        if (!configured.isBlank()) {
            return toAbsoluteUrl(origin, request.getURI().getScheme(), configured);
        }
        return origin + "/plugins/dishes/assets/dishes-frontend/logo.png";
    }

    private String toAbsoluteUrl(String origin, String scheme, String rawUrl) {
        var value = rawUrl == null ? "" : rawUrl.trim();
        if (value.isBlank()) return "";
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("//")) return scheme + ":" + value;
        if (value.startsWith("/")) return origin + value;
        return origin + "/" + value;
    }
}
