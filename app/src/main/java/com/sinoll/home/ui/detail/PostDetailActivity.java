package com.sinoll.home.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.sinoll.home.R;
import com.sinoll.home.data.ApiClient;
import com.sinoll.home.data.AuthSession;
import com.sinoll.home.data.Comment;
import com.sinoll.home.data.CommentApi;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Post;
import com.sinoll.home.data.PostApi;
import com.sinoll.home.data.PostDetail;
import com.sinoll.home.data.PostHtml;
import com.sinoll.home.databinding.ActivityDetailBinding;
import com.sinoll.home.databinding.ItemCommentBinding;
import com.sinoll.home.ui.ChipRow;
import com.sinoll.home.ui.CommentList;
import com.sinoll.home.ui.InlineVideo;
import com.sinoll.home.ui.MentionFormat;
import com.sinoll.home.ui.PostActionRouter;
import com.sinoll.home.ui.PostFormat;
import com.sinoll.home.ui.compose.ComposeActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内容详情：列表接口只给摘要，正文与图片清单要单独取。
 * 无权查看与已删除在服务端都回 404，这里也只说「看不了」，不猜原因。
 */
public final class PostDetailActivity extends AppCompatActivity {

    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_ID = "id";

    private ActivityDetailBinding binding;
    private final com.sinoll.home.ui.PostActions actions =
            new PostActionRouter(this);
    private Post post;
    private InlineVideo video;

    public static Intent intent(Context context, String type, int id) {
        Intent intent = new Intent(context, PostDetailActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_ID, id);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(view -> finish());
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 编辑或评论回来要能看到新内容，省掉手动重进
        if (post != null) {
            load();
        }
    }

    /** 退到后台不该继续出声，解码器也一起让出去。 */
    @Override
    protected void onPause() {
        super.onPause();
        releaseVideo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseVideo();
        binding = null;
    }

    private void releaseVideo() {
        if (video != null) {
            video.release();
            video = null;
        }
    }

    private void load() {
        String type = getIntent().getStringExtra(EXTRA_TYPE);
        int id = getIntent().getIntExtra(EXTRA_ID, 0);
        PostApi.detail(this, type, id, response -> {
            if (binding == null) {
                return;
            }
            if (!response.success) {
                showState(PostFormat.message(this, response), response.code == 404
                        ? R.string.state_detail_missing : 0);
                return;
            }
            hideState();
            render(PostDetail.fromJson(response.data));
        });
    }

    private void render(PostDetail detail) {
        post = detail.post;
        bindAuthor(post);
        bindTitle(detail);
        bindState(post);
        bindBlocks(detail);
        binding.tags.setItems(PostFormat.withHash(post.tags),
                R.drawable.bg_chip_tag, R.color.brand_primary);
        binding.comment.setOnClickListener(view ->
                actions.onComment(post, () -> {
                    renderCounts();
                    loadComments();
                }));
        renderCounts();
        loadComments();
        binding.share.setVisibility(post.isShareable() ? View.VISIBLE : View.GONE);
        binding.share.setOnClickListener(view -> actions.onShare(post));

        boolean mine = post.isMine(AuthSession.get(this).userId());
        binding.btnEdit.setVisibility(mine ? View.VISIBLE : View.GONE);
        binding.btnEdit.setOnClickListener(view ->
                startActivity(ComposeActivity.editIntent(this, post)));
        binding.publishDraft.setVisibility(mine && post.status == 0 ? View.VISIBLE : View.GONE);
        binding.publishDraft.setOnClickListener(view -> publishDraft());
    }

    /** 和卡片一个规矩：没人评论时只留文字，不挂一个 0。 */
    private void renderCounts() {
        binding.commentCount.setText(PostFormat.count(this, post.commentCount));
        binding.commentCount.setVisibility(post.commentCount > 0 ? View.VISIBLE : View.GONE);
    }

    /** 有评论才拉列表；公开的已发布内容游客也能读，取失败就当没有，不打扰正文。 */
    private void loadComments() {
        if (post.commentCount <= 0) {
            renderComments(Collections.emptyList());
            return;
        }
        CommentApi.list(this, post.id, response -> {
            if (binding == null) {
                return;
            }
            renderComments(response.success
                    ? Comment.list(response.data) : Collections.emptyList());
        });
    }

    private void renderComments(List<Comment> comments) {
        binding.commentList.removeAllViews();
        int visibility = comments.isEmpty() ? View.GONE : View.VISIBLE;
        binding.commentDivider.setVisibility(visibility);
        binding.commentTitle.setVisibility(visibility);
        binding.commentList.setVisibility(visibility);
        CommentList.render(getLayoutInflater(), binding.commentList, comments, actions::onMention);
    }

    private void bindAuthor(Post post) {
        binding.author.setText(post.authorName);
        binding.avatar.setText(PostFormat.initial(post.authorName));
        binding.avatar.setBackgroundResource(PostFormat.avatarFallback(post.id));
        binding.time.setText(PostFormat.time(this, post.createdAtMillis));
        ImageLoader.intoCircle(binding.avatarImage, post.authorAvatarUrl, dp(36));
    }

    private void bindTitle(PostDetail detail) {
        String title = detail.post.title;
        // 动态的标题由正文派生，再显示一遍只是重复
        boolean redundant = title == null || title.trim().isEmpty()
                || Post.TYPE_MOMENT.equals(detail.post.type);
        binding.title.setVisibility(redundant ? View.GONE : View.VISIBLE);
        binding.title.setText(title);
    }

    /** 草稿与非公开内容只在需要说明时占一行，公开内容不留多余标记。 */
    private void bindState(Post post) {
        List<String> labels = new ArrayList<>();
        if (post.status == 0) {
            labels.add(getString(R.string.label_draft));
        }
        if (post.visibility == 0) {
            labels.add(getString(R.string.label_private));
        } else if (post.visibility == 1) {
            labels.add(getString(R.string.label_members));
        }
        if (labels.isEmpty()) {
            binding.stateRow.setVisibility(View.GONE);
            return;
        }
        binding.stateRow.setVisibility(View.VISIBLE);
        ChipRow chips = new ChipRow(this, null);
        binding.stateRow.removeAllViews();
        binding.stateRow.addView(chips);
        chips.setItems(labels, R.drawable.bg_chip_cate, R.color.ink_muted);
    }

    private void bindBlocks(PostDetail detail) {
        binding.blocks.removeAllViews();
        releaseVideo();
        if (detail.post.isVideo()) {
            if (detail.post.videoUrl != null) {
                video = new InlineVideo(this, detail.post);
                addBlock(video);
            }
        } else if (detail.isImages()) {
            for (PostDetail.Image image : detail.images) {
                addImage(image.fileUrl, dp(720));
                addCaption(image.title, image.altText);
            }
            return;
        } else if (Post.TYPE_BLOG.equals(detail.post.type) && !detail.html.isEmpty()) {
            addBlock(newBodyView(detail.html));
            return;
        }
        for (PostHtml.Block block : PostHtml.blocks(detail.html)) {
            if (block.imageUrl != null) {
                addImage(block.imageUrl, dp(720));
            } else {
                addText(block.text);
            }
        }
    }

    private void addText(String text) {
        TextView view = new TextView(this);
        view.setLineSpacing(dp(4), 1f);
        view.setTextColor(ContextCompat.getColor(this, R.color.ink_body));
        view.setTextSize(15);
        MentionFormat.bind(view, text, actions::onMention);
        addBlock(view);
    }

    private void addCaption(String title, String alt) {
        StringBuilder caption = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) {
            caption.append(title.trim());
        }
        if (alt != null && !alt.trim().isEmpty()) {
            caption.append(caption.length() > 0 ? " · " : "").append(alt.trim());
        }
        if (caption.length() == 0) {
            return;
        }
        TextView view = new TextView(this);
        view.setText(caption);
        view.setTextColor(ContextCompat.getColor(this, R.color.ink_faint));
        view.setTextSize(12);
        addBlock(view);
    }

    private void addImage(String url, int maxSide) {
        ImageView view = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        view.setLayoutParams(params);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.into(view, url, maxSide);
        binding.blocks.addView(view);
    }

    private void addBlock(View view) {
        if (view.getLayoutParams() == null) {
            view.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        binding.blocks.addView(view);
    }

    /** 博客正文是带内联样式的净化 HTML，拆块渲染会丢光排版，交给 WebView 原样显示。 */
    private WebView newBodyView(String html) {
        WebView web = new WebView(this);
        web.getSettings().setAllowFileAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
                return true;
            }
        });
        web.loadDataWithBaseURL(ApiClient.BASE_URL + "/", styledHtml(html),
                "text/html", "utf-8", null);
        return web;
    }

    private static String styledHtml(String html) {
        return "<!doctype html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>body{margin:0;font:15px/1.75 sans-serif;color:#33333F;word-wrap:break-word}"
                + "p{margin:0 0 10px}"
                + "img{max-width:100%;height:auto;display:block;margin:8px 0;border-radius:8px}"
                + "</style></head><body>" + html + "</body></html>";
    }

    /** 草稿发布走一次部分更新，只提交 status，正文原样留着。 */
    private void publishDraft() {
        PostApi.updateStatus(this, post.type, post.id, 1, response -> {
            if (binding == null) {
                return;
            }
            if (!response.success) {
                Toast.makeText(this, PostFormat.message(this, response),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, R.string.compose_published, Toast.LENGTH_SHORT).show();
            ComposeActivity.contentGeneration++;
            load();
        });
    }

    private void showState(String message, int fallbackRes) {
        binding.message.setVisibility(View.VISIBLE);
        binding.message.setText(fallbackRes != 0 ? getString(fallbackRes) : message);
        binding.retry.setVisibility(View.VISIBLE);
        binding.retry.setOnClickListener(view -> load());
    }

    private void hideState() {
        binding.message.setVisibility(View.GONE);
        binding.retry.setVisibility(View.GONE);
    }

    private int dp(float value) {
        return PostFormat.dp(this, value);
    }
}
