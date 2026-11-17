package com.sinoll.home.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 站内消息接口：未读计数、分页列表与标记已读，全部要带令牌。 */
public final class MessageApi {

    public interface UnreadCallback {
        /** 拉取失败按 0 处理：一条后台提醒不值得弹错误。 */
        void onUnread(int count);
    }

    public interface PageCallback {
        void onPage(List<Message> messages, int page, int totalPages,
                    ApiClient.Response response);
    }

    public static void unreadCount(Context context, UnreadCallback callback) {
        ApiClient.get("/messages/unread-count", AuthSession.get(context).token(), response ->
                callback.onUnread(response.success ? response.data.optInt("unread_count") : 0));
    }

    public static void load(Context context, int page, int perPage, PageCallback callback) {
        String myUserId = AuthSession.get(context).userId();
        ApiClient.get("/messages?page=" + page + "&per_page=" + perPage,
                AuthSession.get(context).token(), response -> {
                    if (!response.success) {
                        callback.onPage(new ArrayList<>(), page, 0, response);
                        return;
                    }
                    JSONObject data = response.data;
                    callback.onPage(parse(data.optJSONArray("items"), myUserId),
                            data.optInt("page", page),
                            data.optInt("total_pages"),
                            response);
                });
    }

    /** 接口是幂等的，重复点同一条也只改一次状态。 */
    public static void markRead(Context context, int id, ApiClient.Callback callback) {
        ApiClient.patch("/messages/" + id + "/read", new JSONObject(),
                AuthSession.get(context).token(), callback);
    }

    private static List<Message> parse(JSONArray items, String myUserId) {
        List<Message> messages = new ArrayList<>();
        if (items == null) {
            return messages;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                messages.add(Message.fromJson(item, myUserId));
            }
        }
        return messages;
    }

    private MessageApi() {
    }
}
