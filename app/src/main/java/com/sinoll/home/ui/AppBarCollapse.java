package com.sinoll.home.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.sinoll.home.MainActivity;

/**
 * 列表纵向滑动时收放标题栏。位移要攒够阈值才动作，否则惯性回弹的几像素会让标题栏来回抖。
 */
public final class AppBarCollapse extends RecyclerView.OnScrollListener {

    private static final int TRAVEL_THRESHOLD_DP = 16;

    private final MainActivity host;
    private final int threshold;
    private int travel;

    private AppBarCollapse(MainActivity host, int threshold) {
        this.host = host;
        this.threshold = threshold;
    }

    public static void attach(RecyclerView list, Fragment fragment) {
        if (fragment.getActivity() instanceof MainActivity) {
            list.addOnScrollListener(new AppBarCollapse(
                    (MainActivity) fragment.getActivity(),
                    PostFormat.dp(fragment.getContext(), TRAVEL_THRESHOLD_DP)));
        }
    }

    @Override
    public void onScrolled(@NonNull RecyclerView list, int dx, int dy) {
        if (dy == 0) {
            return;
        }
        if (dy > 0 != travel > 0) {
            travel = 0;
        }
        travel += dy;
        if (travel >= threshold) {
            host.setBarCollapsed(true);
            travel = 0;
        } else if (travel <= -threshold) {
            host.setBarCollapsed(false);
            travel = 0;
        }
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView list, int state) {
        if (state == RecyclerView.SCROLL_STATE_IDLE) {
            travel = 0;
        }
    }
}
