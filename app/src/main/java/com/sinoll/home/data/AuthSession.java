package com.sinoll.home.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 登录态：令牌自包含、服务端不落库，所以本地只需存令牌与账户快照。
 */
public final class AuthSession {

    private static final String PREFS = "auth_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EXPIRES_AT = "expires_at_millis";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_AVATAR = "avatar_url";

    private static AuthSession instance;

    private final SharedPreferences prefs;

    public static AuthSession get(Context context) {
        if (instance == null) {
            instance = new AuthSession(context.getApplicationContext());
        }
        return instance;
    }

    private AuthSession(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isSignedIn() {
        String token = prefs.getString(KEY_TOKEN, "");
        if (TextUtils.isEmpty(token)) {
            return false;
        }
        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L);
        // 0 表示服务端没给过期时间，交给 401 兜底
        return expiresAt == 0L || expiresAt > System.currentTimeMillis();
    }

    public String token() {
        return prefs.getString(KEY_TOKEN, "");
    }

    /** 消息要分辨「我发出的」和「别人发给我的」，靠这个 ID 对得上。 */
    public String userId() {
        return prefs.getString(KEY_USER_ID, "");
    }

    public String username() {
        return prefs.getString(KEY_USERNAME, "");
    }

    /** 优先昵称，其次用户名。 */
    public String displayName() {
        String nickname = prefs.getString(KEY_NICKNAME, "");
        return TextUtils.isEmpty(nickname) ? username() : nickname;
    }

    public String email() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String avatarUrl() {
        return ApiClient.absoluteUrl(prefs.getString(KEY_AVATAR, ""));
    }

    /** 头像位上的首字符，取名字第一个码点。 */
    public String initial() {
        String name = displayName();
        return TextUtils.isEmpty(name) ? "?" : name.substring(0, 1).toUpperCase();
    }

    /** 用 POST /auth/login 的 data 覆盖本地登录态。 */
    public void signIn(JSONObject data) {
        if (data == null) {
            return;
        }
        JSONObject user = data.optJSONObject("user");
        if (user == null) {
            user = new JSONObject();
        }
        long expiresAt = resolveExpiryMillis(data);
        prefs.edit()
                .putString(KEY_TOKEN, data.optString("token"))
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .putString(KEY_USER_ID, user.optString("id"))
                .putString(KEY_USERNAME, user.optString("username"))
                .putString(KEY_NICKNAME, firstNonEmpty(user.optString("nickname"), user.optString("name")))
                .putString(KEY_EMAIL, user.optString("email"))
                .putString(KEY_AVATAR, user.optString("avatar_url"))
                .apply();
    }

    /** /auth/me 回来的是最新账户快照，只覆盖资料字段，不动令牌。 */
    public void refreshProfile(JSONObject data) {
        if (data == null) {
            return;
        }
        JSONObject user = data.optJSONObject("user");
        if (user == null) {
            user = data;
        }
        prefs.edit()
                .putString(KEY_USERNAME, user.optString("username", username()))
                .putString(KEY_NICKNAME, firstNonEmpty(user.optString("nickname"), user.optString("name")))
                .putString(KEY_EMAIL, user.optString("email", email()))
                .putString(KEY_AVATAR, user.optString("avatar_url"))
                .apply();
    }

    public void signOut() {
        prefs.edit().clear().apply();
    }

    private static long resolveExpiryMillis(JSONObject data) {
        long expiresIn = data.optLong("expires_in", 0L);
        if (expiresIn > 0) {
            return System.currentTimeMillis() + expiresIn * 1000L;
        }
        String expiresAt = data.optString("expires_at");
        if (!TextUtils.isEmpty(expiresAt)) {
            try {
                return OffsetDateTime.parse(expiresAt).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static String firstNonEmpty(String first, String second) {
        return TextUtils.isEmpty(first) ? second : first;
    }
}
