package com.sinoll.home.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 列表图片下载：按目标边长下采样、内存缓存、按 view tag 丢弃复用后的过期回调。
 */
public final class ImageLoader {

    public interface BitmapCallback {
        void onBitmap(Bitmap bitmap);
    }

    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    /** 矩形图，交给 ImageView 的 scaleType 做裁切。 */
    public static void into(ImageView view, String url, int maxSidePx) {
        into(view, url, maxSidePx, false);
    }

    /** 圆形头像，尺寸即直径。 */
    public static void intoCircle(ImageView view, String url, int sizePx) {
        into(view, url, sizePx, true);
    }

    private static void into(ImageView view, String url, int targetPx, boolean circular) {
        view.setTag(url);
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(null);
            return;
        }
        String key = (circular ? "c" : "r") + targetPx + "|" + url;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        WORKERS.execute(() -> {
            Bitmap bitmap = decode(url, targetPx);
            if (bitmap != null) {
                bitmap = circular ? toCircle(bitmap, targetPx) : fitWithin(bitmap, targetPx);
            }
            Bitmap result = bitmap;
            MAIN.post(() -> {
                if (result == null || !url.equals(view.getTag())) {
                    return;
                }
                CACHE.put(key, result);
                view.setImageBitmap(result);
            });
        });
    }

    /** 首屏头像一类的小图，回调交回调用方自己处理降级。 */
    public static void loadCircular(String url, int targetPx, BitmapCallback callback) {
        if (url == null || url.isEmpty()) {
            return;
        }
        String key = "c" + targetPx + "|" + url;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            MAIN.post(() -> callback.onBitmap(cached));
            return;
        }
        WORKERS.execute(() -> {
            Bitmap bitmap = decode(url, targetPx);
            if (bitmap != null) {
                bitmap = toCircle(bitmap, targetPx);
            }
            Bitmap result = bitmap;
            if (result != null) {
                CACHE.put(key, result);
            }
            MAIN.post(() -> callback.onBitmap(result));
        });
    }

    private static Bitmap decode(String url, int targetPx) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            byte[] bytes;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                bytes = buffer.toByteArray();
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, Math.min(bounds.outWidth, bounds.outHeight) / targetPx);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 长边压到 targetPx，短边等比，保留原始比例。 */
    private static Bitmap fitWithin(Bitmap source, int targetPx) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= targetPx) {
            return source;
        }
        float scale = targetPx / (float) longest;
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
        if (scaled != source) {
            source.recycle();
        }
        return scaled;
    }

    private static Bitmap toCircle(Bitmap source, int size) {
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        int side = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(source, new Rect(left, top, left + side, top + side),
                new RectF(0, 0, size, size), paint);
        if (source != output) {
            source.recycle();
        }
        return output;
    }

    private ImageLoader() {
    }
}
