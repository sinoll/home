package com.sinoll.home.ui.messages;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sinoll.home.R;
import com.sinoll.home.data.Message;
import com.sinoll.home.data.MessageApi;
import com.sinoll.home.databinding.FragmentMessagesBinding;
import com.sinoll.home.databinding.ItemMessageBinding;
import com.sinoll.home.ui.FeedState;
import com.sinoll.home.ui.MessageRow;
import com.sinoll.home.ui.PagedFeed;
import com.sinoll.home.ui.PostFormat;

import java.util.ArrayList;
import java.util.List;

/** 消息页：GET /messages 的分页列表，收件人那半边点开才算读过。 */
public class MessagesFragment extends Fragment implements PagedFeed.Ui {

    private static final int PER_PAGE = 20;

    private FragmentMessagesBinding binding;
    private FeedState state;
    private MessageAdapter adapter;
    private PagedFeed paged;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMessagesBinding.inflate(inflater, container, false);
        state = new FeedState(binding.state, false);
        adapter = new MessageAdapter(this::open);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.state.retry.setOnClickListener(view -> paged.retry());
        paged = new PagedFeed(binding.recycler, this::load, this);
        paged.refresh();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        state = null;
        adapter = null;
        paged = null;
    }

    /** 已读和不是我收的都无需请求；接口幂等，重复点也只是再确认一次。 */
    private void open(Message message) {
        if (message.read || message.mine) {
            return;
        }
        Context context = getContext();
        MessageApi.markRead(context, message.id, response -> {
            if (binding == null || !response.success) {
                return;
            }
            adapter.markRead(message);
        });
    }

    private void load(int page, PagedFeed.Result result) {
        Context context = getContext();
        MessageApi.load(context, page, PER_PAGE, (messages, current, totalPages, response) -> {
            if (binding == null) {
                return;
            }
            if (!response.success) {
                result.onResult(0, false, PostFormat.message(context, response));
                return;
            }
            adapter.addAll(messages);
            result.onResult(messages.size(), current < totalPages, null);
        });
    }

    @Override
    public void onReset() {
        adapter.clear();
    }

    @Override
    public void onFirstLoading() {
        state.showLoading(R.string.state_loading);
    }

    @Override
    public void onEmpty() {
        state.showMessage(R.string.state_empty_messages);
    }

    @Override
    public void onFailed(String message) {
        state.showFailed(message);
    }

    @Override
    public void onContent() {
        state.hide();
    }

    private interface OpenMessage {
        void open(Message message);
    }

    private static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {

        private final OpenMessage open;
        private final List<Message> items = new ArrayList<>();

        MessageAdapter(OpenMessage open) {
            this.open = open;
        }

        void addAll(List<Message> messages) {
            int start = items.size();
            items.addAll(messages);
            notifyItemRangeInserted(start, messages.size());
        }

        void clear() {
            items.clear();
            notifyDataSetChanged();
        }

        /** 按对象本身找位置，中途来了新数据也不会标错行。 */
        void markRead(Message message) {
            int index = items.indexOf(message);
            if (index < 0) {
                return;
            }
            message.read = true;
            notifyItemChanged(index);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemMessageBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Message message = items.get(position);
            new MessageRow(holder.binding).bind(message);
            holder.binding.getRoot().setOnClickListener(view -> {
                int clicked = holder.getBindingAdapterPosition();
                if (clicked != RecyclerView.NO_POSITION) {
                    open.open(items.get(clicked));
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemMessageBinding binding;

            VH(ItemMessageBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
