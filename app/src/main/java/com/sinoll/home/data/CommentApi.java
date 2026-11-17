package com.sinoll.home.data;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * /comments 写接口。顶级评论不带 parent_id，正文与站点口径一样最多 500 字。
 */
public final class CommentApi {

    public static void send(Context context, int postId, String content,
                            ApiClient.Callback callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("post_id", postId);
            payload.put("content", content);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        ApiClient.post("/comments", payload, AuthSession.get(context).token(), callback);
    }

    private CommentApi() {
    }
}
