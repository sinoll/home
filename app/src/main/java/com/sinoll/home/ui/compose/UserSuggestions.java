package com.sinoll.home.ui.compose;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListPopupWindow;

import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.UserApi;
import com.sinoll.home.data.UserRef;
import com.sinoll.home.databinding.ItemMentionBinding;
import com.sinoll.home.ui.PostFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * 「输入即搜用户」的候选浮层，@提及 与私信收件人共用。
 * 关键词怎么取、选中后写回什么由调用方决定，这里只管防抖、请求和展示。
 */
public final class UserSuggestions {

    private static final long DEBOUNCE_MS = 220L;

    /** 从当前文本与光标取出要查询的关键词；返回 null 表示此刻不该弹候选。 */
    public interface QuerySource {
        String query(CharSequence text, int cursor);
    }

    public interface OnPicked {
        void onPicked(UserRef user);
    }

    private final EditText anchor;
    private final QuerySource source;
    private final OnPicked picked;
    private final ListPopupWindow window;
    private final List<UserRef> users = new ArrayList<>();
    private final BaseAdapter adapter = new UserAdapter();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String requested;
    private boolean writing;

    public UserSuggestions(EditText anchor, QuerySource source, OnPicked picked) {
        this.anchor = anchor;
        this.source = source;
        this.picked = picked;
        window = new ListPopupWindow(anchor.getContext());
        window.setAnchorView(anchor);
        window.setAdapter(adapter);
        window.setWidth(PostFormat.dp(anchor.getContext(), 240));
        window.setModal(false);
        window.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setOnItemClickListener((parent, view, position, id) -> choose(position));
        anchor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (writing) {
                    return;
                }
                schedule(anchor.getSelectionStart());
            }
        });
    }

    public void dismiss() {
        handler.removeCallbacksAndMessages(null);
        window.dismiss();
    }

    private void choose(int position) {
        writing = true;
        picked.onPicked(users.get(position));
        writing = false;
        dismiss();
    }

    private void schedule(int cursor) {
        String query = source.query(anchor.getText(), cursor);
        handler.removeCallbacksAndMessages(null);
        if (query == null) {
            window.dismiss();
            return;
        }
        handler.postDelayed(() -> request(query, cursor), DEBOUNCE_MS);
    }

    /** 关键词为空时服务端给候选用户，所以刚聚焦也能弹出人来。 */
    private void request(String query, int cursor) {
        requested = query;
        UserApi.suggest(anchor.getContext(), query, candidates -> {
            // 请求回来时用户已经继续打字，这一批作废，交给下一次输入触发
            if (!anchor.isShown() || !query.equals(requested)
                    || !query.equals(source.query(anchor.getText(), anchor.getSelectionStart()))) {
                window.dismiss();
                return;
            }
            users.clear();
            users.addAll(candidates);
            if (users.isEmpty()) {
                window.dismiss();
                return;
            }
            adapter.notifyDataSetChanged();
            window.show();
        });
    }

    private final class UserAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return users.size();
        }

        @Override
        public UserRef getItem(int position) {
            return users.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ItemMentionBinding binding = convertView != null
                    ? ItemMentionBinding.bind(convertView)
                    : ItemMentionBinding.inflate(
                            LayoutInflater.from(parent.getContext()), parent, false);
            UserRef user = users.get(position);
            binding.name.setText(user.name);
            binding.username.setText("@" + user.username);
            binding.avatar.setText(PostFormat.initial(user.name));
            binding.avatar.setBackgroundResource(PostFormat.avatarFallback(user.id.hashCode()));
            ImageLoader.intoCircle(binding.avatarImage, user.avatarUrl,
                    PostFormat.dp(parent.getContext(), 28));
            return binding.getRoot();
        }
    }
}
