package com.sinoll.home.ui.messages;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sinoll.home.R;
import com.sinoll.home.data.MessageApi;
import com.sinoll.home.data.UserApi;
import com.sinoll.home.data.UserRef;
import com.sinoll.home.databinding.ActivityMessageBinding;
import com.sinoll.home.ui.PostFormat;
import com.sinoll.home.ui.compose.UserSuggestions;

/**
 * 发起私信。收件人必须是活跃用户，接口按用户名找人，所以候选项写入的一直是 username。
 */
public final class PrivateMessageActivity extends AppCompatActivity {

    private static final String EXTRA_RECIPIENT = "recipient";

    private ActivityMessageBinding binding;
    private UserRef recipient;
    private UserSuggestions suggestions;

    public static Intent intent(Context context, String recipientUsername) {
        Intent intent = new Intent(context, PrivateMessageActivity.class);
        intent.putExtra(EXTRA_RECIPIENT, recipientUsername);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMessageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(view -> finish());
        binding.btnSend.setOnClickListener(view -> send());

        suggestions = new UserSuggestions(binding.recipient,
                (text, cursor) -> text.toString().trim(),
                user -> {
                    recipient = user;
                    binding.recipient.setText("@" + user.username);
                });

        String prefill = getIntent().getStringExtra(EXTRA_RECIPIENT);
        if (!TextUtils.isEmpty(prefill)) {
            binding.recipient.setText("@" + prefill);
            resolve(prefill);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        suggestions.dismiss();
        binding = null;
    }

    /** 从正文点进来时只带用户名，先确认这个人还在，免得发出去才报错。 */
    private void resolve(String username) {
        UserApi.suggest(this, username, users -> {
            if (binding == null) {
                return;
            }
            for (UserRef user : users) {
                if (user.username.equals(username)) {
                    recipient = user;
                    return;
                }
            }
        });
    }

    private void send() {
        if (recipient == null) {
            showError(getString(R.string.message_need_recipient));
            return;
        }
        String content = binding.content.getText().toString().trim();
        if (content.isEmpty()) {
            showError(getString(R.string.message_empty));
            return;
        }
        binding.error.setVisibility(View.GONE);
        binding.btnSend.setEnabled(false);
        MessageApi.send(this, recipient.username, content, response -> {
            if (binding == null) {
                return;
            }
            binding.btnSend.setEnabled(true);
            if (!response.success) {
                showError(PostFormat.message(this, response));
                return;
            }
            Toast.makeText(this, R.string.message_sent, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void showError(String message) {
        binding.error.setText(message);
        binding.error.setVisibility(View.VISIBLE);
    }
}
