package com.sinoll.home.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import com.sinoll.home.R;
import com.sinoll.home.data.AuthSession;
import com.sinoll.home.data.Post;
import com.sinoll.home.ui.auth.LoginActivity;
import com.sinoll.home.ui.detail.PostDetailActivity;
import com.sinoll.home.ui.messages.PrivateMessageActivity;

/** 列表卡片与详情页共用的四个动作实现，界面只把点击递进来。 */
public final class PostActionRouter implements PostActions {

    private final Context context;

    public PostActionRouter(Context context) {
        this.context = context;
    }

    @Override
    public void onOpen(Post post) {
        // 详情页自己带播放器，短视频也照样进详情；只有视频 tab 不套这一层
        context.startActivity(PostDetailActivity.intent(context, post.type, post.id));
    }

    @Override
    public void onComment(Post post, Runnable onCommented) {
        if (!AuthSession.get(context).isSignedIn()) {
            context.startActivity(new Intent(context, LoginActivity.class));
            return;
        }
        CommentComposer.show(context, post, () -> {
            post.commentCount++;
            onCommented.run();
        });
    }

    /** 分享的是前台网页地址：接口域名打不开给人看的页面。 */
    @Override
    public void onShare(Post post) {
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

    /** 私信要登录，和评论同一套兜底。 */
    @Override
    public void onMention(String username) {
        if (!AuthSession.get(context).isSignedIn()) {
            context.startActivity(new Intent(context, LoginActivity.class));
            return;
        }
        context.startActivity(PrivateMessageActivity.intent(context, username));
    }
}
