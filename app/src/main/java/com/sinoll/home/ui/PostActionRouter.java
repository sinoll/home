package com.sinoll.home.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.sinoll.home.R;
import com.sinoll.home.data.AuthSession;
import com.sinoll.home.data.Post;
import com.sinoll.home.ui.auth.LoginActivity;

/** 三个列表页共用的评论与分享实现，卡片只把点击递进来。 */
public final class PostActionRouter implements PostActions {

    private final Fragment host;

    public PostActionRouter(Fragment host) {
        this.host = host;
    }

    @Override
    public void onComment(Post post, Runnable onCommented) {
        Context context = host.requireContext();
        if (!AuthSession.get(context).isSignedIn()) {
            context.startActivity(new Intent(context, LoginActivity.class));
            return;
        }
        CommentComposer.show(host, post, () -> {
            post.commentCount++;
            onCommented.run();
        });
    }

    /** 分享的是前台网页地址：接口域名打不开给人看的页面。 */
    @Override
    public void onShare(Post post) {
        Context context = host.requireContext();
        String summary = !TextUtils.isEmpty(post.title) ? post.title : post.body;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,
                (summary == null ? "" : summary.trim()) + "\n" + post.webUrl);
        try {
            context.startActivity(Intent.createChooser(intent,
                    context.getString(R.string.action_share)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.share_none, Toast.LENGTH_SHORT).show();
        }
    }
}
