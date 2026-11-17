package com.sinoll.home.data;

import org.json.JSONObject;

/**
 * 站内消息：私信与 @提及共用一份结构，列表接口一次把发送人、收件人都带全。
 */
public final class Message {

    public static final String TYPE_PRIVATE = "private";
    public static final String TYPE_MENTION = "mention";

    public final int id;
    public final String type;
    public final String content;
    public final String senderName;
    public final String senderAvatarUrl;
    public final String recipientName;
    /** 提及消息的来源内容，私信没有。 */
    public final String sourceType;
    public final int sourceId;
    public final long createdAtMillis;
    /** 我发出去的那半边不参与未读提醒，界面上也要换个说法。 */
    public final boolean mine;
    /** 收件人点开才置已读，界面就地更新，不等下一次列表。 */
    public boolean read;

    private Message(JSONObject item, String myUserId) {
        id = item.optInt("id");
        type = item.optString("type");
        content = item.optString("content");
        sourceType = item.optString("source_type");
        sourceId = item.optInt("source_id");
        createdAtMillis = Post.parseTime(item.optString("created_at"));
        read = !isNullish(item.opt("read_at"));

        JSONObject sender = orEmpty(item.optJSONObject("sender"));
        JSONObject recipient = orEmpty(item.optJSONObject("recipient"));
        senderName = displayName(sender);
        recipientName = displayName(recipient);
        senderAvatarUrl = ApiClient.absoluteUrl(sender.optString("avatar_url"));
        mine = String.valueOf(optId(sender)).equals(myUserId);
    }

    public static Message fromJson(JSONObject item, String myUserId) {
        return new Message(item, myUserId);
    }

    public boolean isMention() {
        return TYPE_MENTION.equals(type);
    }

    /** 只有收件人那半边、且还没读过的才该提醒。 */
    public boolean isUnread() {
        return !mine && !read;
    }

    private static JSONObject orEmpty(JSONObject value) {
        return value == null ? new JSONObject() : value;
    }

    private static String displayName(JSONObject user) {
        String nickname = user.optString("nickname");
        if (!nickname.isEmpty()) {
            return nickname;
        }
        String name = user.optString("name");
        return name.isEmpty() ? user.optString("username") : name;
    }

    private static int optId(JSONObject user) {
        Object value = user.opt("id");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isNullish(Object value) {
        return value == null || value == JSONObject.NULL || String.valueOf(value).isEmpty();
    }
}
