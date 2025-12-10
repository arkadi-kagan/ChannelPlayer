package com.channelplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.channelplayer.cache.VideoItem;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

/**
 * An adapter for displaying a list of videos. It uses ListAdapter for efficient
 * list updates and includes a filter for searching.
 */
public class VideoAdapter extends ListAdapter<VideoItem, VideoAdapter.VideoViewHolder> implements Filterable {

    private final OnVideoClickListener listener;
    // This list holds the original, unfiltered data provided by submitList.
    private List<VideoItem> originalList = new ArrayList<>();

    public interface OnVideoClickListener {
        void onVideoClick(VideoItem item);
    }

    public VideoAdapter(OnVideoClickListener listener) {
        // Pass the DiffUtil callback to the super constructor.
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.video_list_item, parent, false);
        return new VideoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        // Use getItem(position) which is provided by ListAdapter
        VideoItem currentItem = getItem(position);
        holder.bind(currentItem, listener);
    }

    /**
     * Override submitList to keep a copy of the original list for filtering.
     */
    @Override
    public void submitList(List<VideoItem> list) {
        // Keep a copy of the master list.
        this.originalList = list == null ? new ArrayList<>() : new ArrayList<>(list);
        super.submitList(this.originalList);
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        public ImageView videoThumbnail;
        public TextView videoDescription;

        public VideoViewHolder(View v) {
            super(v);
            videoThumbnail = v.findViewById(R.id.video_thumbnail);
            videoDescription = v.findViewById(R.id.video_title);
        }

        public void bind(final VideoItem item, final OnVideoClickListener listener) {
            // As requested, we use 'description' for the title.
            videoDescription.setText(item.description);
            if (item.thumbnailUrl != null && !item.thumbnailUrl.isEmpty()) {
                Picasso.get().load(item.thumbnailUrl).into(videoThumbnail);
            }
            itemView.setOnClickListener(v -> listener.onVideoClick(item));
        }
    }

    /**
     * This callback is the magic behind ListAdapter. It tells the adapter how to
     * efficiently calculate changes between two lists.
     */
    private static final DiffUtil.ItemCallback<VideoItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<VideoItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull VideoItem oldItem, @NonNull VideoItem newItem) {
            // Check for uniqueness based on ID
            return oldItem.videoId.equals(newItem.videoId);
        }

        @Override
        public boolean areContentsTheSame(@NonNull VideoItem oldItem, @NonNull VideoItem newItem) {
            // Check if the visual content has changed
            return oldItem.description.equals(newItem.description) &&
                    oldItem.thumbnailUrl.equals(newItem.thumbnailUrl);
        }
    };

    @Override
    public Filter getFilter() {
        return videoFilter;
    }

    private final Filter videoFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<VideoItem> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(originalList);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (VideoItem item : originalList) {
                    if (item.description.toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            // We call super.submitList here to display the filtered list,
            // which will still be efficiently diffed and updated.
            List<VideoItem> filteredList = (List<VideoItem>) results.values;
            if (filteredList == null) {
                filteredList = new ArrayList<>();
            }
            // Use the parent's submitList to update the UI with the filtered results.
            VideoAdapter.super.submitList(filteredList);
        }
    };
}
