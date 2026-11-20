package com.sinoll.home.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 详情接口比列表多给的东西：动态与博客的完整正文、图集的图片清单、短视频的简介。
 */
public final class PostDetail {

    public final Post post;
    /** 动态 content / 博客 content / 短视频 description；图集没有这一段。 */
    public final String html;
    /** 图集说明：首行是每张图的标题，其余行是替代文本。 */
    public final String caption;
    public final List<Image> images;

    private PostDetail(JSONObject item) {
        post = Post.fromJson(item);
        html = firstPresent(item, "content", "description");
        caption = item.optString("caption");
        images = readImages(item.optJSONArray("images"));
    }

    public static PostDetail fromJson(JSONObject item) {
        return new PostDetail(item);
    }

    public boolean isImages() {
        return post.isImages();
    }

    private static String firstPresent(JSONObject item, String... keys) {
        for (String key : keys) {
            String value = item.optString(key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static List<Image> readImages(JSONArray items) {
        List<Image> images = new ArrayList<>();
        if (items == null) {
            return images;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                images.add(Image.fromJson(item));
            }
        }
        return images;
    }

    /** 图集里的单张图。接口给的 file_url 本身就是根相对路径，编辑时原样回填进 file_paths。 */
    public static final class Image {

        public final int id;
        public final String title;
        public final String altText;
        public final String filePath;
        public final String fileUrl;
        public final String thumbnailUrl;

        private Image(JSONObject item) {
            id = item.optInt("id");
            title = item.optString("title");
            altText = item.optString("alt_text");
            filePath = item.optString("file_url");
            fileUrl = ApiClient.absoluteUrl(filePath);
            thumbnailUrl = ApiClient.absoluteUrl(item.optString("thumbnail_url"));
        }

        public static Image fromJson(JSONObject item) {
            return new Image(item);
        }
    }
}
