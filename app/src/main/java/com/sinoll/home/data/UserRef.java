package com.sinoll.home.data;

import org.json.JSONObject;

/**
 * @提及 与私信要的用户摘要。/users/suggest 不返回邮箱等私密字段。
 */
public final class UserRef {

    public final String id;
    public final String username;
    public final String nickname;
    /** 服务端算好的展示名：有昵称用昵称，否则用用户名。 */
    public final String name;
    public final String avatarUrl;

    private UserRef(JSONObject item) {
        id = String.valueOf(item.opt("id"));
        username = item.optString("username");
        nickname = item.optString("nickname");
        name = item.optString("name").isEmpty() ? username : item.optString("name");
        avatarUrl = ApiClient.absoluteUrl(item.optString("avatar_url"));
    }

    public static UserRef fromJson(JSONObject item) {
        return new UserRef(item);
    }

    /** 写进正文的是用户名：昵称可能带空格，@昵称 在服务端的字面匹配里会歧义。 */
    public String mentionToken() {
        return username;
    }
}
