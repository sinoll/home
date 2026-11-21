package com.sinoll.home.data;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * /comments 写接口。顶级评论不带 parent_id，正文与站点口径一样最多 500 字。
 */
public final class CommentApi {

    public static void send(Context context, int postId, int parentId, String content,
                            ApiClient.Callback callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("post_id", postId);
            payload.put("content", content);
            if (parentId > 0) {
                payload.put("parent_id", parentId);
            }
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        ApiClient.post("/comments", payload, AuthSession.get(context).token(), callback);
    }

    /**
     * 读取某条内容下的顶级评论，按时间正序分页；每条内嵌最多 3 条最早回复。
     * 公开的已发布内容游客也能读，令牌为空时不带 Authorization 头即可。
     */
    public static void list(Context context, int postId, ApiClient.Callback callback) {
        ApiClient.get("/comments?post_id=" + postId + "&page=1&per_page=50",
                AuthSession.get(context).token(), callback);
    }

    private CommentApi() {
    }
}
