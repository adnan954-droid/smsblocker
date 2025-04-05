package com.example.smsblocker;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private List<Message> messages;

    public ChatAdapter(List<Message> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Message message = messages.get(position);

        // Set message content and timestamp
        holder.messageBody.setText(message.getBody());
        holder.timestamp.setText(message.getTimestamp());

        // Adjust the background and alignment for received vs. sent messages
        if (message.isReceived()) {
            holder.messageBody.setBackgroundResource(R.drawable.received_message_bg);
            holder.messageBody.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.black));

            // Align left for received messages
            holder.messageBody.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            ((LinearLayout.LayoutParams) holder.messageBody.getLayoutParams()).gravity = Gravity.START;

            holder.timestamp.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            ((LinearLayout.LayoutParams) holder.timestamp.getLayoutParams()).gravity = Gravity.START;
        } else {
            holder.messageBody.setBackgroundResource(R.drawable.sent_message_bg);
            holder.messageBody.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.white));

            // Align right for sent messages
            holder.messageBody.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            ((LinearLayout.LayoutParams) holder.messageBody.getLayoutParams()).gravity = Gravity.END;

            holder.timestamp.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            ((LinearLayout.LayoutParams) holder.timestamp.getLayoutParams()).gravity = Gravity.END;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateMessages(List<Message> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }

    public void addMessage(Message message) {
        this.messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView messageBody;
        TextView timestamp;

        public ViewHolder(View itemView) {
            super(itemView);
            messageBody = itemView.findViewById(R.id.message_body);
            timestamp = itemView.findViewById(R.id.timestamp);
        }
    }
}
