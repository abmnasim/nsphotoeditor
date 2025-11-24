package com.example.nsphotoeditor.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nsphotoeditor.R;
import com.example.nsphotoeditor.callbacks.FeatureListener;
import com.example.nsphotoeditor.utils.FeatureItem;

import java.util.List;

public class FeatureAdapter extends RecyclerView.Adapter<FeatureAdapter.FeatureViewHolder> {

    private final List<FeatureItem> items;
    private FeatureListener listener;

    public  FeatureAdapter(List<FeatureItem> items, FeatureListener listener) {
        this.items = items;
        this.listener = listener;
    }
    @NonNull
    @Override
    public FeatureViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.feature_item, parent, false);

        return new FeatureViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FeatureViewHolder holder, int position) {
        final FeatureItem fi = items.get(position);
        holder.name.setText(fi.name);
        holder.icon.setImageResource(fi.iconRes);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onFeatureClicked(fi);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FeatureViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        ImageView icon;
        FeatureViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.feature_icon);
            name = itemView.findViewById(R.id.feature_name);
        }
    }
}
