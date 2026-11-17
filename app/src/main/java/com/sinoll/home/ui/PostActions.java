package com.sinoll.home.ui;

import com.sinoll.home.data.Post;

/** 卡片上的操作入口，由列表页统一接住。 */
public interface PostActions {

    /** onCommented 在评论真的发出去之后回调，卡片借它把条数补上。 */
    void onComment(Post post, Runnable onCommented);

    void onShare(Post post);
}
