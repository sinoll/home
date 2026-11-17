package com.sinoll.home.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sinoll.home.R;
import com.sinoll.home.data.Post;
import com.sinoll.home.data.PostRepository;
import com.sinoll.home.databinding.FragmentFeedBinding;
import com.sinoll.home.databinding.ItemFeedBinding;
import com.sinoll.home.ui.AppBarCollapse;
import com.sinoll.home.ui.FeedCard;
import com.sinoll.home.ui.FeedState;
import com.sinoll.home.ui.PagedFeed;
import com.sinoll.home.ui.PostActionRouter;
import com.sinoll.home.ui.PostActions;
import com.sinoll.home.ui.PostFormat;

import java.util.ArrayList;
import java.util.List;

/** 动态 tab：GET /moment 的无限滚动列表。 */
public class FeedFragment extends Fragment implements PagedFeed.Ui {

    private static final int PER_PAGE = 20;

    private FragmentFeedBinding binding;
    private FeedState state;
    private FeedAdapter adapter;
    private PagedFeed paged;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFeedBinding.inflate(inflater, container, false);
        state = new FeedState(binding.state, false);
        adapter = new FeedAdapter(new PostActionRouter(this));
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        AppBarCollapse.attach(binding.recycler, this);
        binding.state.retry.setOnClickListener(view -> paged.retry());
        paged = new PagedFeed(binding.recycler, this::load, this);
        paged.refresh();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        state = null;
        adapter = null;
        paged = null;
    }

    private void load(int page, PagedFeed.Result result) {
        Context context = getContext();
        PostRepository.load(context, Post.TYPE_MOMENT, page, PER_PAGE,
                (posts, current, totalPages, response) -> {
                    if (binding == null) {
                        return;
                    }
                    if (!response.success) {
                        result.onResult(0, false, PostFormat.message(context, response));
                        return;
                    }
                    adapter.addAll(posts);
                    result.onResult(posts.size(), current < totalPages, null);
                });
    }

    @Override
    public void onReset() {
        adapter.clear();
    }

    @Override
    public void onFirstLoading() {
        state.showLoading(R.string.state_loading);
    }

    @Override
    public void onEmpty() {
        state.showMessage(R.string.state_empty_feed);
    }

    @Override
    public void onFailed(String message) {
        state.showFailed(message);
    }

    @Override
    public void onContent() {
        state.hide();
    }

    private static class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

        private final PostActions actions;
        private final List<Post> items = new ArrayList<>();

        FeedAdapter(PostActions actions) {
            this.actions = actions;
        }

        void addAll(List<Post> posts) {
            int start = items.size();
            items.addAll(posts);
            notifyItemRangeInserted(start, posts.size());
        }

        void clear() {
            items.clear();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemFeedBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            new FeedCard(holder.binding, actions).bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemFeedBinding binding;

            VH(ItemFeedBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
