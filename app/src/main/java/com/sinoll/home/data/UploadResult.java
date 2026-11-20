package com.sinoll.home.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * POST /upload 的响应。三种 kind 的形状不同，这里按用途归一：
 * 图片取 {@link #paths} 与 {@link #thumbnailPath}，视频取 {@link #videoPath} 等四个字段。
 */
public final class UploadResult {

    /** 按上传顺序排列的图片站内路径，形如 /uploads/images/202611/xxx.jpg。 */
    public final List<String> paths;
    /** 第一张图片的 460×260 缩略图；生成失败为 null，可退回 paths.get(0)。 */
    public final String thumbnailPath;
    public final String videoPath;
    public final int durationSeconds;
    /** 1 横屏 / 2 竖屏，由服务端按显示方向判定。 */
    public final int videoType;

    UploadResult(JSONObject data) {
        List<String> items = new ArrayList<>();
        JSONArray imgs = data.optJSONArray("imgs");
        if (imgs != null) {
            for (int i = 0; i < imgs.length(); i++) {
                JSONObject entry = imgs.optJSONObject(i);
                if (entry != null && !entry.optString("path").isEmpty()) {
                    items.add(entry.optString("path"));
                }
            }
        }
        String single = data.optString("path");
        if (items.isEmpty() && !single.isEmpty()) {
            items.add(single);
        }
        paths = Collections.unmodifiableList(items);
        thumbnailPath = orNull(data.optString("thumbnail_path"));
        videoPath = orNull(data.optString("video_path"));
        durationSeconds = data.optInt("duration_seconds");
        videoType = data.optInt("video_type");
    }

    /** 封面优先用缩略图，缩略图缺失时退回原图路径。 */
    public String coverPath() {
        if (thumbnailPath != null) {
            return thumbnailPath;
        }
        return paths.isEmpty() ? null : paths.get(0);
    }

    private static String orNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
