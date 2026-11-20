package com.sinoll.home.data;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /users/suggest：按用户名或昵称模糊查活跃用户，服务端排除当前用户、最多返回 8 条。
 * 省略 q 时服务端给候选用户，所以空关键词也照样请求。
 */
public final class UserApi {

    public interface SuggestCallback {
        void onUsers(List<UserRef> users);
    }

    public static void suggest(Context context, String query, SuggestCallback callback) {
        String path = "/users/suggest";
        if (query != null && !query.trim().isEmpty()) {
            path += "?q=" + PostRepository.encode(query.trim());
        }
        ApiClient.get(path, AuthSession.get(context).token(), response -> {
            List<UserRef> users = new ArrayList<>();
            JSONArray items = response.success ? response.data.optJSONArray("users") : null;
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    users.add(UserRef.fromJson(items.optJSONObject(i)));
                }
            }
            callback.onUsers(users);
        });
    }

    private UserApi() {
    }
}
