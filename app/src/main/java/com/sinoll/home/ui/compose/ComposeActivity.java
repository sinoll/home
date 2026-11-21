package com.sinoll.home.ui.compose;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.sinoll.home.R;
import com.sinoll.home.data.ApiClient;
import com.sinoll.home.data.Category;
import com.sinoll.home.data.CategoryApi;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Post;
import com.sinoll.home.data.PostApi;
import com.sinoll.home.data.PostDetail;
import com.sinoll.home.data.PostHtml;
import com.sinoll.home.data.UploadApi;
import com.sinoll.home.data.UploadResult;
import com.sinoll.home.databinding.ActivityComposeBinding;
import com.sinoll.home.ui.PostFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 四类内容的发布与修改共用一套骨架：标题、正文或文案、媒体、可见性、草稿或发布。
 * 差别只在媒体那一段——动态和图集是九宫格，短视频是一段视频加封面，博客只有可选封面。
 */
public final class ComposeActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_ID = "id";

    /** 发布、修改、删除成功后自增；列表页回前台时比对它，决定要不要重新拉。 */
    public static int contentGeneration;

    private static final int MAX_IMAGES = 9;
    /** 可见性三档的服务端取值：公开 2、注册用户 1、仅自己 0。 */
    private static final int[] VISIBILITY_VALUES = {2, 1, 0};
    /** 档位顺序，同时是 types.value()，切换表单形状全靠它。 */
    private static final String[] TYPE_KEYS =
            {Post.TYPE_MOMENT, Post.TYPE_IMAGES, Post.TYPE_VIDEO, Post.TYPE_BLOG};
    private static final int MOMENT = 0;
    private static final int IMAGES = 1;
    private static final int VIDEO = 2;
    private static final int BLOG = 3;

    private static final int LEG_IMAGES = 0;
    private static final int LEG_VIDEO = 1;
    private static final int LEG_COVER = 2;

    private ActivityComposeBinding binding;
    private OptionChips types;
    private OptionChips visibility;
    private OptionChips categories;
    private MentionPopup mentionPopup;
    private final List<Cell> cells = new ArrayList<>();

    private int postId;
    private boolean busy;
    private ActivityResultLauncher<String> imagePicker;
    private ActivityResultLauncher<String> videoPicker;
    private ActivityResultLauncher<String> coverPicker;
    private ActivityResultLauncher<Intent> recorder;

    private List<Category> cateOptions = Collections.emptyList();
    /** 编辑态拿到的分类只有名字，等分类列表回来再按名字落位。 */
    private String wantedCateName;

    private Uri videoLocal;
    private String videoPath;
    private String videoUrl;
    /** 素材名，本地选的和站内已有的都归到这里，供「时长 · 方向」那行展示。 */
    private String videoName;
    private boolean videoVertical;
    private int durationSeconds;
    private int videoType;
    /** 只有本轮刚上传的视频才带时长和方向，编辑时不动这两个字段。 */
    private boolean videoFresh;
    private Uri coverLocal;
    private String coverPath;
    private String existingCoverUrl;
    /** 本轮刚上传的正文图片首图缩略图；动态更新时用它当封面，服务端不会自动重推。 */
    private String imagesThumb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityComposeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(), this::onImagesPicked);
        videoPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onVideoPicked);
        coverPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onCoverPicked);
        recorder = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onRecorded);

        types = new OptionChips(binding.typeRow, new String[]{
                        getString(R.string.type_moment), getString(R.string.type_images),
                        getString(R.string.type_video), getString(R.string.type_blog)},
                new int[]{MOMENT, IMAGES, VIDEO, BLOG}, index -> applyType());
        visibility = new OptionChips(binding.visibilityRow,
                new String[]{getString(R.string.vis_public), getString(R.string.vis_members),
                        getString(R.string.vis_private)},
                VISIBILITY_VALUES, index -> applyVisibility());

        mentionPopup = MentionPopup.attach(binding.body);
        binding.btnBack.setOnClickListener(view -> finish());
        binding.btnPublish.setOnClickListener(view -> submit(1));
        binding.btnDraft.setOnClickListener(view -> submit(0));
        binding.btnVideoPick.setOnClickListener(view -> videoPicker.launch("video/*"));
        binding.btnVideoRecord.setOnClickListener(view -> recordVideo());
        binding.btnCoverPick.setOnClickListener(view -> coverPicker.launch("image/*"));

        postId = getIntent().getIntExtra(EXTRA_ID, 0);
        if (postId > 0) {
            binding.title.setText(R.string.title_compose_edit);
            binding.btnDelete.setVisibility(View.VISIBLE);
            binding.btnDelete.setOnClickListener(view -> confirmDelete());
            binding.btnPublish.setText(R.string.action_update);
            binding.btnDraft.setVisibility(View.GONE);
            types.select(typeIndexOf(getIntent().getStringExtra(EXTRA_TYPE)));
            loadForEdit();
        }
        applyType();
        renderGrid();
        CategoryApi.list(this, this::applyCategories);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mentionPopup.dismiss();
        binding = null;
    }

    private static int typeIndexOf(String type) {
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            if (TYPE_KEYS[i].equals(type)) {
                return i;
            }
        }
        return MOMENT;
    }

    private int kind() {
        return types.value();
    }

    private boolean isImages() {
        return kind() == IMAGES;
    }

    private boolean isVideo() {
        return kind() == VIDEO;
    }

    private boolean isBlog() {
        return kind() == BLOG;
    }

    private String currentType() {
        return TYPE_KEYS[kind()];
    }

    /** 换档只改可见性与文案，已填的内容留在原地，切回来还在。 */
    private void applyType() {
        int kind = kind();
        binding.titleInput.setVisibility(kind == MOMENT ? View.GONE : View.VISIBLE);
        binding.titleInput.setHint(kind == IMAGES ? R.string.hint_compose_title
                : kind == VIDEO ? R.string.hint_compose_title_video
                : R.string.hint_compose_title_blog);
        binding.body.setHint(kind == MOMENT ? R.string.hint_compose_moment
                : kind == IMAGES ? R.string.hint_compose_note
                : kind == VIDEO ? R.string.hint_compose_desc : R.string.hint_compose_content);
        binding.body.setMinimumHeight(dp(kind == MOMENT || kind == BLOG ? 150 : 80));

        boolean grid = !isVideo();
        binding.picsRow.setVisibility(grid ? View.VISIBLE : View.GONE);
        binding.grid.setVisibility(grid ? View.VISIBLE : View.GONE);
        binding.picsLabel.setText(kind == IMAGES ? R.string.label_compose_images
                : kind == BLOG ? R.string.label_compose_pics_blog : R.string.label_compose_pics);
        binding.videoSection.setVisibility(isVideo() ? View.VISIBLE : View.GONE);
        binding.coverSection.setVisibility(isVideo() || isBlog() ? View.VISIBLE : View.GONE);
        binding.btnCoverPick.setText(isVideo()
                ? R.string.action_cover_replace : R.string.action_cover_pick);
        binding.cateSection.setVisibility(isBlog() ? View.VISIBLE : View.GONE);

        renderGrid();
        renderVideoInfo();
        renderCover();
        applyVisibility();
    }

    /** 草稿和私密内容服务端不发提及通知；博客与短视频正文也不触发提及，提示语跟着档位走。 */
    private void applyVisibility() {
        boolean mentions = kind() == MOMENT || isImages();
        binding.mentionHint.setVisibility(mentions ? View.VISIBLE : View.GONE);
        if (mentions) {
            binding.mentionHint.setText(visibility.value() == 0
                    ? R.string.compose_hint_mention_quiet : R.string.compose_hint_mention);
        }
    }

    private void chooseImages() {
        if (cells.size() >= MAX_IMAGES) {
            toast(getString(R.string.compose_limit, MAX_IMAGES));
            return;
        }
        imagePicker.launch("image/*");
    }

    private void onImagesPicked(List<Uri> uris) {
        for (Uri uri : uris) {
            if (cells.size() >= MAX_IMAGES) {
                toast(getString(R.string.compose_limit, MAX_IMAGES));
                break;
            }
            cells.add(Cell.local(uri));
        }
        renderGrid();
    }

    /** 换视频等于换整条素材：旧的上传结果和自动封面一起作废。 */
    private void onVideoPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        videoLocal = uri;
        videoName = UploadApi.displayName(this, uri);
        videoUrl = null;
        coverLocal = null;
        coverPath = null;
        videoFresh = false;
        renderVideoInfo();
        renderCover();
    }

    private void onCoverPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        coverLocal = uri;
        coverPath = null;
        renderCover();
    }

    /**
     * 拍摄交给系统相机，回来的 uri 在结果数据里。
     * 不传 EXTRA_OUTPUT：老相机应用（com.android.camera2）拿到 content uri 会在存片时直接崩掉。
     */
    private void recordVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        try {
            recorder.launch(intent);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.video_record_unavailable));
        }
    }

    private void onRecorded(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) {
            return;
        }
        Intent data = result.getData();
        Uri uri = data == null ? null : data.getData();
        if (uri == null) {
            toast(getString(R.string.video_record_missing));
            return;
        }
        onVideoPicked(uri);
    }

    /** 九宫格整体重绘：最多 9 格，重建比算增量删除更省事。 */
    private void renderGrid() {
        int size = (getResources().getDisplayMetrics().widthPixels - dp(32) - dp(16)) / 3;
        binding.grid.removeAllViews();
        for (int i = 0; i < cells.size(); i++) {
            binding.grid.addView(cellView(cells.get(i), i, size));
        }
        if (cells.size() < MAX_IMAGES) {
            binding.grid.addView(addView(size));
        }
        binding.picsCount.setText(getString(R.string.compose_limit, MAX_IMAGES));
    }

    private View cellView(Cell cell, int index, int size) {
        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutParams(gridParams(size));
        ImageView image = new ImageView(this);
        image.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (cell.local != null) {
            image.setImageURI(cell.local);
        } else {
            ImageLoader.into(image, cell.remoteUrl, size);
        }
        frame.addView(image);

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextSize(15);
        remove.setGravity(Gravity.CENTER);
        remove.setTextColor(ContextCompat.getColor(this, R.color.white));
        remove.setBackgroundResource(R.drawable.bg_badge_dark);
        FrameLayout.LayoutParams removeParams =
                new FrameLayout.LayoutParams(dp(22), dp(22));
        removeParams.gravity = Gravity.TOP | Gravity.END;
        removeParams.setMargins(dp(3), dp(3), dp(3), dp(3));
        remove.setLayoutParams(removeParams);
        remove.setOnClickListener(view -> {
            cells.remove(index);
            renderGrid();
        });
        frame.addView(remove);
        return frame;
    }

    private View addView(int size) {
        TextView add = new TextView(this);
        add.setLayoutParams(gridParams(size));
        add.setBackgroundResource(R.drawable.bg_add_image);
        add.setGravity(Gravity.CENTER);
        add.setText(R.string.action_add_image);
        add.setTextSize(13);
        add.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        add.setOnClickListener(view -> chooseImages());
        return add;
    }

    private GridLayout.LayoutParams gridParams(int size) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = size;
        params.height = size;
        params.setMargins(0, 0, dp(8), dp(8));
        return params;
    }

    private void renderVideoInfo() {
        if (videoLocal != null) {
            binding.videoInfo.setText(getString(R.string.compose_video_ready, videoName));
            return;
        }
        if (videoName == null) {
            binding.videoInfo.setText("");
            return;
        }
        binding.videoInfo.setText(getString(R.string.compose_video_current, videoName,
                durationSeconds,
                getString(videoVertical ? R.string.video_portrait : R.string.video_landscape)));
    }

    private void renderCover() {
        if (coverLocal != null) {
            binding.coverImage.setImageURI(coverLocal);
        } else if (coverPath != null) {
            ImageLoader.into(binding.coverImage, ApiClient.absoluteUrl(coverPath), dp(120));
        } else if (existingCoverUrl != null) {
            ImageLoader.into(binding.coverImage, existingCoverUrl, dp(120));
        } else {
            binding.coverImage.setImageDrawable(null);
        }
        boolean hasCover = coverLocal != null || coverPath != null || existingCoverUrl != null;
        if (isBlog()) {
            binding.coverHint.setText(R.string.video_cover_blog);
        } else if (hasCover) {
            binding.coverHint.setText(R.string.video_cover_auto);
        } else if (videoPath == null && videoUrl == null) {
            // 还没上传，抽帧结果无从得知，先按「会自动有」说
            binding.coverHint.setText(R.string.video_cover_wait);
        } else {
            binding.coverHint.setText(R.string.video_cover_none);
        }
    }

    private void applyCategories(List<Category> options) {
        cateOptions = options;
        binding.cateRow.removeAllViews();
        List<Category> known = options;
        if (known.isEmpty()) {
            categories = null;
            return;
        }
        String[] labels = new String[known.size() + 1];
        int[] values = new int[known.size() + 1];
        labels[0] = getString(R.string.cate_none);
        values[0] = 0;
        for (int i = 0; i < known.size(); i++) {
            labels[i + 1] = known.get(i).name;
            values[i + 1] = known.get(i).id;
        }
        categories = new OptionChips(binding.cateRow, labels, values, index -> { });
        if (wantedCateName != null) {
            for (int i = 1; i <= known.size(); i++) {
                if (wantedCateName.equals(known.get(i - 1).name)) {
                    categories.select(i);
                    break;
                }
            }
        }
    }

    private void loadForEdit() {
        PostApi.detail(this, currentType(), postId, response -> {
            if (binding == null) {
                return;
            }
            if (!response.success) {
                showError(PostFormat.message(this, response));
                return;
            }
            PostDetail detail = PostDetail.fromJson(response.data);
            visibility.selectValue(detail.post.visibility);
            if (detail.post.status == 0) {
                // 草稿不该被「保存修改」顺手发布，把存草稿和发布两个按钮还回来
                binding.btnPublish.setText(R.string.action_publish);
                binding.btnDraft.setVisibility(View.VISIBLE);
            }
            switch (currentTypeIndex()) {
                case IMAGES:
                    String caption = detail.caption;
                    int line = caption.indexOf('\n');
                    binding.titleInput.setText(line < 0 ? caption : caption.substring(0, line));
                    binding.body.setText(line < 0 ? "" : caption.substring(line + 1).trim());
                    for (PostDetail.Image image : detail.images) {
                        cells.add(Cell.remote(image.filePath));
                    }
                    break;
                case VIDEO:
                    binding.titleInput.setText(detail.post.title);
                    binding.body.setText(detail.html);
                    videoUrl = detail.post.videoUrl;
                    videoName = TextUtils.isEmpty(videoUrl) ? null
                            : Uri.parse(videoUrl).getLastPathSegment();
                    durationSeconds = detail.post.durationSeconds;
                    videoVertical = detail.post.vertical;
                    existingCoverUrl = detail.post.coverUrl;
                    break;
                case BLOG:
                    binding.titleInput.setText(detail.post.title);
                    binding.body.setText(PostHtml.plainText(detail.html));
                    for (String src : PostHtml.imageSrcs(detail.html)) {
                        cells.add(Cell.remote(src));
                    }
                    existingCoverUrl = detail.post.coverUrl;
                    wantedCateName = detail.post.categories.isEmpty()
                            ? null : detail.post.categories.get(0);
                    applyCategories(cateOptions);
                    break;
                default:
                    binding.body.setText(PostHtml.plainText(detail.html));
                    for (String src : PostHtml.imageSrcs(detail.html)) {
                        cells.add(Cell.remote(src));
                    }
            }
            applyType();
        });
    }

    private int currentTypeIndex() {
        return kind();
    }

    private void submit(int status) {
        if (busy) {
            return;
        }
        String error = validate();
        if (error != null) {
            showError(error);
            return;
        }
        binding.error.setVisibility(View.GONE);
        List<Leg> legs = legs();
        if (legs.isEmpty()) {
            send(status);
            return;
        }
        imagesThumb = null;
        runLegs(legs, 0, status);
    }

    /** 一次发布最多三条腿：正文图片、视频文件、封面，各走自己的 kind，只能排队传。 */
    private List<Leg> legs() {
        List<Leg> legs = new ArrayList<>();
        List<Cell> pending = pending();
        if (!pending.isEmpty()) {
            legs.add(new Leg(LEG_IMAGES, isImages() ? UploadApi.KIND_IMAGES : UploadApi.KIND_MOMENT,
                    getString(R.string.compose_uploading, pending.size()), uris(pending), pending));
        }
        if (videoLocal != null && videoPath == null) {
            legs.add(new Leg(LEG_VIDEO, UploadApi.KIND_VIDEO,
                    getString(R.string.compose_uploading_video),
                    Collections.singletonList(videoLocal), null));
        }
        if (coverLocal != null) {
            legs.add(new Leg(LEG_COVER, isVideo() ? UploadApi.KIND_IMAGES : UploadApi.KIND_BLOG,
                    getString(R.string.compose_uploading_cover),
                    Collections.singletonList(coverLocal), null));
        }
        return legs;
    }

    private void runLegs(List<Leg> legs, int index, int status) {
        if (index == legs.size()) {
            send(status);
            return;
        }
        Leg leg = legs.get(index);
        setBusy(true, leg.busyText);
        UploadApi.upload(this, leg.kind, leg.files, percent -> {
            if (binding != null) {
                binding.progress.setProgress(percent);
            }
        }, (response, result) -> {
            if (binding == null) {
                return;
            }
            if (!response.success || result == null) {
                setBusy(false, null);
                showError(PostFormat.message(this, response));
                return;
            }
            leg.apply(result);
            runLegs(legs, index + 1, status);
        });
    }

    private List<Cell> pending() {
        List<Cell> pending = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.local != null && cell.path == null) {
                pending.add(cell);
            }
        }
        return pending;
    }

    private List<Uri> uris(List<Cell> pending) {
        List<Uri> uris = new ArrayList<>();
        for (Cell cell : pending) {
            uris.add(cell.local);
        }
        return uris;
    }

    /** 服务端对四类内容的硬性要求都跟草稿一样，别让用户提交完才收到 422。 */
    private String validate() {
        String text = binding.body.getText().toString().trim();
        switch (kind()) {
            case IMAGES:
                return cells.isEmpty() ? getString(R.string.compose_need_images) : null;
            case VIDEO:
                // 编辑态的视频只在 videoUrl 里，不重新选也算「有视频」
                if (videoLocal == null && videoPath == null && videoUrl == null) {
                    return getString(R.string.compose_need_video);
                }
                return text.isEmpty() ? getString(R.string.compose_need_desc) : null;
            case BLOG:
                return text.isEmpty() ? getString(R.string.compose_need_content) : null;
            default:
                boolean empty = text.isEmpty() && cells.isEmpty();
                return empty ? getString(R.string.compose_empty_text) : null;
        }
    }

    private void send(int status) {
        setBusy(true, getString(R.string.compose_publishing));
        JSONObject payload = payload(status);
        ApiClient.Callback callback = response -> {
            if (binding == null) {
                return;
            }
            setBusy(false, null);
            if (response.success) {
                contentGeneration++;
                toast(getString(status == 0 ? R.string.compose_draft_saved
                        : postId > 0 ? R.string.compose_updated : R.string.compose_published));
                setResult(Activity.RESULT_OK);
                finish();
                return;
            }
            showError(fieldError(response));
        };
        if (postId > 0) {
            PostApi.update(this, currentType(), postId, payload, callback);
        } else {
            PostApi.create(this, currentType(), payload, callback);
        }
    }

    /** 422 的明细按字段给，四类内容可能踩到的都在这儿。 */
    private String fieldError(ApiClient.Response response) {
        String[] fields = {"content", "file_paths", "caption", "description",
                "video_path", "thumb", "cate_id"};
        for (String field : fields) {
            String message = response.errorOf(field);
            if (message != null) {
                return message;
            }
        }
        return PostFormat.message(this, response);
    }

    private JSONObject payload(int status) {
        List<String> paths = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.path != null) {
                paths.add(cell.path);
            }
        }
        String title = binding.titleInput.getText().toString().trim();
        String text = binding.body.getText().toString().trim();
        JSONObject payload = new JSONObject();
        try {
            switch (kind()) {
                case IMAGES:
                    payload.put("caption", PostHtml.caption(title, text));
                    JSONArray filePaths = new JSONArray();
                    for (String path : paths) {
                        filePaths.put(path);
                    }
                    // file_paths 是完整清单：漏掉的图会被服务端从图集里移除
                    payload.put("file_paths", filePaths);
                    break;
                case VIDEO:
                    putIfNotEmpty(payload, "title", title);
                    payload.put("description", text);
                    // video_path 传空串等于「这次不换」，没有新视频就干脆不提交
                    if (videoPath != null) {
                        payload.put("video_path", videoPath);
                    }
                    if (coverPath != null) {
                        payload.put("thumbnail_path", coverPath);
                    }
                    if (videoFresh) {
                        payload.put("duration_seconds", durationSeconds);
                        payload.put("video_type", videoType);
                    }
                    break;
                case BLOG:
                    putIfNotEmpty(payload, "title", title);
                    payload.put("content", PostHtml.article(text, paths));
                    if (coverPath != null) {
                        payload.put("thumb", coverPath);
                    }
                    if (categories != null && categories.value() != 0) {
                        payload.put("cate_id", categories.value());
                    }
                    break;
                default:
                    payload.put("content", PostHtml.article(text, paths));
                    putMomentThumb(payload, paths);
            }
            payload.put("status", status);
            payload.put("visibility", visibility.value());
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return payload;
    }

    private static void putIfNotEmpty(JSONObject payload, String key, String value)
            throws JSONException {
        if (!value.isEmpty()) {
            payload.put(key, value);
        }
    }

    /**
     * 动态封面只在创建时由服务端按正文首图自动推导，更新时不重推：不提交 thumb 就保留旧封面。
     * 所以「原来没图、编辑后加了图」的动态必须在这里显式补上 thumb，否则列表卡片永远没封面。
     * 新建时交给服务端推导；更新时图片删空则清空封面，首图是本轮新上传的就用它的缩略图。
     */
    private void putMomentThumb(JSONObject payload, List<String> paths) throws JSONException {
        if (postId <= 0) {
            return;
        }
        if (paths.isEmpty()) {
            payload.put("thumb", "");
            return;
        }
        Cell first = cells.get(0);
        if (first.local != null) {
            payload.put("thumb", imagesThumb != null ? imagesThumb : paths.get(0));
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.compose_delete_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        PostApi.remove(this, currentType(), postId, response -> {
                            if (binding == null) {
                                return;
                            }
                            if (!response.success) {
                                showError(PostFormat.message(this, response));
                                return;
                            }
                            toast(getString(R.string.compose_deleted));
                            contentGeneration++;
                            setResult(Activity.RESULT_OK);
                            finish();
                        }))
                .show();
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.progressText.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.progressText.setText(message == null ? "" : message);
        binding.btnPublish.setEnabled(!busy);
        binding.btnDraft.setEnabled(!busy);
        binding.btnPublish.setAlpha(busy ? 0.5f : 1f);
    }

    private void showError(String message) {
        binding.error.setText(message);
        binding.error.setVisibility(View.VISIBLE);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return PostFormat.dp(this, value);
    }

    public static Intent composeIntent(Activity host, String type) {
        Intent intent = new Intent(host, ComposeActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        return intent;
    }

    public static Intent editIntent(Activity host, Post post) {
        Intent intent = new Intent(host, ComposeActivity.class);
        intent.putExtra(EXTRA_TYPE, post.type);
        intent.putExtra(EXTRA_ID, post.id);
        return intent;
    }

    /** 一条上传腿：kind、文件、进度文案，以及拿到站内路径后往哪个字段上写。 */
    private final class Leg {
        final int tag;
        final String kind;
        final String busyText;
        final List<Uri> files;
        final List<Cell> targets;

        Leg(int tag, String kind, String busyText, List<Uri> files, List<Cell> targets) {
            this.tag = tag;
            this.kind = kind;
            this.busyText = busyText;
            this.files = files;
            this.targets = targets;
        }

        void apply(UploadResult result) {
            if (tag == LEG_IMAGES) {
                for (int i = 0; i < targets.size() && i < result.paths.size(); i++) {
                    targets.get(i).path = result.paths.get(i);
                }
                imagesThumb = result.thumbnailPath;
            } else if (tag == LEG_VIDEO) {
                videoPath = result.videoPath;
                coverPath = result.thumbnailPath;
                durationSeconds = result.durationSeconds;
                videoType = result.videoType;
                videoVertical = result.videoType == 2;
                videoFresh = true;
                videoLocal = null;
                renderVideoInfo();
                renderCover();
            } else {
                coverPath = result.coverPath();
                coverLocal = null;
            }
        }
    }

    /** 一张图：local 是相册里的文件，path 是上传后服务端的站内路径，两者都可能在编辑态出现。 */
    private static final class Cell {
        final Uri local;
        final String remoteUrl;
        String path;

        private Cell(Uri local, String path, String remoteUrl) {
            this.local = local;
            this.path = path;
            this.remoteUrl = remoteUrl;
        }

        static Cell local(Uri uri) {
            return new Cell(uri, null, null);
        }

        static Cell remote(String path) {
            return new Cell(null, path, ApiClient.absoluteUrl(path));
        }
    }
}
