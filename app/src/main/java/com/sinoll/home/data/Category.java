package com.sinoll.home.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 博客分类。发布只需要 id 和名字，其余字段留给站点后台自己用。
 */
public final class Category {

    public final int id;
    public final String name;

    private Category(JSONObject item) {
        id = item.optInt("id");
        name = item.optString("name");
    }

    public static Category fromJson(JSONObject item) {
        return item == null ? null : new Category(item);
    }

    /** GET /cate 的 data 是 {items, total}，列表不分页、按名称升序。 */
    public static List<Category> listFrom(JSONObject data) {
        JSONArray items = data == null ? null : data.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return Collections.emptyList();
        }
        List<Category> categories = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            Category category = fromJson(items.optJSONObject(i));
            if (category != null && category.id > 0 && !category.name.isEmpty()) {
                categories.add(category);
            }
        }
        return categories;
    }
}
