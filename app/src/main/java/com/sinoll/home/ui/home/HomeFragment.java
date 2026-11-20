package com.sinoll.home.ui.home;

import android.os.Bundle;
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

    private FragmentHomeBinding binding;

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

        // 恢复现场时页码可能是动态，这条 post 跑在恢复之后，所有子页都保持标题栏收起态
        binding.getRoot().post(() -> {
            if (binding == null) {
                return;
            }
            applyVideoImmersive();
            collapseBarForListPage();
        });
        return binding.getRoot();
    }

    private final ViewPager2.OnPageChangeCallback pageChangeCallback =
            new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    if (binding == null) {
                        return;
                    }
                    applyVideoImmersive();
                    collapseBarForListPage();
                }
            };

    private void collapseBarForListPage() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).collapseBarForListPage();
        }
    }

    private void applyVideoImmersive() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setVideoImmersive(false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.pager.unregisterOnPageChangeCallback(pageChangeCallback);
        applyVideoImmersive();
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
