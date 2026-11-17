package com.sinoll.home.ui.search;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * 标题栏的搜索框和结果页分属两个 View，靠 activity 作用域的这份状态把关键词递过去。
 */
public class SearchViewModel extends ViewModel {

    public static final String ARG_QUERY = "q";

    private final MutableLiveData<String> query = new MutableLiveData<>();

    public MutableLiveData<String> query() {
        return query;
    }

    public void setQuery(String value) {
        query.setValue(value);
    }
}
