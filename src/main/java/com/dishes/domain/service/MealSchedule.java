package com.dishes.domain.service;

import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.regex.Pattern;

@Schema(name = "MealSchedule")
public record MealSchedule(String lunchStart, String dinnerStart) {
    public static final MealSchedule DEFAULT = new MealSchedule("10:00", "15:00");
    private static final Pattern TIME = Pattern.compile("(?:[01][0-9]|2[0-3]):[0-5][0-9]");

    public static MealSchedule normalize(MealSchedule value) {
        if (value == null) return DEFAULT;
        var lunch = minute(value.lunchStart()); var dinner = minute(value.dinnerStart());
        if (lunch == 0 || lunch >= dinner) {
            throw invalid("餐次时间需要满足 00:00 < 午餐开始 < 晚餐开始 < 24:00");
        }
        return value;
    }

    public String periodAt(LocalTime time) {
        var current = time.getHour() * 60 + time.getMinute();
        if (current < minute(lunchStart)) return "breakfast";
        return current < minute(dinnerStart) ? "lunch" : "dinner";
    }

    private static int minute(String value) {
        if (value == null || !TIME.matcher(value).matches()) throw invalid("餐次时间须使用 HH:mm 格式");
        return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3, 5));
    }
    private static BusinessException invalid(String message) {
        return new BusinessException(BusinessErrorCode.INVALID_REQUEST, message);
    }
}
