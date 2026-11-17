package com.sinoll.home.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;

import com.sinoll.home.R;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.data.Message;
import com.sinoll.home.databinding.ItemMessageBinding;

/** 消息行：头像、对方称呼、内容、时间与未读点，私信和提及共用。 */
public final class MessageRow {

    private final ItemMessageBinding binding;
    private final Context context;

    public MessageRow(ItemMessageBinding binding) {
        this.binding = binding;
        this.context = binding.getRoot().getContext();
    }

    public void bind(Message message) {
        bindWho(message);
        binding.content.setText(message.content);
        binding.time.setText(PostFormat.time(context, message.createdAtMillis));
        binding.type.setText(context.getString(message.isMention()
                ? R.string.type_message_mention : R.string.type_message_private));
        binding.unread.setVisibility(message.isUnread() ? View.VISIBLE : View.GONE);
    }

    /** 我发出去的那半边没有「谁找我」的语义，换成收件人，头像也退回首字。 */
    private void bindWho(Message message) {
        String name = message.mine ? message.recipientName : message.senderName;
        binding.name.setText(message.mine
                ? context.getString(R.string.message_to, name) : name);
        binding.name.setTypeface(message.isUnread() ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        binding.avatar.setText(PostFormat.initial(name));
        binding.avatar.setBackgroundResource(PostFormat.avatarFallback(message.id));
        ImageLoader.intoCircle(binding.avatarImage,
                message.mine ? null : message.senderAvatarUrl, PostFormat.dp(context, 36));
    }
}
