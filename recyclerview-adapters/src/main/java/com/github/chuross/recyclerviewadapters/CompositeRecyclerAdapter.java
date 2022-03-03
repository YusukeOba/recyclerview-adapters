package com.github.chuross.recyclerviewadapters;

import android.view.View;
import android.view.ViewGroup;

import com.github.chuross.recyclerviewadapters.internal.LocalAdapterDataObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import static com.github.chuross.recyclerviewadapters.internal.RecyclerAdaptersUtils.checkNonNull;

public class CompositeRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements LocalAdapter<RecyclerView.ViewHolder> {

    private List<LocalAdapter<?>> localAdapters = new ArrayList<>();
    private List<LocalAdapterDataObserver> observers = new ArrayList<>();
    private Map<Integer, LocalAdapter<?>> localAdapterMapping = new HashMap<>();
    private Map<Integer, LocalAdapter<?>> unstableAdapterMapping = new HashMap<>();
    private WeakReference<RecyclerView> recyclerView;
    private View.OnAttachStateChangeListener recyclerViewAttachStateChangeListener;
    private WeakReference<CompositeRecyclerAdapter> parentAdapter;
    private boolean visible = true;

    @Override
    public final int getItemViewType(final int position) {
        final LocalAdapterItem item = getLocalAdapterItem(position);

        if (item == null) throw new IllegalStateException("LocalAdapterItem is not found.");

        final LocalAdapter<?> localAdapter = item.getLocalAdapter();

        if (localAdapter.hasStableItemViewType()) {
            return localAdapter.getAdapterId();
        }

        final int itemViewType = localAdapter.getItemViewType(item.getLocalAdapterPosition());
        unstableAdapterMapping.put(itemViewType, localAdapter);
        return itemViewType;
    }

    @Override
    public void onAttachedToRecyclerView(final RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = new WeakReference<>(recyclerView);
        recyclerViewAttachStateChangeListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                clear();
                if (hasRecyclerView()) {
                    CompositeRecyclerAdapter.this.recyclerView.get().removeOnAttachStateChangeListener(recyclerViewAttachStateChangeListener);
                }
            }
        };
        recyclerView.addOnAttachStateChangeListener(recyclerViewAttachStateChangeListener);
        for (LocalAdapter<?> localAdapter : localAdapters) {
            localAdapter.onAttachedToRecyclerView(recyclerView);
        }
    }

    @NonNull
    @Override
    public final RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int itemViewType) {
        if (localAdapterMapping.containsKey(itemViewType)) {
            return localAdapterMapping.get(itemViewType).onCreateViewHolder(parent, itemViewType);
        }
        return unstableAdapterMapping.get(itemViewType).onCreateViewHolder(parent, itemViewType);
    }

    @Override
    public void onViewRecycled(RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        LocalAdapterItem localAdapterItem = getLocalAdapterItem(holder.getAdapterPosition());

        if (localAdapterItem == null) return;

        localAdapterItem.getLocalAdapter().onViewRecycled(holder);
    }

    @Override
    public boolean onFailedToRecycleView(RecyclerView.ViewHolder holder) {
        LocalAdapterItem localAdapterItem = getLocalAdapterItem(holder.getAdapterPosition());

        if (localAdapterItem == null) return super.onFailedToRecycleView(holder);

        localAdapterItem.getLocalAdapter().onFailedToRecycleView(holder);
        return super.onFailedToRecycleView(holder);
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        LocalAdapterItem localAdapterItem = getLocalAdapterItem(holder.getAdapterPosition());

        if (localAdapterItem == null) return;

        localAdapterItem.getLocalAdapter().onViewAttachedToWindow(holder);
    }

    @Override
    public final void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        final LocalAdapterItem localAdapterItem = getLocalAdapterItem(position);

        if (localAdapterItem == null) return;

        localAdapterItem.getLocalAdapter().onBindViewHolder(holder, localAdapterItem.getLocalAdapterPosition());
    }

    @Override
    public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {
        LocalAdapterItem localAdapterItem = getLocalAdapterItem(holder.getAdapterPosition());

        if (localAdapterItem == null) return;

        localAdapterItem.getLocalAdapter().onViewDetachedFromWindow(holder);
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        for (LocalAdapter<?> localAdapter : localAdapters) {
            localAdapter.onDetachedFromRecyclerView(recyclerView);
        }
        super.onDetachedFromRecyclerView(recyclerView);
        this.recyclerView = null;
    }

    private void cleanUnstableAdapterMapping(@NonNull LocalAdapter<?> targetLocalAdapter) {
        for (Map.Entry<Integer, LocalAdapter<?>> entry : unstableAdapterMapping.entrySet()) {
            if (entry.getValue().equals(targetLocalAdapter)) {
                unstableAdapterMapping.remove(entry.getKey());
            }
        }
    }

    public RecyclerView getAttachedRecyclerView() {
        return recyclerView != null && recyclerView.get() != null ? recyclerView.get() : null;
    }

    public int positionOf(LocalAdapter<?> targetLocalAdapter) {
        int offset = 0;
        for (LocalAdapter<?> localAdapter : localAdapters) {
            if (!localAdapter.isVisible()) continue;

            if (localAdapter.equals(targetLocalAdapter)) return offset;

            offset += localAdapter.getItemCount();
        }
        return offset;
    }

    @Override
    public int getItemCount() {
        return getTotalCount();
    }

    private int getTotalCount() {
        int size = 0;
        for (LocalAdapter localAdapter : localAdapters) {
            if (!localAdapter.isVisible()) continue;

            size += localAdapter.getItemCount();
        }
        return size;
    }

    public int getLocalAdapterCount() {
        return localAdapters.size();
    }

    @Nullable
    public LocalAdapterItem getLocalAdapterItem(final int position) {
        if (position < 0) return null;

        int offset = 0;
        for (LocalAdapter localAdapter : localAdapters) {
            if (!localAdapter.isVisible()) continue;

            if (position < (offset + localAdapter.getItemCount())) {
                int localAdapterPosition = position - offset;
                return new LocalAdapterItem(localAdapterPosition, localAdapter);
            }
            offset += localAdapter.getItemCount();
        }
        return null;
    }

    public void add(@NonNull LocalAdapter<?> localAdapter) {
        addAll(localAdapter);
    }

    public void add(int index, @NonNull LocalAdapter<?> localAdapter) {
        addAll(index, localAdapter);
    }

    public void addAll(@NonNull Collection<LocalAdapter<?>> localAdapters) {
        checkNonNull(localAdapters);
        addAll(this.localAdapters.size(), localAdapters.toArray(new LocalAdapter[localAdapters.size()]));
    }

    public void addAll(@NonNull LocalAdapter<?>... localAdapters) {
        checkNonNull(localAdapters);
        addAll(this.localAdapters.size(), localAdapters);
    }

    public void addAll(int index, @NonNull LocalAdapter<?>... localAdapters) {
        checkNonNull(localAdapters);

        if (localAdapters.length == 0) return;

        if (localAdapters.length > 1) {
            this.localAdapters.addAll(index, Arrays.asList(localAdapters));
        } else {
            this.localAdapters.add(index, localAdapters[0]);
        }

        for (LocalAdapter localAdapter : localAdapters) {
            if (localAdapter.hasStableItemViewType())
                localAdapterMapping.put(localAdapter.getAdapterId(), localAdapter);
            final LocalAdapterDataObserver observer = new LocalAdapterDataObserver(this, localAdapter);
            localAdapter.bindParentAdapter(this, observer);
            observers.add(observer);
            if (hasRecyclerView()) localAdapter.onAttachedToRecyclerView(recyclerView.get());
        }
        notifyDataSetChanged();
    }

    public void remove(@NonNull LocalAdapter<?> localAdapter) {
        checkNonNull(localAdapter);
        localAdapters.remove(localAdapter);
        localAdapterMapping.remove(localAdapter.getAdapterId());
        cleanUnstableAdapterMapping(localAdapter);
        localAdapter.unBindParentAdapter();
        if (hasRecyclerView()) localAdapter.onDetachedFromRecyclerView(recyclerView.get());
        notifyDataSetChanged();
    }

    public void clear() {
        for (LocalAdapter<?> localAdapter : localAdapters) {
            localAdapter.unBindParentAdapter();
            if (hasRecyclerView()) localAdapter.onDetachedFromRecyclerView(recyclerView.get());
        }
        localAdapters.clear();
        localAdapterMapping.clear();
        unstableAdapterMapping.clear();
        for (LocalAdapterDataObserver observer : observers) {
            unregisterAdapterDataObserver(observer);
        }
        observers.clear();
        notifyDataSetChanged();
    }

    /**
     * nested support
     */
    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        setVisible(visible, false);
    }

    public void setVisible(boolean visible, boolean animated) {
        if (isVisible() == visible) return;

        if (!animated) {
            this.visible = visible;
            notifyDataSetChanged();
            return;
        }

        if (visible) {
            this.visible = visible;
            notifyItemRangeInserted(0, getTotalCount());
        } else {
            notifyItemRangeRemoved(0, getTotalCount());
            this.visible = visible;
        }
    }

    @Override
    public int getAdapterId() {
        return 0;
    }

    @Override
    public boolean hasStableItemViewType() {
        return false;
    }

    @Override
    public CompositeRecyclerAdapter getParentAdapter() {
        return parentAdapter != null ? parentAdapter.get() : null;
    }

    @Override
    public void bindParentAdapter(@Nullable CompositeRecyclerAdapter adapter, @Nullable RecyclerView.AdapterDataObserver dataObserver) {
        if (hasParentAdapter()) {
            throw new IllegalStateException("Adapter already has parentAdapter.");
        }
        parentAdapter = new WeakReference<>(adapter);
        registerAdapterDataObserver(dataObserver);
    }

    @Override
    public void unBindParentAdapter() {
        parentAdapter.clear();
        parentAdapter = null;
    }

    @Override
    public boolean hasParentAdapter() {
        return parentAdapter != null && parentAdapter.get() != null;
    }

    public boolean hasRecyclerView() {
        return recyclerView != null && recyclerView.get() != null;
    }

}