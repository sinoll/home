package com.sinoll.home.ui.auth;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.sinoll.home.R;
import com.sinoll.home.data.AuthApi;
import com.sinoll.home.databinding.ActivityForgotPasswordBinding;

import java.util.HashMap;
import java.util.Map;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private AuthForms.CodeCountdown countdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        countdown = new AuthForms.CodeCountdown(binding.tvSendCode);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvGoLogin.setOnClickListener(v -> finish());
        binding.tvSendCode.setOnClickListener(v -> sendCode());
        binding.btnSubmit.setOnClickListener(v -> submit());
        binding.etConfirm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
    }

    private void sendCode() {
        if (countdown.isRunning()) {
            return;
        }
        String email = AuthForms.text(binding.etEmail);
        binding.tilEmail.setError(null);
        if (!AuthForms.isEmail(email)) {
            binding.tilEmail.setError(email.isEmpty()
                    ? getString(R.string.error_required, getString(R.string.hint_email))
                    : getString(R.string.error_email_invalid));
            return;
        }
        binding.tvSendCode.setEnabled(false);
        AuthApi.sendResetCode(email, response -> {
            if (binding == null) {
                return;
            }
            binding.tvSendCode.setEnabled(true);
            if (response.success) {
                countdown.start();
                Toast.makeText(this, getString(R.string.toast_code_sent, email),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (response.errors().has("email")) {
                binding.tilEmail.setError(response.errorOf("email"));
            } else {
                Toast.makeText(this, AuthForms.messageFor(this, response), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void submit() {
        String email = AuthForms.text(binding.etEmail);
        String code = AuthForms.text(binding.etCode);
        String password = AuthForms.text(binding.etPassword);
        String confirm = AuthForms.text(binding.etConfirm);
        Map<String, TextInputLayout> fields = fields();
        AuthForms.clearErrors(binding.tilEmail, binding.tilCode,
                binding.tilPassword, binding.tilConfirm);

        boolean valid = true;
        if (!AuthForms.isEmail(email)) {
            binding.tilEmail.setError(email.isEmpty()
                    ? getString(R.string.error_required, getString(R.string.hint_email))
                    : getString(R.string.error_email_invalid));
            valid = false;
        }
        if (!AuthForms.isCode(code)) {
            binding.tilCode.setError(code.isEmpty()
                    ? getString(R.string.error_required, getString(R.string.hint_code))
                    : getString(R.string.error_code_invalid));
            valid = false;
        }
        if (!AuthForms.isPassword(password)) {
            binding.tilPassword.setError(password.isEmpty()
                    ? getString(R.string.error_required, getString(R.string.hint_new_password))
                    : getString(R.string.error_password_invalid));
            valid = false;
        }
        if (!confirm.equals(password)) {
            binding.tilConfirm.setError(confirm.isEmpty()
                    ? getString(R.string.error_required, getString(R.string.hint_confirm_password))
                    : getString(R.string.error_password_mismatch));
            valid = false;
        }
        if (!valid) {
            return;
        }

        AuthForms.setLoading(binding.btnSubmit, binding.progress, true);
        AuthApi.resetPassword(email, code, password, confirm, response -> {
            if (binding == null) {
                return;
            }
            AuthForms.setLoading(binding.btnSubmit, binding.progress, false);
            if (response.success) {
                Toast.makeText(this, R.string.toast_reset_ok, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (!AuthForms.applyFieldErrors(fields, response)) {
                Toast.makeText(this, AuthForms.messageFor(this, response), Toast.LENGTH_LONG).show();
            }
        });
    }

    private Map<String, TextInputLayout> fields() {
        Map<String, TextInputLayout> map = new HashMap<>();
        map.put("email", binding.tilEmail);
        map.put("code", binding.tilCode);
        map.put("password", binding.tilPassword);
        map.put("confirmPassword", binding.tilConfirm);
        return map;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        countdown.release();
        binding = null;
    }
}
