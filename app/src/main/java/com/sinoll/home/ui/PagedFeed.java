package com.sinoll.home.ui;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

/**
 * 列表分页的游标与触底触发。数据怎么拼由 {@link Loader} 决定，界面状态由 {@link Ui} 决定。
 */
public final class PagedFeed {

    public interface Loader {
        /** page 从 1 开始；回调里给出本次追加条数、是否还有下一页、失败文案（成功为 null）。 */
        void load(int page, Result result);
    }

    public interface Result {
        void onResult(int appended, boolean hasMore, String error);
    }

    public interface Ui {
        void onReset();

        void onFirstLoading();

        void onEmpty();

        void onFailed(String message);

        void onContent();
    }

    private static final int PREFETCH = 5;

    private final Loader loader;
    private final Ui ui;
    private final RecyclerView recycler;

    private int page;
    private boolean loading;
    private boolean finished;

    public PagedFeed(RecyclerView recycler, Loader loader, Ui ui) {
        this.recycler = recycler;
        this.loader = loader;
        this.ui = ui;
        this.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                int visible = lastVisiblePosition(view);
                int total = view.getAdapter() == null ? 0 : view.getAdapter().getItemCount();
                if (total > 0 && visible >= total - PREFETCH) {
                    loadNext();
                }
            }
        });
    }

    public void refresh() {
        page = 0;
        finished = false;
        loading = false;
        ui.onReset();
        loadNext();
    }

    /** 首屏失败后重试：还没拿到数据就整页重来，否则从停住的那页继续。 */
    public void retry() {
        if (page == 0) {
            refresh();
        } else {
            loadNext();
        }
    }

    private void loadNext() {
        if (loading || finished) {
            return;
        }
        loading = true;
        if (page == 0) {
            ui.onFirstLoading();
        }
        int requestPage = page + 1;
        loader.load(requestPage, (appended, hasMore, error) -> {
            loading = false;
            if (error != null) {
                ui.onFailed(error);
                return;
            }
            page = requestPage;
            finished = !hasMore;
            if (page == 1 && appended == 0) {
                ui.onEmpty();
                return;
            }
            ui.onContent();
        });
    }

    private static int lastVisiblePosition(RecyclerView view) {
        RecyclerView.LayoutManager manager = view.getLayoutManager();
        if (manager instanceof StaggeredGridLayoutManager) {
            int[] positions = ((StaggeredGridLayoutManager) manager)
                    .findLastVisibleItemPositions(null);
            int last = positions.length == 0 ? 0 : positions[0];
            for (int position : positions) {
                last = Math.max(last, position);
            }
            return last;
        }
        if (manager instanceof LinearLayoutManager) {
            return ((LinearLayoutManager) manager).findLastVisibleItemPosition();
        }
        return 0;
    }
}
