package com.sinoll.home.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import com.sinoll.home.R;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Post;
import com.sinoll.home.databinding.ItemArticleBinding;

/**
 * 图文卡片，博客与图集共用：博客多一行分类，图集多一个张数角标。
 */
public final class ArticleCard {

    private final ItemArticleBinding binding;
    private final Context context;
    private final PostActions actions;

    public ArticleCard(ItemArticleBinding binding, PostActions actions) {
        this.binding = binding;
        this.context = binding.getRoot().getContext();
        this.actions = actions;
    }

    public void bind(Post post) {
        bindCover(post);
        binding.title.setText(post.title);
        bindSummary(post);
        binding.categories.setItems(post.categories, R.drawable.bg_chip_cate, R.color.ink_muted);
        binding.tags.setItems(PostFormat.withHash(post.tags),
                R.drawable.bg_chip_tag, R.color.brand_primary);
        bindAuthor(post);
        bindActions(post);
    }

    private void bindCover(Post post) {
        if (post.isImages()) {
            binding.badge.setVisibility(View.VISIBLE);
            binding.badge.setText(context.getString(R.string.badge_image_count, post.imageCount));
        } else {
            binding.badge.setVisibility(View.GONE);
        }
        binding.cover.setBackgroundResource(PostFormat.coverFallback(post.id));
        ImageLoader.into(binding.cover, post.coverUrl, PostFormat.dp(context, 320));
    }

    private void bindAuthor(Post post) {
        binding.avatar.setText(PostFormat.initial(post.authorName));
        binding.avatar.setBackgroundResource(PostFormat.avatarFallback(post.id));
        ImageLoader.intoCircle(binding.avatarImage, post.authorAvatarUrl,
                PostFormat.dp(context, 20));
        binding.author.setText(post.authorName);
        binding.time.setText(PostFormat.time(context, post.createdAtMillis));
    }

    private void bindActions(Post post) {
        // 心形常在不显得缺项，数字只在真有人赞过时出现
        binding.likes.setText(PostFormat.count(context, post.likeCount));
        binding.likes.setVisibility(post.likeCount > 0 ? View.VISIBLE : View.GONE);
        renderCommentCount(post);
        binding.comment.setOnClickListener(
                view -> actions.onComment(post, () -> renderCommentCount(post)));
        binding.share.setVisibility(post.isShareable() ? View.VISIBLE : View.GONE);
        binding.share.setOnClickListener(view -> actions.onShare(post));
        binding.getRoot().setOnClickListener(view -> actions.onOpen(post));
    }

    /** 图集的说明首行往往就是标题，重复了就不再占一行。 */
    private void bindSummary(Post post) {
        String detail = post.body == null ? "" : post.body.trim();
        int lineEnd = detail.indexOf('\n');
        String firstLine = (lineEnd < 0 ? detail : detail.substring(0, lineEnd)).trim();
        if (TextUtils.equals(firstLine, post.title)) {
            detail = lineEnd < 0 ? "" : detail.substring(lineEnd + 1).trim();
        }
        MentionFormat.bind(binding.summary, detail, actions::onMention);
        binding.summary.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 和点赞一个规矩：没人评论时不留一个 0 在按钮边上。 */
    private void renderCommentCount(Post post) {
        binding.commentCount.setText(PostFormat.count(context, post.commentCount));
        binding.commentCount.setVisibility(
                post.commentCount > 0 ? View.VISIBLE : View.GONE);
    }
}
