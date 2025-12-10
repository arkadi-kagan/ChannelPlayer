package com.channelplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.channelplayer.cache.ChannelInfo; // Use the entity from the cache package
import com.squareup.picasso.Picasso;

/**
 * An adapter for displaying a list of YouTube channels in a RecyclerView.
 * This adapter uses ListAdapter for efficient updates when used with LiveData.
 */
public class ChannelAdapter extends ListAdapter<ChannelInfo, ChannelAdapter.ChannelViewHolder> {

    private final OnChannelClickListener listener;

    /**
     * Interface for handling click events on items in the RecyclerView.
     */
    public interface OnChannelClickListener {
        void onChannelClick(ChannelInfo item);
    }

    /**
     * Constructor for the ChannelAdapter.
     * @param listener A listener to handle item clicks.
     */
    public ChannelAdapter(OnChannelClickListener listener) {
        super(DIFF_CALLBACK);
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
        // Get the data item for this position
        ChannelInfo currentItem = getItem(position);
        holder.bind(currentItem, listener);
    }

    /**
     * ViewHolder for a single channel item.
     * It holds the views and binds the data to them.
     */
    public static class ChannelViewHolder extends RecyclerView.ViewHolder {
        public ImageView channelIcon;
        public TextView channelDescription;

        public ChannelViewHolder(View v) {
            super(v);
            channelIcon = v.findViewById(R.id.channel_icon);
            channelDescription = v.findViewById(R.id.channel_description);
        }

        /**
         * Binds a ChannelInfo object to the ViewHolder's views.
         * @param item The ChannelInfo object containing the data.
         * @param listener The listener to handle clicks on the item view.
         */
        public void bind(final ChannelInfo item, final OnChannelClickListener listener) {
            channelDescription.setText(item.title);
            Picasso.get().load(item.thumbnailUrl).into(channelIcon);
            itemView.setOnClickListener(v -> listener.onChannelClick(item));
        }
    }

    /**
     * A DiffUtil.ItemCallback implementation that helps ListAdapter determine
     * the differences between two lists, enabling smooth animations.
     */
    private static final DiffUtil.ItemCallback<ChannelInfo> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ChannelInfo>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChannelInfo oldItem, @NonNull ChannelInfo newItem) {
                    // Check if items are the same by their unique ID
                    return oldItem.channelId.equals(newItem.channelId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChannelInfo oldItem, @NonNull ChannelInfo newItem) {
                    // Check if the visual content of the items has changed
                    return oldItem.title.equals(newItem.title) &&
                            oldItem.thumbnailUrl.equals(newItem.thumbnailUrl);
                }
            };
}
