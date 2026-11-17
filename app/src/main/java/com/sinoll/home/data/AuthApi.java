package com.sinoll.home.data;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * /auth/* 各端点的入参组装。成功登录时顺手落地登录态，界面向导只关心结果。
 */
public final class AuthApi {

    public static void login(Context context, String username, String password,
                             ApiClient.Callback callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("username", username);
            payload.put("password", password);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        ApiClient.post("/auth/login", payload, null, response -> {
            if (response.success) {
                AuthSession.get(context).signIn(response.data);
            }
            callback.onResult(response);
        });
    }

    public static void sendRegisterCode(String email, ApiClient.Callback callback) {
        ApiClient.post("/auth/register/code", emailPayload(email), null, callback);
    }

    public static void register(String username, String nickname, String email, String code,
                                String password, String confirmPassword,
                                ApiClient.Callback callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("username", username);
            if (!nickname.isEmpty()) {
                payload.put("nickname", nickname);
            }
            payload.put("email", email);
            payload.put("code", code);
            payload.put("password", password);
            payload.put("confirmPassword", confirmPassword);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        ApiClient.post("/auth/register", payload, null, callback);
    }

    /** 找回密码的第一步：服务端校验邮箱后投递验证码。 */
    public static void sendResetCode(String email, ApiClient.Callback callback) {
        ApiClient.post("/auth/forgot-password", emailPayload(email), null, callback);
    }

    public static void resetPassword(String email, String code, String password,
                                     String confirmPassword, ApiClient.Callback callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("email", email);
            payload.put("code", code);
            payload.put("password", password);
            payload.put("confirmPassword", confirmPassword);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        ApiClient.post("/auth/reset-password", payload, null, callback);
    }

    public static void fetchMe(Context context, ApiClient.Callback callback) {
        ApiClient.get("/auth/me", AuthSession.get(context).token(), response -> {
            if (response.success) {
                AuthSession.get(context).refreshProfile(response.data);
            }
            callback.onResult(response);
        });
    }

    /** 无状态令牌在到期前仍然可用，所以这里无论服务端返回什么都清本地。 */
    public static void logout(Context context, ApiClient.Callback callback) {
        AuthSession session = AuthSession.get(context);
        ApiClient.post("/auth/logout", new JSONObject(), session.token(), response -> {
            session.signOut();
            callback.onResult(response);
        });
    }

    private static JSONObject emailPayload(String email) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("email", email);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return payload;
    }

    private AuthApi() {
    }
}
