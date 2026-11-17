package com.sinoll.home.ui;

import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.sinoll.home.R;
import com.sinoll.home.databinding.ViewFeedStateBinding;

/**
 * 列表叠在内容上的空 / 加载 / 失败态，由 fragment 的分页回调驱动。
 * 视频 tab 是深色底，构造时换一套文字颜色。
 */
public final class FeedState {

    private final ViewFeedStateBinding binding;

    public FeedState(ViewFeedStateBinding binding, boolean onDark) {
        this.binding = binding;
        if (onDark) {
            binding.message.setTextColor(color(R.color.on_dark_muted));
            binding.retry.setTextColor(color(R.color.brand_secondary));
        }
    }

    public void showLoading(@StringRes int message) {
        visible(true);
        binding.progress.setVisibility(View.VISIBLE);
        binding.message.setText(message);
        binding.retry.setVisibility(View.GONE);
    }

    public void showMessage(@StringRes int message) {
        visible(true);
        binding.progress.setVisibility(View.GONE);
        binding.message.setText(message);
        binding.retry.setVisibility(View.GONE);
    }

    public void showFailed(String message) {
        visible(true);
        binding.progress.setVisibility(View.GONE);
        binding.message.setText(message);
        binding.retry.setVisibility(View.VISIBLE);
    }

    public void hide() {
        visible(false);
    }

    private void visible(boolean show) {
        binding.getRoot().setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private int color(@ColorRes int res) {
        return ContextCompat.getColor(binding.getRoot().getContext(), res);
    }
}
