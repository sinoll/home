package com.sinoll.home.ui;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.sinoll.home.R;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Post;
import com.sinoll.home.data.VideoCache;

import java.io.File;
import java.util.Collections;

/**
 * 详情页里的内嵌播放器：封面先垫着，点一下才真正解码。
 * 和视频 tab 共用 VideoCache，从那儿看过的视频在这儿是本地文件直接起播。
 */
public final class InlineVideo extends FrameLayout {

    private final ScalingVideoView video;
    private final ImageView cover;
    private final ImageView play;
    private final String videoUrl;
    private boolean started;
    /** 解码器就绪前只有 setVideoURI 可做，起播动作得挂到 onPrepared 上。 */
    private boolean prepared;

    public InlineVideo(@NonNull Context context, Post post) {
        super(context);
        videoUrl = post.videoUrl;
        setBackgroundColor(ContextCompat.getColor(context, R.color.black));

        // 竖屏给足高度，横屏压扁一条，两种方向都不会在正文里占满整屏
        int height = PostFormat.dp(context, post.vertical ? 320 : 200);
        setMinimumHeight(height);

        video = new ScalingVideoView(context, null);
        video.setCenterCrop(false);
        video.setLayoutParams(match(height));
        video.setOnPreparedListener(player -> {
            prepared = true;
            player.setLooping(false);
            // VideoView 不给真实分辨率，只能从 MediaPlayer 这儿拿；少了这一步画面会被拉变形
            video.setVideoSize(player.getVideoWidth(), player.getVideoHeight());
            if (started) {
                player.start();
            }
        });
        video.setOnInfoListener((player, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                hideCover();
            }
            return true;
        });
        video.setOnErrorListener((player, what, extra) -> {
            stop();
            return true;
        });
        video.setOnCompletionListener(player -> stop());
        addView(video);

        cover = new ImageView(context);
        cover.setScaleType(post.vertical ? ImageView.ScaleType.CENTER_CROP
                : ImageView.ScaleType.FIT_CENTER);
        cover.setLayoutParams(match(height));
        cover.setContentDescription(context.getString(R.string.card_video_desc));
        cover.setBackgroundResource(PostFormat.coverFallback(post.id));
        ImageLoader.into(cover, post.coverUrl, PostFormat.dp(context, 400));
        addView(cover);

        play = new ImageView(context);
        int badge = PostFormat.dp(context, 56);
        LayoutParams badgeParams = new LayoutParams(badge, badge);
        badgeParams.gravity = Gravity.CENTER;
        play.setLayoutParams(badgeParams);
        play.setAlpha(0.9f);
        play.setImageResource(R.drawable.ic_play);
        play.setContentDescription(context.getString(R.string.action_play));
        addView(play);

        String duration = PostFormat.duration(post.durationSeconds);
        if (!duration.isEmpty()) {
            TextView chip = new TextView(context);
            LayoutParams chipParams = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            chipParams.gravity = Gravity.TOP | Gravity.END;
            int margin = PostFormat.dp(context, 8);
            chipParams.setMargins(margin, margin, margin, margin);
            chip.setLayoutParams(chipParams);
            chip.setBackgroundResource(R.drawable.bg_badge_dark);
            chip.setPadding(margin, PostFormat.dp(context, 2), margin,
                    PostFormat.dp(context, 2));
            chip.setTextColor(ContextCompat.getColor(context, R.color.white));
            chip.setTextSize(11);
            chip.setText(duration);
            addView(chip);
        }

        setOnClickListener(view -> toggle());
        video.setOnClickListener(view -> toggle());
        cover.setOnClickListener(view -> toggle());
        play.setOnClickListener(view -> toggle());
    }

    private LayoutParams match(int height) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, height);
        params.gravity = Gravity.CENTER;
        return params;
    }

    private void toggle() {
        if (videoUrl == null) {
            return;
        }
        if (started) {
            video.pause();
            play.setVisibility(View.VISIBLE);
            started = false;
            return;
        }
        if (!prepared) {
            String local = VideoCache.localPath(getContext(), videoUrl);
            video.setVideoURI(local != null
                    ? Uri.fromFile(new File(local)) : Uri.parse(videoUrl));
        } else {
            video.start();
        }
        // 详情页看过的视频也进缓存，下次从哪儿看都是本地起播
        VideoCache.prefill(getContext(), Collections.singletonList(videoUrl));
        play.setVisibility(View.GONE);
        started = true;
    }

    /** 播完或出错都回到封面态，下次点击从头再来，不留一个黑屏在正文中间。 */
    private void stop() {
        started = false;
        video.seekTo(0);
        cover.setVisibility(View.VISIBLE);
        play.setVisibility(View.VISIBLE);
    }

    private void hideCover() {
        cover.setVisibility(View.GONE);
        play.setVisibility(View.GONE);
    }

    public void release() {
        video.stopPlayback();
        prepared = false;
        started = false;
    }
}
