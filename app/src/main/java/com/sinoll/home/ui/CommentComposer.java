package com.sinoll.home.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.sinoll.home.R;
import com.sinoll.home.data.CommentApi;
import com.sinoll.home.data.Post;
import com.sinoll.home.databinding.DialogCommentBinding;

/** 底部评论输入条：发一条顶级评论，成功就收起。 */
public final class CommentComposer {

    public static void show(Fragment host, Post post, Runnable onPublished) {
        Context context = host.requireContext();
        DialogCommentBinding binding = DialogCommentBinding.inflate(
                LayoutInflater.from(context));
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(binding.getRoot())
                .create();

        binding.send.setOnClickListener(view -> {
            String content = binding.content.getText().toString().trim();
            if (content.isEmpty()) {
                showError(binding, context.getString(R.string.comment_empty));
                return;
            }
            binding.send.setEnabled(false);
            CommentApi.send(context, post.id, content, response -> {
                binding.send.setEnabled(true);
                if (!dialog.isShowing()) {
                    return;
                }
                if (response.success) {
                    dialog.dismiss();
                    Toast.makeText(context, R.string.comment_sent, Toast.LENGTH_SHORT).show();
                    onPublished.run();
                    return;
                }
                showError(binding, PostFormat.message(context, response));
            });
        });

        dialog.show();
        // 默认窗口有圆角内边距，底部输入条要贴着屏幕下沿铺满
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.BOTTOM;
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        binding.content.requestFocus();
    }

    private static void showError(DialogCommentBinding binding, String message) {
        binding.error.setText(message);
        binding.error.setVisibility(View.VISIBLE);
    }

    private CommentComposer() {
    }
}
