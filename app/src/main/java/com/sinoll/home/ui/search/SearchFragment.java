package com.sinoll.home.ui.search;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sinoll.home.R;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Post;
import com.sinoll.home.data.PostRepository;
import com.sinoll.home.databinding.FragmentSearchBinding;
import com.sinoll.home.databinding.ItemSearchBinding;
import com.sinoll.home.ui.FeedState;
import com.sinoll.home.ui.PagedFeed;
import com.sinoll.home.ui.PostFormat;

import java.util.ArrayList;
import java.util.List;

/** 搜索结果页：GET /search 的分页列表，顶部按内容类型筛选。 */
public class SearchFragment extends Fragment implements PagedFeed.Ui {

    private static final int PER_PAGE = 20;

    private FragmentSearchBinding binding;
    private FeedState state;
    private SearchAdapter adapter;
    private PagedFeed paged;

    private String query = "";
    private String type = "";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        state = new FeedState(binding.state, false);
        adapter = new SearchAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.state.retry.setOnClickListener(view -> paged.retry());
        paged = new PagedFeed(binding.recycler, this::load, this);
        bindFilters();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        SearchViewModel model =
                new ViewModelProvider(requireActivity()).get(SearchViewModel.class);
        if (model.query().getValue() == null) {
            // 进程重建后 activity 作用域的状态是空的，用导航参数把这次的关键词补回来
            Bundle args = getArguments();
            model.setQuery(args == null
                    ? "" : args.getString(SearchViewModel.ARG_QUERY, ""));
        }
        // 在结果页里再搜一次时页面不重建，只能靠这份状态把新关键词递进来
        model.query().observe(getViewLifecycleOwner(), this::search);
    }

    /** 每个筛选条上的 tag 就是接口的 type，空串代表不限类型。 */
    private void bindFilters() {
        for (int i = 0; i < binding.filters.getChildCount(); i++) {
            View chip = binding.filters.getChildAt(i);
            if (chip.getTag() == null) {
                chip.setTag("");
            }
            chip.setOnClickListener(view -> pickType((String) view.getTag()));
        }
        pickType("");
    }

    private void pickType(String value) {
        type = value;
        for (int i = 0; i < binding.filters.getChildCount(); i++) {
            TextView chip = (TextView) binding.filters.getChildAt(i);
            boolean selected = value.equals(chip.getTag());
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_chip_filter_on : R.drawable.bg_chip_filter);
            chip.setTextColor(color(selected ? R.color.brand_on_primary : R.color.ink_muted));
        }
        if (query.isEmpty()) {
            return;
        }
        paged.refresh();
    }

    private void search(String value) {
        query = value == null ? "" : value.trim();
        if (query.isEmpty()) {
            adapter.clear();
            state.showMessage(R.string.state_search_prompt);
            return;
        }
        paged.refresh();
    }

    private void load(int page, PagedFeed.Result result) {
        Context context = getContext();
        PostRepository.search(context, query, type, page, PER_PAGE,
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
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        state = null;
        adapter = null;
        paged = null;
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
        state.showMessage(R.string.state_empty_search);
    }

    @Override
    public void onFailed(String message) {
        state.showFailed(message);
    }

    @Override
    public void onContent() {
        state.hide();
    }

    private int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }

    private static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> {

        private final List<Post> items = new ArrayList<>();

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
            return new VH(ItemSearchBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Context context = holder.binding.getRoot().getContext();
            Post post = items.get(position);
            holder.binding.type.setText(PostFormat.typeLabel(context, post));
            holder.binding.cover.setBackgroundResource(PostFormat.coverFallback(post.id));
            ImageLoader.into(holder.binding.cover, post.coverUrl, PostFormat.dp(context, 92));
            holder.binding.author.setText(post.authorName);
            holder.binding.time.setText(PostFormat.time(context, post.createdAtMillis));
            bindTexts(holder.binding, post);
        }

        /** 动态的标题就是正文截断，图集的说明首行往往重复标题，都只留一份。 */
        private void bindTexts(ItemSearchBinding binding, Post post) {
            String detail = post.body == null ? "" : post.body.trim();
            String title = post.title == null ? "" : post.title.trim();
            if (title.isEmpty()) {
                int lineEnd = detail.indexOf('\n');
                title = lineEnd < 0 ? detail : detail.substring(0, lineEnd).trim();
                detail = lineEnd < 0 ? "" : detail.substring(lineEnd + 1).trim();
            } else if (detail.startsWith(title)) {
                detail = detail.substring(title.length()).trim();
            }
            binding.title.setText(title);
            binding.summary.setText(detail);
            binding.summary.setVisibility(
                    detail.isEmpty() ? View.GONE : View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemSearchBinding binding;

            VH(ItemSearchBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
