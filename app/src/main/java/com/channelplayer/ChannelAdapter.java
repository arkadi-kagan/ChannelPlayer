package com.channelplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    private List<ChannelItem> channelItems;
    private OnChannelClickListener listener;

    public interface OnChannelClickListener {
        void onChannelClick(ChannelItem item);
    }

    public static class ChannelItem {
        private final String channelId;
        private final String iconUrl;
        private final String description;

        public ChannelItem(String channelId, String iconUrl, String description) {
            this.channelId = channelId;
            this.iconUrl = iconUrl;
            this.description = description;
        }

        public String getChannelId() {
            return channelId;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class ChannelViewHolder extends RecyclerView.ViewHolder {
        public ImageView channelIcon;
        public TextView channelDescription;

        public ChannelViewHolder(View v) {
            super(v);
            channelIcon = v.findViewById(R.id.channel_icon);
            channelDescription = v.findViewById(R.id.channel_description);
        }

        public void bind(final ChannelItem item, final OnChannelClickListener listener) {
            itemView.setOnClickListener(v -> listener.onChannelClick(item));
        }
    }

    public ChannelAdapter(List<ChannelItem> channelItems, OnChannelClickListener listener) {
        this.channelItems = channelItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.channel_list_item, parent, false);
        return new ChannelViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        ChannelItem currentItem = channelItems.get(position);
        holder.channelDescription.setText(currentItem.getDescription());
        Picasso.get().load(currentItem.getIconUrl()).into(holder.channelIcon);
        holder.bind(currentItem, listener);
    }

    @Override
    public int getItemCount() {
        return channelItems.size();
    }
}
