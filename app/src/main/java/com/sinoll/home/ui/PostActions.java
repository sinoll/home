package com.sinoll.home.ui;

import com.sinoll.home.data.Post;

/** 卡片上的操作入口，由列表页统一接住。 */
public interface PostActions {

    /** 点整张卡片看详情。短视频在 tab 里就是全屏，不走这一步。 */
    void onOpen(Post post);

    /** onCommented 在评论真的发出去之后回调，卡片借它把条数补上。 */
    void onComment(Post post, Runnable onCommented);

    void onShare(Post post);

    /** 正文里的 @某人 被点到：客户端没有用户主页，落到「私信 TA」。 */
    void onMention(String username);
}
