package com.sinoll.home.data;

import android.os.Handler;
import android.os.Looper;

import com.sinoll.home.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 拾光 RESTful 接口的传输层：统一 {code,message,data} 封装，回调固定回到主线程。
 */
public final class ApiClient {

    public static final String BASE_URL = BuildConfig.BASE_URL;
    /** 前台站点地址：接口和网页不在同一个域名，分享出去的链接要用这个。 */
    public static final String SITE_URL = BuildConfig.SITE_URL;

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 12000;

    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(Response response);
    }

    public static final class Response {

        public final boolean success;
        public final int code;
        public final String message;
        public final JSONObject data;
        public final Failure failure;

        Response(boolean success, int code, String message, JSONObject data, Failure failure) {
            this.success = success;
            this.code = code;
            this.message = message == null ? "" : message;
            this.data = data == null ? new JSONObject() : data;
            this.failure = failure;
        }

        /** 校验失败明细，形如 {"email":["邮箱已被注册"]}；没有则返回空对象。 */
        public JSONObject errors() {
            JSONObject errors = data.optJSONObject("errors");
            return errors == null ? new JSONObject() : errors;
        }

        /** 取 errors 里某个字段的第一条说明，没有则返回 null。 */
        public String errorOf(String field) {
            Object value = errors().opt(field);
            if (value instanceof org.json.JSONArray) {
                org.json.JSONArray list = (org.json.JSONArray) value;
                if (list.length() > 0) {
                    return list.optString(0);
                }
            }
            return value instanceof String ? (String) value : null;
        }
    }

    public enum Failure {
        NONE,
        NETWORK,
        TIMEOUT
    }

    public static String absoluteUrl(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return path.startsWith("/") ? BASE_URL + path : BASE_URL + "/" + path;
    }

    public static void post(String path, JSONObject payload, String token, Callback callback) {
        send("POST", path, payload, token, callback);
    }

    public static void patch(String path, JSONObject payload, String token, Callback callback) {
        send("PATCH", path, payload, token, callback);
    }

    public static void get(String path, String token, Callback callback) {
        send("GET", path, null, token, callback);
    }

    private static void send(String method, String path, JSONObject payload,
                             String token, Callback callback) {
        WORKERS.execute(() -> {
            Response response = execute(method, absoluteUrl(path), payload, token);
            MAIN.post(() -> callback.onResult(response));
        });
    }

    private static Response execute(String method, String url, JSONObject payload, String token) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            // 重定向器不会重放 PATCH 的请求体，跟着跳只会换来一个莫名失败
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (payload != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body);
                }
            }

            int status = conn.getResponseCode();
            String raw = readFully(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            JSONObject json = null;
            try {
                json = new JSONObject(raw);
            } catch (JSONException ignored) {
                // 网关或静态资源返回的非 JSON 响应，落到 status 上
            }
            if (json == null) {
                return failure(status >= 400 ? Failure.NONE : Failure.NETWORK, status);
            }
            JSONObject data = json.optJSONObject("data");
            int code = json.optInt("code", status);
            String message = json.optString("message");
            boolean ok = status >= 200 && status < 300 && code >= 200 && code < 300;
            return new Response(ok, code, message, data, Failure.NONE);
        } catch (SocketTimeoutException e) {
            return failure(Failure.TIMEOUT, 0);
        } catch (IOException e) {
            return failure(Failure.NETWORK, 0);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 传输层不拼用户可见文案，交给界面对应 string 资源，避免文案两处维护。 */
    private static Response failure(Failure kind, int status) {
        return new Response(false, status, "", null, kind);
    }

    private static String readFully(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream source = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = source.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private ApiClient() {
    }
}
