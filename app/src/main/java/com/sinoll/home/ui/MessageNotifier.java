package com.sinoll.home.ui;

import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.sinoll.home.R;
import com.sinoll.home.data.AuthSession;
import com.sinoll.home.data.MessageApi;

/**
 * 未读消息提醒：只在回前台时查一次，未登录既不发请求也不弹条。
 */
public final class MessageNotifier {

    private final AppCompatActivity host;
    private final View anchor;
    private final Runnable onViewMessages;

    private Snackbar current;

    public MessageNotifier(AppCompatActivity host, View anchor, Runnable onViewMessages) {
        this.host = host;
        this.anchor = anchor;
        this.onViewMessages = onViewMessages;
    }

    public void refresh() {
        if (!AuthSession.get(host).isSignedIn()) {
            return;
        }
        MessageApi.unreadCount(host, count -> {
            if (count <= 0 || host.isFinishing() || host.isDestroyed()) {
                return;
            }
            show(count);
        });
    }

    /** 每次回前台只留最新一条，前一条还挂在屏幕上就先收掉。 */
    private void show(int count) {
        if (current != null) {
            current.dismiss();
        }
        current = Snackbar.make(anchor,
                        host.getString(R.string.state_unread_messages, count),
                        Snackbar.LENGTH_LONG)
                .setActionTextColor(color(R.color.brand_secondary))
                .setAction(R.string.action_view_messages, view -> onViewMessages.run());
        applyIcon(current);
        current.show();
    }

    /** Snackbar 没有图标位，挂到文字左侧是改动最小、也不会打乱原生动画的做法。 */
    private void applyIcon(Snackbar bar) {
        TextView text = bar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        text.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_message_badge, 0, 0, 0);
        text.setCompoundDrawablePadding(PostFormat.dp(host, 12));
    }

    private int color(int res) {
        return ContextCompat.getColor(host, res);
    }
}
