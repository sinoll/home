package com.sinoll.home.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 四类内容的共用列表模型。列表接口只给摘要级字段，正文与简介要详情接口才有。
 */
public final class Post {

    public static final String TYPE_BLOG = "blog";
    public static final String TYPE_MOMENT = "moment";
    public static final String TYPE_IMAGES = "images";
    public static final String TYPE_VIDEO = "short_video";

    /** 站点给无封面内容挂的占位图，客户端按「没有图」处理。 */
    private static final String DEFAULT_COVER = "/images/default-cover.png";

    public final String type;
    public final int id;
    /** 前台详情页地址，供分享使用；短视频的网页路径是 video，和接口名不同。 */
    public final String webUrl;
    /** status 0 草稿 / 1 已发布；visibility 0 私密 / 1 仅注册用户 / 2 公开。 */
    public final int status;
    public final int visibility;
    public final String title;
    /** 动态取 text、博客取 summary、图集取 caption；短视频列表不含简介，为 null。 */
    public final String body;
    public final String coverUrl;
    public final String videoUrl;
    public final int durationSeconds;
    public final boolean vertical;
    public final int imageCount;
    public final String authorName;
    public final String authorAvatarUrl;
    public final List<String> tags;
    /** 分类只有博客有。 */
    public final List<String> categories;
    public final int likeCount;
    /** 接口统计全部评论节点（含回复）。发完评论就地加一，不等下一次列表。 */
    public int commentCount;
    public final int views;
    public final long createdAtMillis;

    private Post(JSONObject item) {
        type = item.optString("type");
        id = item.optInt("id");
        webUrl = ApiClient.SITE_URL + "/"
                + (TYPE_VIDEO.equals(type) ? "video" : type) + "/view?id=" + id;
        title = item.optString("title");
        status = item.optInt("status");
        visibility = item.optInt("visibility");
        body = firstPresentText(item, "text", "summary", "caption");
        likeCount = item.optInt("like_count");
        commentCount = item.optInt("comment_count");
        views = item.optInt("views");
        durationSeconds = item.optInt("duration_seconds");
        vertical = item.optInt("video_type", 2) != 1;
        imageCount = item.optInt("image_count");
        createdAtMillis = parseTime(item.optString("created_at"));
        tags = stringList(item.optJSONArray("tags"));
        categories = nameList(item.optJSONArray("categories"));

        JSONObject author = item.optJSONObject("author");
        if (author == null) {
            author = new JSONObject();
        }
        String nickname = author.optString("nickname");
        authorName = nickname.isEmpty() ? author.optString("username") : nickname;
        authorAvatarUrl = ApiClient.absoluteUrl(realPath(author.optString("avatar_url")));
        coverUrl = ApiClient.absoluteUrl(resolveCover(item));
        videoUrl = ApiClient.absoluteUrl(realPath(item.optString("video_url")));
    }

    public static Post fromJson(JSONObject item) {
        return new Post(item);
    }

    public boolean isVideo() {
        return TYPE_VIDEO.equals(type);
    }

    public boolean isImages() {
        return TYPE_IMAGES.equals(type);
    }

    /** 草稿和非公开内容分享出去只会得到 404，所以不给分享入口。 */
    public boolean isShareable() {
        return status == 1 && visibility == 2;
    }

    private String resolveCover(JSONObject item) {
        String thumb = realPath(item.optString("thumb_url"));
        if (thumb != null) {
            return thumb;
        }
        String cover = realPath(item.optString("cover_url"));
        if (cover != null) {
            return cover;
        }
        JSONArray images = item.optJSONArray("images");
        if (images != null && images.length() > 0) {
            JSONObject first = images.optJSONObject(0);
            if (first != null) {
                return realPath(first.optString("thumbnail_url"));
            }
        }
        return null;
    }

    /** 空串与默认封面都算「没有图」，返回 null 让界面走渐变兜底。 */
    private static String realPath(String path) {
        if (path == null || path.isEmpty() || path.endsWith(DEFAULT_COVER)) {
            return null;
        }
        return path;
    }

    private static String firstPresentText(JSONObject item, String... keys) {
        for (String key : keys) {
            if (item.has(key) && !item.isNull(key)) {
                String value = item.optString(key);
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static List<String> stringList(JSONArray array) {
        if (array == null || array.length() == 0) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i);
            if (!value.isEmpty()) {
                values.add(value.startsWith("#") ? value.substring(1) : value);
            }
        }
        return Collections.unmodifiableList(values);
    }

    private static List<String> nameList(JSONArray array) {
        if (array == null || array.length() == 0) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry != null) {
                String name = entry.optString("name");
                if (!name.isEmpty()) {
                    values.add(name);
                }
            }
        }
        return Collections.unmodifiableList(values);
    }

    static long parseTime(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0L;
        }
        try {
            return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0L;
        }
    }
}
