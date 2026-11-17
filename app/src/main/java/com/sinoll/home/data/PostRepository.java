package com.sinoll.home.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** 内容列表的分页读取。登录时带上令牌，才能看到「注册用户可见」那一档。 */
public final class PostRepository {

    public interface PageCallback {
        /**
         * @param posts      当页内容，失败时为空列表
         * @param page       服务端回显的页码
         * @param totalPages 总页数，用于判断是否到底
         * @param response   原始响应，界面据此区分「空」与「失败」
         */
        void onPage(List<Post> posts, int page, int totalPages, ApiClient.Response response);
    }

    public static void load(Context context, String type, int page, int perPage,
                           PageCallback callback) {
        request("/" + type + "?page=" + page + "&per_page=" + perPage, context, page, callback);
    }

    /** 全站搜索：type 传空表示不限类型，接口只搜已发布内容。 */
    public static void search(Context context, String query, String type, int page, int perPage,
                             PageCallback callback) {
        StringBuilder path = new StringBuilder("/search?q=").append(encode(query))
                .append("&page=").append(page)
                .append("&per_page=").append(perPage);
        if (type != null && !type.isEmpty()) {
            path.append("&type=").append(type);
        }
        request(path.toString(), context, page, callback);
    }

    private static void request(String path, Context context, int page, PageCallback callback) {
        ApiClient.get(path, AuthSession.get(context).token(), response -> {
            if (!response.success) {
                callback.onPage(new ArrayList<>(), page, 0, response);
                return;
            }
            JSONObject data = response.data;
            callback.onPage(parse(data.optJSONArray("items")),
                    data.optInt("page", page),
                    data.optInt("total_pages"),
                    response);
        });
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private static List<Post> parse(JSONArray items) {
        List<Post> posts = new ArrayList<>();
        if (items == null) {
            return posts;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                posts.add(Post.fromJson(item));
            }
        }
        return posts;
    }

    private PostRepository() {
    }
}
