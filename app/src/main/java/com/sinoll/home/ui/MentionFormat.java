package com.sinoll.home.ui;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.sinoll.home.R;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正文里的 {@code @用户名} 高亮并可点。用户名规则与注册时一致，昵称按同一套字符集匹配，
 * 命中不了只是不高亮，不会把正文改坏。
 */
public final class MentionFormat {

    private static final Pattern MENTION =
            Pattern.compile("@([\\p{IsHan}A-Za-z0-9._-]{2,24})");

    /**
     * 没有提及时完全不挂 movement method：挂了会让文本自己吃掉点击，
     * 卡片整行的「点开看详情」就失效了。有提及时也只在点到链接外时把事件交还给父级。
     */
    public static void bind(TextView view, CharSequence text, Consumer<String> onMention) {
        if (text == null) {
            view.setText("");
            return;
        }
        Matcher matcher = MENTION.matcher(text);
        if (!matcher.find()) {
            view.setText(text);
            view.setMovementMethod(null);
            return;
        }
        SpannableString styled = new SpannableString(text);
        matcher.reset();
        while (matcher.find()) {
            String token = matcher.group(1);
            int start = matcher.start();
            styled.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(view.getContext(), R.color.brand_primary)),
                    start, matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                    start, matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new MentionSpan(token, onMention),
                    start, matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(styled);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /** 补全时把 @ 前已经打出来的关键词取出来，交给 /users/suggest。 */
    public static String tokenAt(CharSequence text, int cursor) {
        String value = text.subSequence(0, Math.min(cursor, text.length())).toString();
        int at = value.lastIndexOf('@');
        if (at < 0) {
            return null;
        }
        String query = value.substring(at + 1);
        // 已经收尾的提及（后面有空格或换行）不再触发补全
        return query.matches("[\\p{IsHan}A-Za-z0-9._-]*") ? query : null;
    }

    /** 从光标处往回找到 @ 的起点，替换候选项时要用。 */
    public static int tokenStart(CharSequence text, int cursor) {
        String value = text.subSequence(0, Math.min(cursor, text.length())).toString();
        return value.lastIndexOf('@');
    }

    private static final class MentionSpan extends android.text.style.ClickableSpan {

        private final String token;
        private final Consumer<String> onMention;

        MentionSpan(String token, Consumer<String> onMention) {
            this.token = token;
            this.onMention = onMention;
        }

        @Override
        public void onClick(android.view.View widget) {
            if (onMention != null) {
                onMention.accept(token);
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MentionSpan && token.equals(((MentionSpan) other).token);
        }

        @Override
        public int hashCode() {
            return token.hashCode();
        }
    }

    private MentionFormat() {
    }
}
