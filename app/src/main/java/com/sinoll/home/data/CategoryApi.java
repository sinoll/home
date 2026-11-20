package com.sinoll.home.data;

import android.content.Context;

import java.util.Collections;
import java.util.List;

/**
 * 博客分类查询。接口公开只读、不需要令牌，列表一次给全部且不分页，所以发布器进来取一次就够。
 */
public final class CategoryApi {

    public interface Callback {
        /** 失败与空列表都不区分：分类只是可选项，取不到就当没有分类。 */
        void onCategories(List<Category> categories);
    }

    public static void list(Context context, Callback callback) {
        ApiClient.get("/cate", null, response -> callback.onCategories(
                response.success ? Category.listFrom(response.data) : Collections.emptyList()));
    }

    private CategoryApi() {
    }
}
