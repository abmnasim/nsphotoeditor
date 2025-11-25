package com.example.nsphotoeditor.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nsphotoeditor.R;
import com.example.nsphotoeditor.callbacks.FrameListener;

import java.util.List;

public class FrameAdapter extends RecyclerView.Adapter<FrameAdapter.Holder> {

    private List<Integer> frames;
    private FrameListener listener;

    public FrameAdapter(List<Integer> frames, FrameListener listener) {
        this.frames = frames;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.frame_item, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.image.setImageResource(frames.get(position));
        holder.image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onFrameClicked(position + 1);
            }
        });
    }

    @Override
    public int getItemCount() {
        return frames.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView image;
        Holder(View v) {
            super(v);;
            image = v.findViewById(R.id.frame_icon);
        }
    }
}
