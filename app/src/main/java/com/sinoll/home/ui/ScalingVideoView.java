package com.sinoll.home.ui;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;

/**
 * 自绘播放器：TextureView + MediaPlayer。
 * 不用 VideoView（SurfaceView）是因为它的 surface 一改尺寸或加变换就重建，
 * 弹半屏评论把视频缩到上半屏时画面会黑住像暂停；TextureView 改尺寸和缩放都不打断解码。
 * 尺寸仍按视频真实宽高比重算：竖屏铺满裁切，横屏完整居中两侧留黑。
 */
public class ScalingVideoView extends TextureView implements TextureView.SurfaceTextureListener {

    public interface OnPreparedListener {
        void onPrepared(MediaPlayer mp);
    }

    public interface OnInfoListener {
        boolean onInfo(MediaPlayer mp, int what, int extra);
    }

    public interface OnErrorListener {
        boolean onError(MediaPlayer mp, int what, int extra);
    }

    public interface OnCompletionListener {
        void onCompletion(MediaPlayer mp);
    }

    private boolean centerCrop;
    private int videoWidth;
    private int videoHeight;

    private MediaPlayer player;
    private Uri uri;
    private Surface surface;
    private boolean surfaceReady;
    private boolean prepared;
    /** start() 可能在解码器就绪前被调用，记下来等 prepared 再真正起播。 */
    private boolean wantPlay;

    private OnPreparedListener preparedListener;
    private OnInfoListener infoListener;
    private OnErrorListener errorListener;
    private OnCompletionListener completionListener;

    public ScalingVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSurfaceTextureListener(this);
    }

    public void setOnPreparedListener(OnPreparedListener listener) {
        preparedListener = listener;
    }

    public void setOnInfoListener(OnInfoListener listener) {
        infoListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        errorListener = listener;
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        completionListener = listener;
    }

    public void setCenterCrop(boolean crop) {
        if (centerCrop != crop) {
            centerCrop = crop;
            requestLayout();
        }
    }

    /** 外部在 onPrepared 里把 MediaPlayer 的真实分辨率喂进来，少了这一步画面会被拉变形。 */
    public void setVideoSize(int width, int height) {
        if (videoWidth == width && videoHeight == height) {
            return;
        }
        videoWidth = width;
        videoHeight = height;
        requestLayout();
    }

    public void setVideoURI(Uri value) {
        uri = value;
        releasePlayer();
        prepare();
    }

    public void start() {
        wantPlay = true;
        if (prepared) {
            player.start();
        }
    }

    public void pause() {
        wantPlay = false;
        if (prepared) {
            player.pause();
        }
    }

    public boolean isPlaying() {
        return prepared && player.isPlaying();
    }

    public void seekTo(int msec) {
        if (prepared) {
            player.seekTo(msec);
        }
    }

    public void stopPlayback() {
        wantPlay = false;
        releasePlayer();
    }

    private void prepare() {
        if (uri == null || !surfaceReady || player != null) {
            return;
        }
        try {
            MediaPlayer created = new MediaPlayer();
            created.setSurface(surface);
            created.setDataSource(getContext(), uri);
            created.setOnPreparedListener(mp -> {
                prepared = true;
                if (preparedListener != null) {
                    preparedListener.onPrepared(mp);
                }
                if (wantPlay) {
                    mp.start();
                }
            });
            created.setOnInfoListener((mp, what, extra) ->
                    infoListener != null && infoListener.onInfo(mp, what, extra));
            created.setOnErrorListener((mp, what, extra) ->
                    errorListener != null && errorListener.onError(mp, what, extra));
            created.setOnCompletionListener(mp -> {
                if (completionListener != null) {
                    completionListener.onCompletion(mp);
                }
            });
            player = created;
            created.prepareAsync();
        } catch (IOException e) {
            if (errorListener != null) {
                errorListener.onError(player, 0, 0);
            }
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
    }

    @Override
    public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture texture,
                                          int width, int height) {
        surface = new Surface(texture);
        surfaceReady = true;
        prepare();
    }

    @Override
    public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture texture,
                                            int width, int height) {
        // TextureView 改尺寸不重建 surface，解码器不用动
    }

    @Override
    public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture texture) {
        surfaceReady = false;
        if (surface != null) {
            surface.release();
            surface = null;
        }
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture texture) {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int spaceWidth = MeasureSpec.getSize(widthMeasureSpec);
        int spaceHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (videoWidth == 0 || videoHeight == 0 || spaceWidth == 0 || spaceHeight == 0) {
            setMeasuredDimension(spaceWidth, spaceHeight);
            return;
        }
        float scale = centerCrop
                ? Math.max(spaceWidth / (float) videoWidth, spaceHeight / (float) videoHeight)
                : Math.min(spaceWidth / (float) videoWidth, spaceHeight / (float) videoHeight);
        setMeasuredDimension(Math.round(videoWidth * scale), Math.round(videoHeight * scale));
    }
}
