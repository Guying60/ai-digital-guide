package com.example.digitaltourguide.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.user.UserReviewItem;

import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private List<UserReviewItem> reviewList;

    private OnItemClickListener onDeleteClickListener;
    private OnItemClickListener onRateClickListener;

    public interface OnItemClickListener {
        void onItemClick(UserReviewItem item, int position);
    }

    public ReviewAdapter(List<UserReviewItem> reviewList) {
        this.reviewList = reviewList;
    }

    public void updateData(List<UserReviewItem> newList) {
        this.reviewList = newList;
        notifyDataSetChanged();
    }

    public void setOnDeleteClickListener(OnItemClickListener listener) {
        this.onDeleteClickListener = listener;
    }

    public void setOnRateClickListener(OnItemClickListener listener) {
        this.onRateClickListener = listener;
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review_card, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        UserReviewItem item = reviewList.get(position);

        // 景点名称
        holder.tvPlaceName.setText(item.getAttractionName());

        // 日期
        holder.tvDate.setText(item.getCreateTime());

        // 封面图（Glide 加载网络图片）
        if (item.getCoverUrl() != null && !item.getCoverUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(item.getCoverUrl())
                    .placeholder(R.drawable.ic_pdf)
                    .error(R.drawable.ic_pdf)
                    .centerCrop()
                    .into(holder.ivPlaceImage);
        } else {
            holder.ivPlaceImage.setImageResource(R.drawable.ic_pdf);
        }

        if (item.isReviewed()) {
            // ── 已评价状态 ──
            holder.tvStatus.setText("已评价");
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_reviewed);
            holder.tvStatus.setTextColor(holder.itemView.getContext()
                    .getColor(R.color.profile_primary));

            holder.starContainer.setVisibility(View.VISIBLE);
            holder.pendingContainer.setVisibility(View.GONE);

            // 设置星级（支持半星，这里简化处理为整星）
            double rating = item.getRating() != null ? item.getRating() : 0;
            int fullStars = (int) Math.round(rating);
            int goldColor = holder.itemView.getContext().getColor(R.color.profile_star_yellow);
            int grayColor = holder.itemView.getContext().getColor(R.color.profile_outline_variant);
            for (int i = 0; i < 5; i++) {
                ImageView star = holder.stars[i];
                if (star != null) {
                    star.setImageResource(R.drawable.ic_star);
                    star.setImageTintList(android.content.res.ColorStateList.valueOf(
                            i < fullStars ? goldColor : grayColor));
                }
            }

            holder.tvRating.setText(String.format("%.1f", rating));

            // 评论文本
            if (item.getContent() != null && !item.getContent().isEmpty()) {
                holder.tvReviewText.setText(item.getContent());
                holder.tvReviewText.setVisibility(View.VISIBLE);
            } else {
                holder.tvReviewText.setVisibility(View.GONE);
            }

            // 标签
            List<String> tags = item.getTags();
            if (tags != null && !tags.isEmpty()) {
                holder.tagsContainer.setVisibility(View.VISIBLE);
                holder.tag1.setVisibility(View.GONE);
                holder.tag2.setVisibility(View.GONE);
                holder.tag3.setVisibility(View.GONE);
                for (int i = 0; i < Math.min(tags.size(), 3); i++) {
                    TextView[] tagViews = {holder.tag1, holder.tag2, holder.tag3};
                    tagViews[i].setText(tags.get(i));
                    tagViews[i].setVisibility(View.VISIBLE);
                }
            } else {
                holder.tagsContainer.setVisibility(View.GONE);
            }

            // 按钮：显示删除，隐藏去评价
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnRate.setVisibility(View.GONE);

        } else {
            // ── 待评价状态 ──
            holder.tvStatus.setText("待评价");
            holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_filter_unselected);
            holder.tvStatus.setTextColor(holder.itemView.getContext()
                    .getColor(R.color.profile_on_surface_variant));

            holder.starContainer.setVisibility(View.GONE);
            holder.pendingContainer.setVisibility(View.VISIBLE);

            holder.tvReviewText.setVisibility(View.GONE);
            holder.tagsContainer.setVisibility(View.GONE);

            // 按钮：隐藏删除，显示去评价
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setText("删除");
            holder.btnRate.setVisibility(View.VISIBLE);
        }

        // 删除按钮点击
        holder.btnDelete.setOnClickListener(v -> {
            if (onDeleteClickListener != null) {
                onDeleteClickListener.onItemClick(item, holder.getAdapterPosition());
            }
        });

        // 去评价按钮点击
        holder.btnRate.setOnClickListener(v -> {
            if (onRateClickListener != null) {
                onRateClickListener.onItemClick(item, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return reviewList == null ? 0 : reviewList.size();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPlaceImage;
        TextView tvPlaceName, tvStatus, tvDate, tvRating, tvReviewText;
        LinearLayout starContainer, pendingContainer, tagsContainer;
        ImageView[] stars = new ImageView[5];
        TextView tag1, tag2, tag3;
        TextView btnDelete, btnRate;

        ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPlaceImage = itemView.findViewById(R.id.review_place_image);
            tvPlaceName = itemView.findViewById(R.id.review_place_name);
            tvStatus = itemView.findViewById(R.id.review_status);
            tvDate = itemView.findViewById(R.id.review_date);
            tvRating = itemView.findViewById(R.id.review_rating);
            tvReviewText = itemView.findViewById(R.id.review_text);
            starContainer = itemView.findViewById(R.id.review_star_container);
            pendingContainer = itemView.findViewById(R.id.review_pending_container);
            tagsContainer = itemView.findViewById(R.id.review_tags_container);
            stars[0] = itemView.findViewById(R.id.star_1);
            stars[1] = itemView.findViewById(R.id.star_2);
            stars[2] = itemView.findViewById(R.id.star_3);
            stars[3] = itemView.findViewById(R.id.star_4);
            stars[4] = itemView.findViewById(R.id.star_5);
            tag1 = itemView.findViewById(R.id.review_tag_1);
            tag2 = itemView.findViewById(R.id.review_tag_2);
            tag3 = itemView.findViewById(R.id.review_tag_3);
            btnDelete = itemView.findViewById(R.id.review_btn_delete);
            btnRate = itemView.findViewById(R.id.review_btn_rate);
        }
    }
}