package com.sinoll.home.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.sinoll.home.R;
import com.sinoll.home.data.Comment;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.databinding.ItemCommentBinding;

import java.util.List;
import java.util.function.Consumer;

/**
 * 评论行渲染。详情页整页列表与视频 tab 的半屏评论面板共用同一套行样式，
 * 顶级评论带头像、内嵌回复缩进不再重复头像，正文里的 @ 依旧可点。
 */
public final class CommentList {

    public static void render(LayoutInflater inflater, LinearLayout container,
                              List<Comment> comments, Consumer<String> onMention,
                              Consumer<Comment> onReply) {
        container.removeAllViews();
        for (Comment comment : comments) {
            container.addView(row(inflater, container, comment, false, onMention, onReply));
        }
    }

    public static View row(LayoutInflater inflater, ViewGroup parent, Comment comment,
                           boolean reply, Consumer<String> onMention,
                           Consumer<Comment> onReply) {
        ItemCommentBinding binding = ItemCommentBinding.inflate(inflater, parent, false);
        binding.avatarWrap.setVisibility(reply ? View.GONE : View.VISIBLE);
        binding.replies.setVisibility(View.GONE);
        if (!reply) {
            binding.avatar.setText(PostFormat.initial(comment.authorName));
            binding.avatar.setBackgroundResource(PostFormat.avatarFallback(comment.id));
            ImageLoader.intoCircle(binding.avatarImage, comment.authorAvatarUrl,
                    PostFormat.dp(parent.getContext(), 32));
        }
        boolean hasTarget = reply && comment.replyToName != null && !comment.replyToName.isEmpty();
        binding.name.setText(hasTarget
                ? parent.getContext().getString(
                        R.string.comment_reply_to, comment.authorName, comment.replyToName)
                : comment.authorName);
        binding.time.setText(PostFormat.time(parent.getContext(), comment.createdAtMillis));
        if (comment.hidden) {
            binding.content.setText(R.string.comment_hidden);
            binding.content.setTextColor(
                    ContextCompat.getColor(parent.getContext(), R.color.ink_faint));
        } else {
            MentionFormat.bind(binding.content, comment.content, onMention);
        }
        if (!reply) {
            for (Comment child : comment.replies) {
                binding.replies.addView(
                        row(inflater, binding.replies, child, true, onMention, onReply));
            }
            binding.replies.setVisibility(comment.replies.isEmpty() ? View.GONE : View.VISIBLE);
            if (comment.hasMoreReplies && comment.replyCount > comment.replies.size()) {
                binding.moreReplies.setVisibility(View.VISIBLE);
                binding.moreReplies.setText(parent.getContext().getString(
                        R.string.comment_more_replies, comment.replyCount));
            }
        }
        // 被隐藏的节点只剩占位文字，回复它没有意义
        if (onReply != null && !comment.hidden) {
            binding.reply.setVisibility(View.VISIBLE);
            View.OnClickListener listener = view -> onReply.accept(comment);
            binding.reply.setOnClickListener(listener);
            binding.getRoot().setOnClickListener(listener);
        } else {
            binding.reply.setVisibility(View.GONE);
        }
        return binding.getRoot();
    }

    private CommentList() {
    }
}
