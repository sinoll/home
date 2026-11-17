package com.sinoll.home.data;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 短视频的磁盘预取：按落位顺序往后排一队，边看边下，下好的直接走本地文件播。
 * 单线程串行下载，一次只占一条带宽，不跟正在流播的那条抢。
 */
public final class VideoCache {

    /**
     * 保留条数与总体积上限，任一超了就按「最久没播过」的先清。
     * 条数留到 8 是为了让往后翻的几条始终有存货，也满足至少缓存 5 个的要求。
     */
    private static final int KEEP = 8;
    private static final long MAX_BYTES = 200L * 1024 * 1024;
    private static final String PART_SUFFIX = ".part";

    private static final ExecutorService WORKERS = Executors.newSingleThreadExecutor();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    /** 主线程每次换落位都重写这份名单，下载前先确认这条还在名单里，滑走了就放弃。 */
    private static volatile Set<String> plan = new HashSet<>();
    private static volatile File directory;

    /** 已经缓存就给出本地路径，顺带记一笔「刚播过」，让它排在淘汰队列最后。 */
    public static String localPath(Context context, String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        File file = fileFor(context, url);
        if (!file.isFile()) {
            return null;
        }
        file.setLastModified(System.currentTimeMillis());
        return file.getAbsolutePath();
    }

    /** urls 按优先级从高到低给出，排在前面先进队。 */
    public static void prefill(Context context, List<String> urls) {
        Context app = context.getApplicationContext();
        plan = new HashSet<>(urls);
        for (String url : urls) {
            if (url == null || url.isEmpty() || fileFor(app, url).isFile() || !IN_FLIGHT.add(url)) {
                continue;
            }
            WORKERS.execute(() -> {
                try {
                    if (plan.contains(url)) {
                        download(app, url);
                    }
                } finally {
                    IN_FLIGHT.remove(url);
                }
            });
        }
    }

    private static void download(Context context, String url) {
        File target = fileFor(context, url);
        File part = new File(target.getAbsolutePath() + PART_SUFFIX);
        HttpURLConnection conn = null;
        long written = 0L;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                return;
            }
            long expected = conn.getContentLength();
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(part)) {
                byte[] chunk = new byte[16384];
                int read;
                while ((read = in.read(chunk)) != -1 && written + read <= MAX_BYTES) {
                    out.write(chunk, 0, read);
                    written += read;
                }
                out.flush();
            }
            if (written > 0 && (expected <= 0 || written == expected)
                    && part.renameTo(target)) {
                trim(context);
            }
        } catch (Exception e) {
            // 断流、超时、磁盘满都当没下过，下次进名单再补
        } finally {
            part.delete();
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void trim(Context context) {
        File dir = dir(context);
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        List<File> videos = new ArrayList<>();
        for (File entry : entries) {
            if (entry.getName().endsWith(PART_SUFFIX)) {
                // 进程被杀留下的半截文件，没有价值
                entry.delete();
            } else if (entry.isFile()) {
                videos.add(entry);
            }
        }
        File[] files = videos.toArray(new File[0]);
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long total = 0L;
        for (File file : files) {
            total += file.length();
        }
        for (int i = 0; i < files.length && (files.length - i > KEEP || total > MAX_BYTES); i++) {
            total -= files[i].length();
            files[i].delete();
        }
    }

    private static File fileFor(Context context, String url) {
        return new File(dir(context), fileName(url));
    }

    private static File dir(Context context) {
        File current = directory;
        if (current != null) {
            return current;
        }
        File created = new File(context.getCacheDir(), "videos");
        if (!created.isDirectory()) {
            created.mkdirs();
        }
        directory = created;
        return created;
    }

    /** 文件名用整条 URL 的摘要，扩展名沿用接口给的，认不出来按 mp4 记。 */
    private static String fileName(String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        String extension = dot > slash ? path.substring(dot) : "";
        if (extension.length() > 6) {
            extension = "";
        }
        return digest(url) + (extension.isEmpty() ? ".mp4" : extension);
    }

    private static String digest(String value) {
        char[] hex = "0123456789abcdef".toCharArray();
        byte[] bytes;
        try {
            bytes = MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            bytes = value.getBytes(StandardCharsets.UTF_8);
        }
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            text.append(hex[(b >> 4) & 0x0F]).append(hex[b & 0x0F]);
        }
        return text.toString();
    }

    private VideoCache() {
    }
}
