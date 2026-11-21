package com.sinoll.home.ui.home;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.sinoll.home.R;
import com.sinoll.home.data.Comment;
import com.sinoll.home.data.CommentApi;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Post;
import com.sinoll.home.data.PostRepository;
import com.sinoll.home.data.VideoCache;
import com.sinoll.home.databinding.FragmentVideoBinding;
import com.sinoll.home.databinding.ItemVideoBinding;
import com.sinoll.home.ui.AppBarCollapse;
import com.sinoll.home.ui.CommentList;
import com.sinoll.home.ui.FeedState;
import com.sinoll.home.ui.PagedFeed;
import com.sinoll.home.ui.PostActionRouter;
import com.sinoll.home.ui.PostActions;
import com.sinoll.home.ui.PostFormat;
import com.sinoll.home.ui.compose.ComposeActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 视频 tab：GET /short_video 的竖滑流，落位的那条带声音自动播，其余只留封面。
 */
public class VideoFragment extends Fragment implements PagedFeed.Ui {

    private static final int PER_PAGE = 10;
    /** 落位之后往后预取的视频条数，和 VideoCache 的保留上限对齐。 */
    private static final int PREFETCH = 8;

    private FragmentVideoBinding binding;
    private FeedState state;
    private VideoAdapter adapter;
    private PagedFeed paged;
    private PagerSnapHelper snapHelper;
    private PostActions actions;

    private boolean muted;
    private int snapped = -1;
    /** 半屏评论面板：打开时视频缩到上半屏，评论占下半屏。 */
    private boolean panelOpen;
    private Post commentPost;
    /** 编辑器改动过内容才重取：这条流会跳回第一条，随便刷新代价太大。 */
    private int seenGeneration = ComposeActivity.contentGeneration;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentVideoBinding.inflate(inflater, container, false);
        state = new FeedState(binding.state, true);
        actions = new PostActionRouter(requireContext());
        adapter = new VideoAdapter();
        binding.recycler.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        binding.recycler.setAdapter(adapter);
        AppBarCollapse.attach(binding.recycler, this);
        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(binding.recycler);
        binding.recycler.addOnScrollListener(settleListener);
        binding.state.retry.setOnClickListener(view -> paged.retry());
        binding.commentClose.setOnClickListener(view -> closeComments());
        binding.commentWrite.setOnClickListener(view -> composeForPanel());
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
            return;
        }
        VideoHolder current = currentHolder();
        if (current != null) {
            current.resume();
        }
    }

    @Override
    public void onPause() {
        VideoHolder current = currentHolder();
        if (current != null) {
            current.pause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopAll();
        super.onDestroyView();
        binding = null;
        state = null;
        adapter = null;
        paged = null;
        snapHelper = null;
        actions = null;
    }

    private final RecyclerView.OnScrollListener settleListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recycler, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                playSnapped();
            }
        }
    };

    private void load(int page, PagedFeed.Result result) {
        Context context = getContext();
        PostRepository.load(context, Post.TYPE_VIDEO, page, PER_PAGE,
                (posts, current, totalPages, response) -> {
                    if (binding == null) {
                        return;
                    }
                    if (!response.success) {
                        result.onResult(0, false, PostFormat.message(context, response));
                        return;
                    }
                    adapter.addAll(posts);
                    if (snapped < 0) {
                        // 首次进 tab 没有滚动事件，落位那条也得自己起播
                        binding.recycler.post(this::playSnapped);
                    }
                    prefillFrom(Math.max(snapped, 0));
                    result.onResult(posts.size(), current < totalPages, null);
                });
    }

    /** 吸附到位后换播放器：上一条彻底释放，这一条从头播。 */
    private void playSnapped() {
        if (binding == null || snapHelper == null) {
            return;
        }
        View snap = snapHelper.findSnapView(binding.recycler.getLayoutManager());
        if (snap == null) {
            return;
        }
        int position = binding.recycler.getChildAdapterPosition(snap);
        if (position == RecyclerView.NO_POSITION || position == snapped) {
            return;
        }
        VideoHolder previous = currentHolder();
        snapped = position;
        prefillFrom(position);
        if (previous != null) {
            previous.stop();
        }
        VideoHolder current = currentHolder();
        if (current != null) {
            current.play();
        }
        if (panelOpen) {
            commentPost = adapter.postAt(position);
            loadPanelComments();
        }
    }

    /** 从落位那条往后排一队，边看边下，滑到下一条时文件多半已经在机器上。 */
    private void prefillFrom(int position) {
        Context context = getContext();
        if (context != null && adapter != null) {
            VideoCache.prefill(context, adapter.urlWindow(position, PREFETCH));
        }
    }

    private VideoHolder currentHolder() {
        if (binding == null || snapped < 0) {
            return null;
        }
        RecyclerView.ViewHolder holder =
                binding.recycler.findViewHolderForAdapterPosition(snapped);
        return holder instanceof VideoHolder ? (VideoHolder) holder : null;
    }

    private void stopAll() {
        if (binding == null) {
            return;
        }
        forEachHolder(holder -> holder.stop());
    }

    private void refreshMuteIcons() {
        forEachHolder(VideoHolder::applyMute);
    }

    private void forEachHolder(Consumer<VideoHolder> action) {
        if (binding == null) {
            return;
        }
        for (int i = 0; i < binding.recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = binding.recycler
                    .getChildViewHolder(binding.recycler.getChildAt(i));
            if (holder instanceof VideoHolder) {
                action.accept((VideoHolder) holder);
            }
        }
    }

    /** 评论图标开半屏评论：视频缩到上半屏，评论占下半屏，点关闭或点上半屏视频收起。 */
    private void openComments(Post post) {
        if (binding == null) {
            return;
        }
        commentPost = post;
        panelOpen = true;
        binding.commentPanel.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams params = binding.commentPanel.getLayoutParams();
        params.height = binding.getRoot().getHeight() / 2;
        binding.commentPanel.setLayoutParams(params);
        binding.commentTitle.setText(getString(R.string.comment_title_count, post.commentCount));
        applyCompact();
        loadPanelComments();
    }

    private void closeComments() {
        if (binding == null || !panelOpen) {
            return;
        }
        panelOpen = false;
        commentPost = null;
        binding.commentPanel.setVisibility(View.GONE);
        applyCompact();
    }

    private void applyCompact() {
        forEachHolder(holder -> holder.setCompact(panelOpen));
    }

    private void loadPanelComments() {
        if (binding == null || commentPost == null) {
            return;
        }
        binding.commentList.removeAllViews();
        binding.commentEmpty.setVisibility(View.GONE);
        if (commentPost.commentCount <= 0) {
            binding.commentEmpty.setVisibility(View.VISIBLE);
            return;
        }
        int postId = commentPost.id;
        CommentApi.list(requireContext(), postId, response -> {
            if (binding == null || commentPost == null || commentPost.id != postId) {
                return;
            }
            List<Comment> comments = response.success
                    ? Comment.list(response.data) : Collections.emptyList();
            CommentList.render(getLayoutInflater(), binding.commentList,
                    comments, actions::onMention, this::replyInPanel);
            binding.commentEmpty.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    /** 面板底部的输入条复用评论弹窗与登录兜底，发完就地刷计数和列表。 */
    private void composeForPanel() {
        if (commentPost == null) {
            return;
        }
        Post target = commentPost;
        actions.onComment(target, () -> refreshPanel(target));
    }

    /** 点某条评论的「回复」：弹窗里带上被回复的人，发完同样就地刷新。 */
    private void replyInPanel(Comment comment) {
        if (commentPost == null) {
            return;
        }
        Post target = commentPost;
        actions.onReply(target, comment, () -> refreshPanel(target));
    }

    private void refreshPanel(Post target) {
        if (binding == null) {
            return;
        }
        binding.commentTitle.setText(
                getString(R.string.comment_title_count, target.commentCount));
        VideoHolder holder = holderFor(target);
        if (holder != null) {
            holder.renderCommentCount(target);
        }
        loadPanelComments();
    }

    private VideoHolder holderFor(Post post) {
        if (binding == null) {
            return null;
        }
        for (int i = 0; i < binding.recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = binding.recycler
                    .getChildViewHolder(binding.recycler.getChildAt(i));
            if (holder instanceof VideoHolder && ((VideoHolder) holder).post == post) {
                return (VideoHolder) holder;
            }
        }
        return null;
    }

    @Override
    public void onReset() {
        stopAll();
        snapped = -1;
        adapter.clear();
    }

    @Override
    public void onFirstLoading() {
        state.showLoading(R.string.state_loading);
    }

    @Override
    public void onEmpty() {
        state.showMessage(R.string.state_empty_video);
    }

    @Override
    public void onFailed(String message) {
        state.showFailed(message);
    }

    @Override
    public void onContent() {
        state.hide();
        binding.recycler.post(this::playSnapped);
    }

    private class VideoAdapter extends RecyclerView.Adapter<VideoHolder> {

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
        public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VideoHolder(ItemVideoBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
            holder.bind(items.get(position), position == snapped);
        }

        @Override
        public void onViewRecycled(@NonNull VideoHolder holder) {
            holder.stop();
            super.onViewRecycled(holder);
        }

        List<String> urlWindow(int from, int size) {
            List<String> urls = new ArrayList<>();
            for (int i = Math.max(0, from); i < items.size() && urls.size() < size; i++) {
                String url = items.get(i).videoUrl;
                if (url != null) {
                    urls.add(url);
                }
            }
            return urls;
        }

        Post postAt(int position) {
            return position >= 0 && position < items.size() ? items.get(position) : null;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private class VideoHolder extends RecyclerView.ViewHolder {

        private final ItemVideoBinding item;
        private Post post;
        private MediaPlayer player;
        private boolean prepared;

        VideoHolder(ItemVideoBinding item) {
            super(item.getRoot());
            this.item = item;
            item.video.setOnPreparedListener(mediaPlayer -> {
                player = mediaPlayer;
                mediaPlayer.setLooping(true);
                item.video.setVideoSize(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
                applyMute();
                if (prepared) {
                    mediaPlayer.start();
                } else {
                    mediaPlayer.pause();
                }
            });
            item.video.setOnInfoListener((mediaPlayer, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    item.cover.setVisibility(View.GONE);
                }
                return true;
            });
            item.video.setOnErrorListener((mediaPlayer, what, extra) -> true);
            item.getRoot().setOnClickListener(view -> {
                if (panelOpen) {
                    closeComments();
                } else {
                    togglePlay();
                }
            });
            item.play.setOnClickListener(view -> togglePlay());
            item.mute.setOnClickListener(view -> toggleMute());
        }

        void bind(Post value, boolean shouldPlay) {
            stop();
            post = value;
            prepared = shouldPlay;
            Context context = item.getRoot().getContext();
            item.uploader.setText(context.getString(R.string.nav_user_handle, value.authorName));
            item.title.setText(value.title);
            item.tags.setItems(PostFormat.withHash(value.tags),
                    R.drawable.bg_chip_on_dark, R.color.on_dark);
            String duration = PostFormat.duration(value.durationSeconds);
            item.duration.setText(duration);
            item.duration.setVisibility(duration.isEmpty() ? View.GONE : View.VISIBLE);
            item.avatar.setText(PostFormat.initial(value.authorName));
            item.avatar.setBackgroundResource(PostFormat.avatarFallback(value.id));
            ImageLoader.intoCircle(item.avatarImage, value.authorAvatarUrl,
                    PostFormat.dp(context, 44));
            item.like.setText(PostFormat.count(context, value.likeCount));
            item.views.setText(PostFormat.count(context, value.views));
            renderCommentCount(value);
            item.comment.setOnClickListener(view -> openComments(value));
            item.share.setVisibility(value.isShareable() ? View.VISIBLE : View.GONE);
            item.share.setOnClickListener(view -> actions.onShare(value));
            item.cover.setVisibility(View.VISIBLE);
            item.cover.setBackgroundResource(PostFormat.coverFallback(value.id));
            item.cover.setScaleType(value.vertical
                    ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            ImageLoader.into(item.cover, value.coverUrl, PostFormat.dp(context, 400));
            item.video.setCenterCrop(value.vertical);
            applyMute();
            setCompact(panelOpen);
            if (shouldPlay) {
                play();
            }
        }

        /** 半屏评论时视频区压到上半屏：竖版缩放，横版完整居中上移。 */
        void setCompact(boolean compact) {
            int height = binding == null ? 0 : binding.getRoot().getHeight();
            ViewGroup.LayoutParams params = item.videoArea.getLayoutParams();
            int target = compact && height > 0
                    ? height / 2 : ViewGroup.LayoutParams.MATCH_PARENT;
            if (params.height != target) {
                params.height = target;
                item.videoArea.setLayoutParams(params);
            }
            boolean crop = post != null && post.vertical && !compact;
            item.video.setCenterCrop(crop);
            item.cover.setScaleType(crop
                    ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            item.cover.setVisibility(compact && prepared ? View.GONE : View.VISIBLE);
            int overlay = compact ? View.GONE : View.VISIBLE;
            item.scrim.setVisibility(overlay);
            item.actionsRail.setVisibility(overlay);
            item.infoBlock.setVisibility(overlay);
        }

        /** 右栏的计数一贯常驻，评论数也一样，没人评论就停在 0。 */
        private void renderCommentCount(Post value) {
            item.comment.setText(PostFormat.count(
                    item.getRoot().getContext(), value.commentCount));
        }

        /** 点视频本体：没起播就起播，播着就暂停，暂停了接着播。 */
        void togglePlay() {
            if (player == null) {
                play();
            } else if (item.video.isPlaying()) {
                pause();
            } else {
                resume();
            }
        }

        void play() {
            prepared = true;
            if (post == null || post.videoUrl == null) {
                return;
            }
            String local = VideoCache.localPath(item.getRoot().getContext(), post.videoUrl);
            item.video.setVideoURI(local != null
                    ? Uri.fromFile(new File(local)) : Uri.parse(post.videoUrl));
            item.video.start();
            item.play.setVisibility(View.GONE);
        }

        void pause() {
            prepared = false;
            item.video.pause();
            item.play.setVisibility(View.VISIBLE);
        }

        void resume() {
            prepared = true;
            item.video.start();
            item.play.setVisibility(View.GONE);
        }

        void stop() {
            prepared = false;
            player = null;
            item.video.stopPlayback();
            item.cover.setVisibility(View.VISIBLE);
            item.play.setVisibility(View.GONE);
        }

        private void toggleMute() {
            muted = !muted;
            refreshMuteIcons();
        }

        /** 静音状态同时作用在图标和当前播放器上，相邻卡片也要跟着换图标。 */
        void applyMute() {
            float volume = muted ? 0f : 1f;
            if (player != null) {
                player.setVolume(volume, volume);
            }
            item.mute.setImageResource(muted ? R.drawable.ic_muted : R.drawable.ic_sound);
            item.mute.setContentDescription(item.mute.getResources().getString(
                    muted ? R.string.action_unmute : R.string.action_mute));
        }
    }
}
