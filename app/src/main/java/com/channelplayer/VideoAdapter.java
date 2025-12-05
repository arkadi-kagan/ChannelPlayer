package com.channelplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> implements Filterable {

    private List<VideoItem> videoList;
    private List<VideoItem> videoListFull;
    private OnVideoClickListener listener;

    public interface OnVideoClickListener {
        void onVideoClick(VideoItem item);
    }

    public static class VideoItem {
        private final String videoId;
        private final String title;
        private final String thumbnailUrl;

        public VideoItem(String videoId, String title, String thumbnailUrl) {
            this.videoId = videoId;
            this.title = title;
            this.thumbnailUrl = thumbnailUrl;
        }

        public String getVideoId() {
            return videoId;
        }

        public String getTitle() {
            return title;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        public ImageView videoThumbnail;
        public TextView videoTitle;

        public VideoViewHolder(View v) {
            super(v);
            videoThumbnail = v.findViewById(R.id.video_thumbnail);
            videoTitle = v.findViewById(R.id.video_title);
        }

        public void bind(final VideoItem item, final OnVideoClickListener listener) {
            itemView.setOnClickListener(v -> listener.onVideoClick(item));
        }
    }

    public VideoAdapter(List<VideoItem> videoList, OnVideoClickListener listener) {
        this.videoList = videoList;
        this.videoListFull = new ArrayList<>(videoList);
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
        VideoItem currentItem = videoList.get(position);
        holder.videoTitle.setText(currentItem.getTitle());
        Picasso.get().load(currentItem.getThumbnailUrl()).into(holder.videoThumbnail);
        holder.bind(currentItem, listener);
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public void updateList(List<VideoItem> newList) {
        videoList.clear();
        videoList.addAll(newList);
        videoListFull = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public void addItems(List<VideoItem> newItems) {
        int startPosition = videoList.size();
        videoList.addAll(newItems);
        videoListFull.addAll(newItems);
        notifyItemRangeInserted(startPosition, newItems.size());
    }

    @Override
    public Filter getFilter() {
        return videoFilter;
    }

    private Filter videoFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<VideoItem> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(videoListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (VideoItem item : videoListFull) {
                    if (item.getTitle().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            videoList.clear();
            if (results.values != null) {
                // The cast is safe because we know the type of the list we created in performFiltering
                videoList.addAll((List<VideoItem>) results.values);
            }
            notifyDataSetChanged();
        }
    };
}
