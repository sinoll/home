package com.sinoll.home.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayoutMediator;
import com.sinoll.home.MainActivity;
import com.sinoll.home.R;
import com.sinoll.home.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private static final int[] TAB_TITLES = {R.string.tab_feed, R.string.tab_video, R.string.tab_article};
    private static final int POSITION_VIDEO = 1;
    private static final long TABS_RESHOW_MS = 2200;

    private FragmentHomeBinding binding;
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideTabBarTask = this::hideTabBar;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        ViewPager2 pager = binding.pager;
        pager.setAdapter(new TabAdapter(this));
        new TabLayoutMediator(binding.tabLayout, pager,
                (tab, position) -> tab.setText(TAB_TITLES[position])).attach();
        pager.setCurrentItem(POSITION_VIDEO, false);
        pager.registerOnPageChangeCallback(pageChangeCallback);

        // 恢复现场时页码可能是动态，这条 post 跑在恢复之后，不能再无条件把标签条和标题栏收掉
        binding.getRoot().post(() -> {
            if (binding == null) {
                return;
            }
            if (binding.pager.getCurrentItem() == POSITION_VIDEO) {
                hideTabBar();
                applyVideoImmersive(true);
            } else {
                collapseBarForListPage();
            }
        });
        return binding.getRoot();
    }

    private final ViewPager2.OnPageChangeCallback pageChangeCallback =
            new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageScrollStateChanged(int state) {
                    if (state == ViewPager2.SCROLL_STATE_DRAGGING && binding != null) {
                        hideHandler.removeCallbacks(hideTabBarTask);
                        showTabBar();
                        revealChromeTemporarily();
                    }
                }

                @Override
                public void onPageSelected(int position) {
                    if (binding == null) {
                        return;
                    }
                    if (position == POSITION_VIDEO) {
                        hideHandler.removeCallbacks(hideTabBarTask);
                        hideHandler.postDelayed(hideTabBarTask, TABS_RESHOW_MS);
                    } else {
                        hideHandler.removeCallbacks(hideTabBarTask);
                        showTabBar();
                    }
                    applyVideoImmersive(position == POSITION_VIDEO);
                    if (position != POSITION_VIDEO) {
                        // 动态/图文进来不带标题栏，用户下滑时它才出现
                        collapseBarForListPage();
                    }
                }
            };

    private void collapseBarForListPage() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).collapseBarForListPage();
        }
    }

    private void hideTabBar() {
        if (binding == null || binding.tabLayout.getAlpha() == 0f) {
            return;
        }
        binding.tabLayout.animate()
                .alpha(0f)
                .translationY(-binding.tabLayout.getHeight())
                .setDuration(220)
                .start();
    }

    private void showTabBar() {
        if (binding == null) {
            return;
        }
        binding.tabLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .start();
    }

    public void revealTabsTemporarily() {
        if (binding == null) {
            return;
        }
        hideHandler.removeCallbacks(hideTabBarTask);
        showTabBar();
        hideHandler.postDelayed(hideTabBarTask, TABS_RESHOW_MS);
    }

    private void revealChromeTemporarily() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).revealChromeTemporarily();
        }
    }

    private void applyVideoImmersive(boolean videoPage) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setVideoImmersive(videoPage);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        hideHandler.removeCallbacks(hideTabBarTask);
        binding.pager.unregisterOnPageChangeCallback(pageChangeCallback);
        applyVideoImmersive(false);
        binding = null;
    }

    private static class TabAdapter extends FragmentStateAdapter {

        TabAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new FeedFragment();
                case 2:
                    return new ArticleFragment();
                default:
                    return new VideoFragment();
            }
        }

        @Override
        public int getItemCount() {
            return TAB_TITLES.length;
        }
    }
}
