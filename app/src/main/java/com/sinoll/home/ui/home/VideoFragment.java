package com.sinoll.home.ui.home;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.sinoll.home.MainActivity;
import com.sinoll.home.R;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Post;
import com.sinoll.home.data.PostRepository;
import com.sinoll.home.data.VideoCache;
import com.sinoll.home.databinding.FragmentVideoBinding;
import com.sinoll.home.databinding.ItemVideoBinding;
import com.sinoll.home.ui.FeedState;
import com.sinoll.home.ui.PagedFeed;
import com.sinoll.home.ui.PostActionRouter;
import com.sinoll.home.ui.PostActions;
import com.sinoll.home.ui.PostFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 视频 tab：GET /short_video 的竖滑流，落位的那条静音自动播，其余只留封面。
 */
public class VideoFragment extends Fragment implements PagedFeed.Ui {

    private static final int PER_PAGE = 10;
    private static final int TOP_ZONE_DP = 100;
    /** 落位之后往后预取的视频条数，和 VideoCache 的保留上限对齐。 */
    private static final int PREFETCH = 8;

    private FragmentVideoBinding binding;
    private FeedState state;
    private VideoAdapter adapter;
    private PagedFeed paged;
    private PagerSnapHelper snapHelper;
    private PostActions actions;

    private boolean muted = true;
    private int snapped = -1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentVideoBinding.inflate(inflater, container, false);
        state = new FeedState(binding.state, true);
        actions = new PostActionRouter(this);
        adapter = new VideoAdapter();
        binding.recycler.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        binding.recycler.setAdapter(adapter);
        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(binding.recycler);
        binding.recycler.addOnScrollListener(settleListener);
        binding.state.retry.setOnClickListener(view -> paged.retry());
        installTopTapReveal();
        paged = new PagedFeed(binding.recycler, this::load, this);
        paged.refresh();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
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

    private void installTopTapReveal() {
        float topZonePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                TOP_ZONE_DP, getResources().getDisplayMetrics());
        GestureDetector detector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        if (e.getRawY() > topZonePx) {
                            return false;
                        }
                        if (getParentFragment() instanceof HomeFragment) {
                            ((HomeFragment) getParentFragment()).revealTabsTemporarily();
                        }
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).revealChromeTemporarily();
                        }
                        return true;
                    }
                });
        binding.recycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                detector.onTouchEvent(e);
                return false;
            }
        });
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
                if (item.video.isPlaying()) {
                    pause();
                } else {
                    resume();
                }
            });
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
            item.comment.setOnClickListener(
                    view -> actions.onComment(value, () -> renderCommentCount(value)));
            item.share.setVisibility(value.isShareable() ? View.VISIBLE : View.GONE);
            item.share.setOnClickListener(view -> actions.onShare(value));
            item.cover.setVisibility(View.VISIBLE);
            item.cover.setBackgroundResource(PostFormat.coverFallback(value.id));
            item.cover.setScaleType(value.vertical
                    ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            ImageLoader.into(item.cover, value.coverUrl, PostFormat.dp(context, 400));
            item.video.setCenterCrop(value.vertical);
            applyMute();
            if (shouldPlay) {
                play();
            }
        }

        /** 右栏的计数一贯常驻，评论数也一样，没人评论就停在 0。 */
        private void renderCommentCount(Post value) {
            item.comment.setText(PostFormat.count(
                    item.getRoot().getContext(), value.commentCount));
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
