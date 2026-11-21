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
import com.sinoll.home.data.ApiClient;
import com.sinoll.home.data.Post;
import com.sinoll.home.data.PostRepository;
import com.sinoll.home.databinding.FragmentArticleBinding;
import com.sinoll.home.databinding.ItemArticleBinding;
import com.sinoll.home.ui.AppBarCollapse;
import com.sinoll.home.ui.ArticleCard;
import com.sinoll.home.ui.FeedState;
import com.sinoll.home.ui.PagedFeed;
import com.sinoll.home.ui.PostActionRouter;
import com.sinoll.home.ui.PostActions;
import com.sinoll.home.ui.PostFormat;
import com.sinoll.home.ui.compose.ComposeActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 图文 tab：博客和图集各按页取出，再按创建时间归并成一条时间线。
 * 两个源都是同序倒排、同页大小，所以逐页归并的结果就是全局时间倒序。
 */
public class ArticleFragment extends Fragment implements PagedFeed.Ui {

    private static final int PER_PAGE = 10;

    private static final Comparator<Post> TIMELINE = (left, right) -> {
        if (left.createdAtMillis != right.createdAtMillis) {
            return Long.compare(right.createdAtMillis, left.createdAtMillis);
        }
        return Integer.compare(right.id, left.id);
    };

    private FragmentArticleBinding binding;
    private FeedState state;
    private ArticleAdapter adapter;
    private PagedFeed paged;
    /** 编辑器改动过内容才重取。 */
    private int seenGeneration = ComposeActivity.contentGeneration;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentArticleBinding.inflate(inflater, container, false);
        state = new FeedState(binding.state, false);
        adapter = new ArticleAdapter(new PostActionRouter(requireContext()));
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        AppBarCollapse.attach(binding.recycler, this);
        binding.state.retry.setOnClickListener(view -> paged.retry());
        paged = new PagedFeed(binding.recycler, this::load, this);
        paged.refresh();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (seenGeneration != ComposeActivity.contentGeneration) {
            seenGeneration = ComposeActivity.contentGeneration;
            paged.refresh();
        }
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
        new Merger(page, result).start();
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
        state.showMessage(R.string.state_empty_article);
    }

    @Override
    public void onFailed(String message) {
        state.showFailed(message);
    }

    @Override
    public void onContent() {
        state.hide();
    }

    /** 两个源都回到主线程后归并；任意一个失败就整页算失败，避免出现半条时间线。 */
    private class Merger implements PostRepository.PageCallback {

        private final Context context;
        private final int page;
        private final PagedFeed.Result result;
        private final List<Post> posts = new ArrayList<>();
        private int pending = 2;
        private boolean hasMore;
        private String error;

        Merger(int page, PagedFeed.Result result) {
            this.context = requireContext();
            this.page = page;
            this.result = result;
        }

        void start() {
            PostRepository.load(context, Post.TYPE_BLOG, page, PER_PAGE, this);
            PostRepository.load(context, Post.TYPE_IMAGES, page, PER_PAGE, this);
        }

        @Override
        public void onPage(List<Post> items, int current, int totalPages,
                           ApiClient.Response response) {
            if (binding == null) {
                return;
            }
            if (error != null) {
                finish();
                return;
            }
            if (!response.success) {
                error = PostFormat.message(context, response);
            } else {
                posts.addAll(items);
                hasMore |= current < totalPages;
            }
            finish();
        }

        private void finish() {
            if (--pending > 0) {
                return;
            }
            if (error != null) {
                result.onResult(0, false, error);
                return;
            }
            Collections.sort(posts, TIMELINE);
            adapter.addAll(posts);
            result.onResult(posts.size(), hasMore, null);
        }
    }

    private static class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.VH> {

        private final PostActions actions;
        private final List<Post> items = new ArrayList<>();

        ArticleAdapter(PostActions actions) {
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
            return new VH(ItemArticleBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            new ArticleCard(holder.binding, actions).bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemArticleBinding binding;

            VH(ItemArticleBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
