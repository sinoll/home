package com.sinoll.home.ui;

import android.content.Context;

import com.sinoll.home.R;
import com.sinoll.home.data.ApiClient;
import com.sinoll.home.data.Post;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 列表卡片上的时间、计数与兜底图形。 */
public final class PostFormat {

    private static final int[] COVER_BG = {
            R.drawable.bg_cover_a, R.drawable.bg_cover_b, R.drawable.bg_cover_c,
            R.drawable.bg_cover_d, R.drawable.bg_cover_e};
    private static final int[] AVATAR_BG = {
            R.drawable.bg_avatar_a, R.drawable.bg_avatar_b,
            R.drawable.bg_avatar_c, R.drawable.bg_avatar_d};

    public static String time(Context context, long millis) {
        if (millis <= 0) {
            return "";
        }
        long elapsed = System.currentTimeMillis() - millis;
        if (elapsed < 0) {
            elapsed = 0;
        }
        if (elapsed < 60_000L) {
            return context.getString(R.string.time_just_now);
        }
        if (elapsed < 3_600_000L) {
            return context.getString(R.string.time_minutes, elapsed / 60_000L);
        }
        if (elapsed < 86_400_000L) {
            return context.getString(R.string.time_hours, elapsed / 3_600_000L);
        }
        if (elapsed < 7 * 86_400_000L) {
            return context.getString(R.string.time_days, elapsed / 86_400_000L);
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        int year = calendar.get(Calendar.YEAR);
        if (year == Calendar.getInstance().get(Calendar.YEAR)) {
            return context.getString(R.string.time_month_day,
                    calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
        }
        return context.getString(R.string.time_year_month_day, year,
                calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    /** 一万以上折成「x.y万」，整数万不留小数点。 */
    public static String count(Context context, int value) {
        if (value < 10_000) {
            return String.valueOf(value);
        }
        double wan = value / 10_000.0;
        String text = String.format(Locale.CHINA, wan < 100 ? "%.1f" : "%.0f", wan);
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return context.getString(R.string.count_wan, text);
    }

    public static String duration(int seconds) {
        if (seconds <= 0) {
            return "";
        }
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        if (minutes < 60) {
            return String.format(Locale.CHINA, "%02d:%02d", minutes, remainder);
        }
        return String.format(Locale.CHINA, "%d:%02d:%02d",
                minutes / 60, minutes % 60, remainder);
    }

    /** 标签在界面上统一带 # 前缀，接口里有的已经带了。 */
    public static List<String> withHash(List<String> tags) {
        List<String> values = new ArrayList<>(tags.size());
        for (String tag : tags) {
            values.add(tag.startsWith("#") ? tag : "#" + tag);
        }
        return values;
    }

    public static String categories(List<String> categories) {
        return String.join(" · ", categories);
    }

    /** 列表拉取失败时的一句话文案：优先服务端原文，其次按传输故障分类。 */
    public static String message(Context context, ApiClient.Response response) {
        if (!response.message.isEmpty()) {
            return response.message;
        }
        switch (response.failure) {
            case TIMEOUT:
                return context.getString(R.string.error_timeout);
            case NETWORK:
                return context.getString(R.string.error_network);
            default:
                break;
        }
        if (response.code == 429) {
            return context.getString(R.string.error_rate_limited);
        }
        return context.getString(R.string.error_server, response.code);
    }

    /** 搜索把四类内容混在一起，靠这个标签说明每条是什么。 */
    public static String typeLabel(Context context, Post post) {
        switch (post.type) {
            case Post.TYPE_BLOG:
                return context.getString(R.string.type_blog);
            case Post.TYPE_MOMENT:
                return context.getString(R.string.type_moment);
            case Post.TYPE_IMAGES:
                return context.getString(R.string.type_images);
            case Post.TYPE_VIDEO:
                return context.getString(R.string.type_video);
            default:
                return "";
        }
    }

    public static String initial(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    public static int coverFallback(int id) {
        return COVER_BG[Math.floorMod(id, COVER_BG.length)];
    }

    public static int avatarFallback(int id) {
        return AVATAR_BG[Math.floorMod(id, AVATAR_BG.length)];
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private PostFormat() {
    }
}
