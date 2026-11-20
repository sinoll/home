package com.sinoll.home.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 正文在接口里是 HTML，界面上是纯文本加图片，两头都要一个薄转换层。
 * 服务端会再过一遍净化白名单，客户端只负责拼出结构，不猜哪些标签能留。
 */
public final class PostHtml {

    /** 详情页按块渲染：一段文字或一张图。 */
    public static final class Block {
        public final String text;
        public final String imageUrl;

        private Block(String text, String imageUrl) {
            this.text = text;
            this.imageUrl = imageUrl;
        }
    }

    /**
     * 动态与博客的正文：空行分段，插图按顺序接在文字后面。
     * 话题 {@code #xx#} 原样留在正文里，由服务端抽取成标签，客户端不重复实现。
     */
    public static String article(String text, List<String> imagePaths) {
        StringBuilder html = new StringBuilder();
        for (String paragraph : paragraphs(text)) {
            html.append("<p>").append(escape(paragraph)).append("</p>");
        }
        for (String path : imagePaths) {
            html.append("<p><img src=\"").append(path).append("\" alt=\"\"></p>");
        }
        return html.toString();
    }

    /** 图集说明：首行当每张图的标题，其余行当替代文本，服务端按行拆开。 */
    public static String caption(String title, String note) {
        if (note == null || note.trim().isEmpty()) {
            return escape(title).trim();
        }
        return escape(title).trim() + "\n" + note.trim();
    }

    public static List<Block> blocks(String html) {
        List<Block> blocks = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return blocks;
        }
        int cursor = 0;
        int index = html.indexOf("<img", cursor);
        while (index >= 0) {
            addText(blocks, html.substring(cursor, index));
            String src = attribute(html, index);
            if (src != null) {
                blocks.add(new Block(null, ApiClient.absoluteUrl(src)));
            }
            int end = html.indexOf('>', index);
            cursor = end < 0 ? html.length() : end + 1;
            index = html.indexOf("<img", cursor);
        }
        addText(blocks, html.substring(cursor));
        return blocks;
    }

    /** 正文里已嵌入的站内插图路径，编辑时原样回传即可保留。 */
    public static List<String> imageSrcs(String html) {
        List<String> srcs = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return srcs;
        }
        int index = html.indexOf("<img");
        while (index >= 0) {
            String src = attribute(html, index);
            if (src != null) {
                srcs.add(src);
            }
            int end = html.indexOf('>', index);
            if (end < 0) {
                break;
            }
            index = html.indexOf("<img", end + 1);
        }
        return srcs;
    }

    /** 列表卡片要一行纯文本，详情正文先降级再用。 */
    public static String plainText(String html) {
        StringBuilder text = new StringBuilder();
        for (Block block : blocks(html)) {
            if (block.text == null) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(block.text);
        }
        return text.toString().trim();
    }

    private static void addText(List<Block> blocks, String fragment) {
        String text = decode(stripTags(fragment));
        if (!text.trim().isEmpty()) {
            blocks.add(new Block(text.trim(), null));
        }
    }

    private static List<String> paragraphs(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String line : text.trim().split("\\n\\s*\\n")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                paragraphs.add(value);
            }
        }
        return paragraphs;
    }

    private static String attribute(String html, int tagStart) {
        int start = html.indexOf("src=", tagStart);
        int limit = html.indexOf('>', tagStart);
        if (start < 0 || (limit >= 0 && start > limit)) {
            return null;
        }
        int value = start + 4;
        char quote = value < html.length() ? html.charAt(value) : 0;
        if (quote != '"' && quote != '\'') {
            int end = limit < 0 ? html.length() : limit;
            int space = indexOfWhitespace(html, value, end);
            return decode(html.substring(value, space));
        }
        int end = html.indexOf(quote, value + 1);
        return end < 0 ? null : decode(html.substring(value + 1, end));
    }

    private static int indexOfWhitespace(String value, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return to;
    }

    private static String stripTags(String value) {
        return value
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]*>", "");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String decode(String value) {
        return value
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
    }

    private PostHtml() {
    }
}
