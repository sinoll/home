package com.sinoll.home.ui.compose;

import android.widget.EditText;

import com.sinoll.home.data.UserRef;
import com.sinoll.home.ui.MentionFormat;

/**
 * 正文里打 @ 时弹出候选用户。写入的是用户名而不是昵称：昵称可能带空格，
 * 会让服务端的字面匹配认错人。
 */
public final class MentionPopup {

    private final EditText anchor;
    private final UserSuggestions suggestions;

    private MentionPopup(EditText anchor) {
        this.anchor = anchor;
        suggestions = new UserSuggestions(anchor,
                MentionFormat::tokenAt, this::insert);
    }

    public static MentionPopup attach(EditText anchor) {
        return new MentionPopup(anchor);
    }

    public void dismiss() {
        suggestions.dismiss();
    }

    private void insert(UserRef user) {
        int start = MentionFormat.tokenStart(anchor.getText(), anchor.getSelectionStart());
        if (start < 0) {
            return;
        }
        anchor.getText().replace(start, anchor.getSelectionStart(),
                "@" + user.mentionToken() + " ");
        anchor.setSelection(anchor.getText().length());
    }
}
