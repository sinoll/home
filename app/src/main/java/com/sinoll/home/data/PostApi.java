package com.sinoll.home.data;

import android.content.Context;

import org.json.JSONObject;

/**
 * 四类内容的读写入口。URL 段就是 {@link Post#type}，短视频的接口名是 short_video。
 */
public final class PostApi {

    public static void detail(Context context, String type, int id, ApiClient.Callback callback) {
        ApiClient.get("/" + type + "/" + id, AuthSession.get(context).token(), callback);
    }

    public static void create(Context context, String type, JSONObject payload,
                              ApiClient.Callback callback) {
        ApiClient.post("/" + type, payload, AuthSession.get(context).token(), callback);
    }

    /**
     * 部分更新：请求里没出现的字段保持原值。
     * 注意 tags 只在提交 tags 或话题字段时整组重算，只传 {@code tags:[]} 会连正文里的话题一起清掉。
     */
    public static void update(Context context, String type, int id, JSONObject payload,
                              ApiClient.Callback callback) {
        ApiClient.patch("/" + type + "/" + id, payload, AuthSession.get(context).token(), callback);
    }

    /** 删除只回收这份内容独占的媒体文件，正文内嵌插图不在回收范围内。 */
    public static void remove(Context context, String type, int id, ApiClient.Callback callback) {
        ApiClient.delete("/" + type + "/" + id, AuthSession.get(context).token(), callback);
    }

    /** 改可见范围或发布状态时不带正文，避免服务端按新请求重算话题。 */
    public static void updateStatus(Context context, String type, int id, int status,
                                    ApiClient.Callback callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("status", status);
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        update(context, type, id, payload, callback);
    }

    private PostApi() {
    }
}
