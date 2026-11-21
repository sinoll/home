package com.sinoll.home.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一条评论节点。顶级评论按时间正序分页返回，内嵌最多 3 条最早回复；
 * 正文被隐藏时服务端仍返回节点以保持回复树完整，此时 content 为 null、status 为 0。
 */
public final class Comment {

    public final int id;
    /** 被隐藏的评论为 null，界面按「已隐藏」显示。 */
    public final String content;
    public final boolean hidden;
    public final String authorName;
    public final String authorAvatarUrl;
    public final long createdAtMillis;
    /** 回复目标的昵称；顶级评论为 null。 */
    public final String replyToName;
    public final int replyCount;
    public final boolean hasMoreReplies;
    public final List<Comment> replies;

    private Comment(JSONObject item) {
        id = item.optInt("id");
        hidden = item.isNull("content") || item.optInt("status") == 0;
        content = hidden ? null : item.optString("content");
        JSONObject author = item.optJSONObject("author");
        authorName = userName(author);
        authorAvatarUrl = ApiClient.absoluteUrl(
                author == null || author.isNull("avatar_url") ? null : author.optString("avatar_url"));
        createdAtMillis = Post.parseTime(item.optString("created_at"));
        replyToName = userName(item.optJSONObject("reply_to"));
        replyCount = item.optInt("reply_count");
        hasMoreReplies = item.optBoolean("has_more_replies");
        replies = readReplies(item.optJSONArray("replies"));
    }

    /** 解析 GET /comments 分页响应里的 items。 */
    public static List<Comment> list(JSONObject data) {
        JSONArray items = data == null ? null : data.optJSONArray("items");
        if (items == null) {
            return Collections.emptyList();
        }
        List<Comment> comments = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                comments.add(new Comment(item));
            }
        }
        return comments;
    }

    private static List<Comment> readReplies(JSONArray items) {
        if (items == null || items.length() == 0) {
            return Collections.emptyList();
        }
        List<Comment> replies = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                replies.add(new Comment(item));
            }
        }
        return replies;
    }

    /** 用户摘要里优先昵称，退回用户名，再退回展示名；缺失返回空串。 */
    private static String userName(JSONObject user) {
        if (user == null) {
            return "";
        }
        String nickname = user.optString("nickname");
        if (!nickname.isEmpty()) {
            return nickname;
        }
        String username = user.optString("username");
        if (!username.isEmpty()) {
            return username;
        }
        return user.optString("name");
    }
}
