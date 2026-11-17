package com.sinoll.home.ui;

import android.content.Context;
import android.view.View;

import com.sinoll.home.R;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Post;
import com.sinoll.home.databinding.ItemFeedBinding;

/** 动态卡片：作者、相对时间、正文、配图、标签与点赞 / 阅读量，外加评论、分享入口。 */
public final class FeedCard {

    private final ItemFeedBinding binding;
    private final Context context;
    private final PostActions actions;

    public FeedCard(ItemFeedBinding binding, PostActions actions) {
        this.binding = binding;
        this.context = binding.getRoot().getContext();
        this.actions = actions;
    }

    public void bind(Post post) {
        bindAuthor(post);
        bindContent(post);
        bindCover(post);
        binding.tags.setItems(PostFormat.withHash(post.tags),
                R.drawable.bg_chip_tag, R.color.brand_primary);
        binding.like.setText(label(post.likeCount, R.string.label_like));
        binding.views.setText(label(post.views, R.string.label_views));
        renderCommentCount(post);
        binding.comment.setOnClickListener(
                view -> actions.onComment(post, () -> renderCommentCount(post)));
        binding.share.setVisibility(post.isShareable() ? View.VISIBLE : View.GONE);
        binding.share.setOnClickListener(view -> actions.onShare(post));
    }

    /** 零评论只留图标，数字等真有人评论了再占位。 */
    private void renderCommentCount(Post post) {
        binding.commentCount.setText(PostFormat.count(context, post.commentCount));
        binding.commentCount.setVisibility(
                post.commentCount > 0 ? View.VISIBLE : View.GONE);
    }

    private void bindAuthor(Post post) {
        binding.name.setText(post.authorName);
        binding.avatar.setText(PostFormat.initial(post.authorName));
        binding.avatar.setBackgroundResource(PostFormat.avatarFallback(post.id));
        binding.time.setText(PostFormat.time(context, post.createdAtMillis));
        ImageLoader.intoCircle(binding.avatarImage, post.authorAvatarUrl,
                PostFormat.dp(context, 40));
    }

    /** 纯图片动态的 text 只剩空白，不留一行高度。 */
    private void bindContent(Post post) {
        String text = post.body == null ? "" : post.body.trim();
        binding.content.setText(text);
        binding.content.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void bindCover(Post post) {
        if (post.coverUrl == null) {
            binding.imageWrap.setVisibility(View.GONE);
            return;
        }
        binding.imageWrap.setVisibility(View.VISIBLE);
        binding.image.setBackgroundResource(PostFormat.coverFallback(post.id));
        ImageLoader.into(binding.image, post.coverUrl, PostFormat.dp(context, 320));
    }

    private String label(int value, int fallbackRes) {
        if (value <= 0) {
            return context.getString(fallbackRes);
        }
        return PostFormat.count(context, value);
    }
}
