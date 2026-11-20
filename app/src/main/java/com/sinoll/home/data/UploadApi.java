package com.sinoll.home.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 内容接口不收文件，只收这里返回的站内路径，所以发布流程固定是「先上传、再提交 JSON」。
 * 请求体手拼 multipart：项目离线构建，引不了 OkHttp。
 */
public final class UploadApi {

    public static final String KIND_BLOG = "blog";
    public static final String KIND_MOMENT = "moment";
    public static final String KIND_IMAGES = "images";
    public static final String KIND_VIDEO = "video";
    /** 分类封面是单图上传，落盘目录平铺，不生成缩略图。 */
    public static final String KIND_CATE = "cate";

    /** 服务端图片单文件上限 20 MB；视频上限由服务器 PHP 配置决定，客户端不猜。 */
    private static final long IMAGE_LIMIT_BYTES = 20L * 1024 * 1024;

    private static final List<String> IMAGE_EXTENSIONS =
            Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
    private static final List<String> VIDEO_EXTENSIONS =
            Arrays.asList("mp4", "webm", "mov", "ogv", "ogg");

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    /** 20 MB 的图在局域网里也要几十秒，沿用普通请求的 12 秒只会稳定超时。 */
    private static final int READ_TIMEOUT_MS = 180_000;

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);

    public interface Progress {
        /** 整批文件的合并进度，0~100。 */
        void onProgress(int percent);
    }

    public interface UploadCallback {
        /** result 在失败时为 null；原始响应留给界面取 errors 明细。 */
        void onUploaded(ApiClient.Response response, UploadResult result);
    }

    public static void upload(Context context, String kind, List<Uri> files, Progress progress,
                              UploadCallback callback) {
        List<UploadFile> uploads = new ArrayList<>();
        ApiClient.Failure rejection = ApiClient.Failure.NONE;
        for (Uri file : files) {
            String name = displayName(context, file);
            long size = sizeOf(context, file);
            String extension = name == null ? "" : extension(name);
            if (name == null || size < 0) {
                rejection = ApiClient.Failure.UNREADABLE;
            } else if (!allowed(kind).contains(extension)) {
                rejection = ApiClient.Failure.BAD_TYPE;
            } else if (!KIND_VIDEO.equals(kind) && size > IMAGE_LIMIT_BYTES) {
                rejection = ApiClient.Failure.TOO_LARGE;
            }
            if (rejection != ApiClient.Failure.NONE) {
                // 有一个不合格就整批停下来：半批上传成功只会让服务端多出一堆没人引用的文件
                uploads.clear();
                break;
            }
            uploads.add(new UploadFile(file, safeName(name), extension, size));
        }
        String token = AuthSession.get(context).token();
        ApiClient.Failure blocked = rejection;
        ApiClient.async(() -> uploads.isEmpty()
                        ? new ApiClient.Response(false, 0, "", null,
                        blocked == ApiClient.Failure.NONE
                                ? ApiClient.Failure.UNREADABLE : blocked)
                        : send(context, kind, uploads, token, progress),
                response -> callback.onUploaded(response,
                        response.success ? new UploadResult(response.data) : null));
    }

    private static List<String> allowed(String kind) {
        return KIND_VIDEO.equals(kind) ? VIDEO_EXTENSIONS : IMAGE_EXTENSIONS;
    }

    private static ApiClient.Response send(Context context, String kind, List<UploadFile> files,
                                           String token, Progress progress) {
        String boundary = "home-" + Long.toHexString(ThreadLocalRandom.current().nextLong());
        HttpURLConnection conn = null;
        try {
            conn = ApiClient.open("POST", "/upload", token);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);

            byte[] kindPart = textField(boundary, "kind", kind);
            byte[] closing = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            long total = kindPart.length + closing.length;
            String field = fieldName(kind);
            for (UploadFile file : files) {
                total += fileHeader(boundary, field, file).length + file.size + CRLF.length;
            }
            conn.setFixedLengthStreamingMode(total);

            OutputStream out = conn.getOutputStream();
            out.write(kindPart);
            long done = kindPart.length;
            for (UploadFile file : files) {
                byte[] header = fileHeader(boundary, field, file);
                out.write(header);
                done += header.length;
                done += stream(context, file, out, done, total, progress);
                out.write(CRLF);
                done += CRLF.length;
            }
            out.write(closing);
            out.flush();
            out.close();
            return ApiClient.read(conn);
        } catch (SocketTimeoutException e) {
            return new ApiClient.Response(false, 0, "", null, ApiClient.Failure.TIMEOUT);
        } catch (IOException e) {
            return new ApiClient.Response(false, 0, "", null, ApiClient.Failure.NETWORK);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static long stream(Context context, UploadFile file, OutputStream out,
                               long done, long total, Progress progress) throws IOException {
        byte[] chunk = new byte[16 * 1024];
        long written = 0;
        int reported = -1;
        try (InputStream in = context.getContentResolver().openInputStream(file.uri)) {
            if (in == null) {
                throw new IOException("内容授权已失效: " + file.uri);
            }
            int read;
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
                written += read;
                int percent = (int) ((done + written) * 100 / Math.max(total, 1));
                // 每 5% 报一次：进度条不需要每个 16K 块都回主线程重画
                if (progress != null && percent / 5 > reported / 5) {
                    reported = percent;
                    final int value = percent;
                    ApiClient.onMain(() -> progress.onProgress(value));
                }
            }
        }
        return written;
    }

    private static byte[] textField(String boundary, String name, String value) {
        return ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    /** 图片类一次收多张，字段名带方括号；视频和分类封面只收单个，用裸 file。 */
    private static String fieldName(String kind) {
        return KIND_VIDEO.equals(kind) || KIND_CATE.equals(kind) ? "file" : "file[]";
    }

    private static byte[] fileHeader(String boundary, String field, UploadFile file) {
        return ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + file.name + "\"\r\n"
                + "Content-Type: " + mime(file.extension) + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String mime(String extension) {
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return TextUtils.isEmpty(type) ? "application/octet-stream" : type;
    }

    /** 文件名要进请求头，换行、引号和路径分隔符必须先去掉。 */
    private static String safeName(String name) {
        return name.replaceAll("[\r\n\"\\\\/]", "_");
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 相册里的原始文件名，界面拿它告诉用户选中的是哪一段。 */
    public static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) {
            // 读不到名字就交给 lastPathSegment 兜底
        }
        String segment = uri.getLastPathSegment();
        return segment == null || segment.contains("/") ? null : segment;
    }

    private static long sizeOf(Context context, Uri uri) {
        // 拍摄产物是自己的 file:// 文件，ContentResolver 查不到列，直接看磁盘
        if ("file".equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            return file.isFile() ? file.length() : -1L;
        }
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
            // 落到未知长度，由调用方判为不可读
        }
        return -1L;
    }

    private static final class UploadFile {
        final Uri uri;
        final String name;
        final String extension;
        final long size;

        UploadFile(Uri uri, String name, String extension, long size) {
            this.uri = uri;
            this.name = name;
            this.extension = extension;
            this.size = size;
        }
    }

    private UploadApi() {
    }
}
