package com.sinoll.home.ui.mine;

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
import com.sinoll.home.data.AuthSession;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Post;
import com.sinoll.home.data.PostRepository;
import com.sinoll.home.databinding.FragmentMineBinding;
import com.sinoll.home.databinding.ItemSearchBinding;
import com.sinoll.home.ui.FeedState;
import com.sinoll.home.ui.PagedFeed;
import com.sinoll.home.ui.PostFormat;
import com.sinoll.home.ui.compose.ComposeActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 我的内容：自己发过的四类内容，草稿也在里面，点进去就是编辑器。
 * 四类各自分页，归并成一条时间线，所以每一页最多拿到 4 倍条数。
 */
public class MineFragment extends Fragment implements PagedFeed.Ui {

    private static final int PER_PAGE = 10;
    private static final String[] TYPES = {Post.TYPE_MOMENT, Post.TYPE_IMAGES,
            Post.TYPE_VIDEO, Post.TYPE_BLOG};

    private static final Comparator<Post> TIMELINE = (left, right) -> {
        if (left.createdAtMillis != right.createdAtMillis) {
            return Long.compare(right.createdAtMillis, left.createdAtMillis);
        }
        return Integer.compare(right.id, left.id);
    };

    private FragmentMineBinding binding;
    private FeedState state;
    private MineAdapter adapter;
    private PagedFeed paged;
    private boolean firstResume = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMineBinding.inflate(inflater, container, false);
        state = new FeedState(binding.state, false);
        adapter = new MineAdapter(this::edit);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
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

    /** 从编辑器回来重取一次：草稿可能刚发布，也可能整条被删了。 */
    @Override
    public void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        paged.refresh();
    }

    private void edit(Post post) {
        startActivity(ComposeActivity.editIntent(requireActivity(), post));
    }

    private void load(int page, PagedFeed.Result result) {
        if (!AuthSession.get(requireContext()).isSignedIn()) {
            result.onResult(0, false, null);
            return;
        }
        new Merger(page, result).start();
    }

    @Override
    public void onReset() {
        adapter.clear();
    }

    @Override
    public void onFirstLoading() {
        if (!AuthSession.get(requireContext()).isSignedIn()) {
            state.showMessage(R.string.state_need_sign_in);
            return;
        }
        state.showLoading(R.string.state_loading);
    }

    @Override
    public void onEmpty() {
        state.showMessage(AuthSession.get(requireContext()).isSignedIn()
                ? R.string.state_no_mine : R.string.state_need_sign_in);
    }

    @Override
    public void onFailed(String message) {
        state.showFailed(message);
    }

    @Override
    public void onContent() {
        state.hide();
    }

    /** 四类内容各自分页，逐页归并；任意一个失败就整页算失败，免得只看到半条时间线。 */
    private class Merger implements PostRepository.PageCallback {

        private final Context context;
        private final int page;
        private final PagedFeed.Result result;
        private final List<Post> posts = new ArrayList<>();
        private int pending = TYPES.length;
        private boolean hasMore;
        private String error;

        Merger(int page, PagedFeed.Result result) {
            this.context = requireContext();
            this.page = page;
            this.result = result;
        }

        void start() {
            String userId = AuthSession.get(context).userId();
            for (String type : TYPES) {
                PostRepository.loadByAuthor(context, type, userId, page, PER_PAGE, this);
            }
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

    private interface OpenPost {
        void open(Post post);
    }

    private static class MineAdapter extends RecyclerView.Adapter<MineAdapter.VH> {

        private final OpenPost open;
        private final List<Post> items = new ArrayList<>();

        MineAdapter(OpenPost open) {
            this.open = open;
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
            // 作者就是自己，那一栏空出来说「这条现在是什么状态」
            holder.binding.author.setText(stateLabel(context, post));
            holder.binding.time.setText(PostFormat.time(context, post.createdAtMillis));
            bindTexts(holder.binding, post);
            holder.binding.getRoot().setOnClickListener(view -> {
                int clicked = holder.getBindingAdapterPosition();
                if (clicked != RecyclerView.NO_POSITION) {
                    open.open(items.get(clicked));
                }
            });
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
            binding.summary.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        }

        private String stateLabel(Context context, Post post) {
            if (post.status == 0) {
                return context.getString(R.string.label_draft);
            }
            if (post.visibility == 0) {
                return context.getString(R.string.label_private);
            }
            if (post.visibility == 1) {
                return context.getString(R.string.label_members);
            }
            return context.getString(R.string.vis_public);
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
