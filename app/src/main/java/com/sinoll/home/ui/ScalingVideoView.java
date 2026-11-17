package com.sinoll.home.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * VideoView 只会把画面拉满自己的尺寸，不会管宽高比。这里按视频真实尺寸重算视图大小：
 * 竖屏铺满裁切，横屏完整居中，两侧留黑。
 */
public class ScalingVideoView extends VideoView {

    private boolean centerCrop;
    private int videoWidth;
    private int videoHeight;

    public ScalingVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCenterCrop(boolean crop) {
        if (centerCrop != crop) {
            centerCrop = crop;
            requestLayout();
        }
    }

    /** VideoView 不暴露解码分辨率，只能在 onPrepared 里把 MediaPlayer 的尺寸喂进来。 */
    public void setVideoSize(int width, int height) {
        if (videoWidth == width && videoHeight == height) {
            return;
        }
        videoWidth = width;
        videoHeight = height;
        requestLayout();
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
