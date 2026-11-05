package com.dishes.domain.service;

import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.domain.DishesStore;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PersistentDishesDomainService {

    public String normalizeSlug(String slug) {
        if (slug == null) return "";
        var s = slug.trim().toLowerCase(Locale.ROOT);
        s = s.replace('_', '-').replace(' ', '-');
        var cleaned = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-';
            if (!ok) continue;
            if (ch == '-' && prev == '-') continue;
            cleaned.append(ch);
            prev = ch;
        }
        int start = 0;
        int end = cleaned.length();
        while (start < end && cleaned.charAt(start) == '-') start++;
        while (end > start && cleaned.charAt(end - 1) == '-') end--;
        return cleaned.substring(start, end);
    }

    public void ensureCategorySlugUnique(String normalizedSlug, Long excludeId, List<DishesStore.Category> categories) {
        if (normalizedSlug == null || normalizedSlug.isBlank()) {
            throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "slug 不能为空");
        }
        for (var c : categories) {
            if (excludeId != null && c.id() == excludeId) continue;
            var other = normalizeSlug(c.slug());
            if (normalizedSlug.equals(other)) {
                throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "slug 已存在，请换一个（当前冲突：" + c.slug() + "）");
            }
        }
    }

    public void validateDishInput(String name, String imageUrl, int recommendationLevel) {
        var n = name == null ? "" : name.trim();
        if (n.isBlank()) throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "菜品名称不能为空");
        if (n.length() > 200) throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "菜品名称过长（最多 200 字符）");

        if (recommendationLevel < 1 || recommendationLevel > 5) {
            throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "推荐等级需在 1-5");
        }
        var img = trimToNull(imageUrl);
        if (img != null) {
            var lower = img.toLowerCase(Locale.ROOT);
            if (lower.startsWith("data:image/")) {
                if (img.length() > 750_000) {
                    throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "内联图片过大（请缩短或使用附件链接）");
                }
                return;
            }
            if (!(lower.startsWith("http://") || lower.startsWith("https://") || img.startsWith("/"))) {
                throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "图片 URL 需是 http(s) 链接、站内相对路径（以 / 开头）或 data:image/* 内联图");
            }
        }
    }

    public String trimToNull(String s) {
        if (s == null) return null;
        var t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

