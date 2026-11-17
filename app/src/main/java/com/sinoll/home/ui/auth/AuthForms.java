package com.sinoll.home.ui.auth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;
import com.sinoll.home.R;
import com.sinoll.home.data.ApiClient;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/** 认证表单共用的校验、错误回填、提交态与验证码倒计时。 */
final class AuthForms {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern USERNAME =
            Pattern.compile("^[\\p{IsHan}A-Za-z0-9._-]{2,24}$");
    private static final Pattern PASSWORD =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$");
    private static final Pattern CODE = Pattern.compile("^\\d{6}$");

    static String text(EditText input) {
        return input.getText().toString().trim();
    }

    static boolean isEmail(String value) {
        return EMAIL.matcher(value).matches();
    }

    static boolean isUsername(String value) {
        return USERNAME.matcher(value).matches();
    }

    static boolean isPassword(String value) {
        return PASSWORD.matcher(value).matches();
    }

    static boolean isCode(String value) {
        return CODE.matcher(value).matches();
    }

    /** 请求进行中锁住提交按钮并显示进度。 */
    static void setLoading(Button submit, ProgressBar progress, boolean loading) {
        submit.setEnabled(!loading);
        progress.setVisibility(loading ? ProgressBar.VISIBLE : ProgressBar.GONE);
    }

    static void clearErrors(TextInputLayout... fields) {
        for (TextInputLayout field : fields) {
            field.setError(null);
        }
    }

    /** 把 data.errors 里能对上字段的说明回填到输入框，返回是否回填成功。 */
    static boolean applyFieldErrors(Map<String, TextInputLayout> fields, ApiClient.Response response) {
        boolean applied = false;
        for (Iterator<String> keys = response.errors().keys(); keys.hasNext(); ) {
            String key = keys.next();
            TextInputLayout field = fields.get(key);
            String message = response.errorOf(key);
            if (field != null && message != null) {
                field.setError(message);
                applied = true;
            }
        }
        return applied;
    }

    /** 服务端没给字段级明细时，给界面一句话文案。 */
    static String messageFor(Context context, ApiClient.Response response) {
        if (!response.message.isEmpty()) {
            return response.message;
        }
        switch (response.failure) {
            case TIMEOUT:
                return context.getString(R.string.error_timeout);
            case NETWORK:
                return context.getString(R.string.error_network);
            default:
                break;
        }
        if (response.code == 429) {
            return context.getString(R.string.error_rate_limited);
        }
        return context.getString(R.string.error_server, response.code);
    }

    /** 60 秒防刷倒计时，同一邮箱服务端也限 60 秒，这里只是把约束显性化。 */
    static final class CodeCountdown {

        private static final int TOTAL_SECONDS = 60;

        private final TextView label;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private int remaining;

        CodeCountdown(TextView label) {
            this.label = label;
        }

        boolean isRunning() {
            return remaining > 0;
        }

        void start() {
            remaining = TOTAL_SECONDS;
            label.setAlpha(0.45f);
            handler.removeCallbacks(tick);
            handler.post(tick);
        }

        void release() {
            handler.removeCallbacks(tick);
        }

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (remaining <= 1) {
                    remaining = 0;
                    label.setAlpha(1f);
                    label.setText(R.string.action_send_code);
                    return;
                }
                remaining--;
                label.setText(label.getResources().getString(R.string.countdown_resend, remaining));
                handler.postDelayed(this, 1000L);
            }
        };
    }

    private AuthForms() {
    }
}
