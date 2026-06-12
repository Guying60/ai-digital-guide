package com.example.digitaltourguide.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.user.ReviewItem;

import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private List<ReviewItem> reviewList;

    public ReviewAdapter(List<ReviewItem> reviewList) {
        this.reviewList = reviewList;
    }

    public void updateData(List<ReviewItem> newList) {
        this.reviewList = newList;
        notifyDataSetChanged();
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
        ReviewItem item = reviewList.get(position);

        // 景点名称
        holder.tvPlaceName.setText(item.getPlaceName());

        // 日期
        holder.tvDate.setText(item.getDate());

        // 封面图（示例用 ic_pdf 占位，可替换为 Glide 加载网络图片）
        holder.ivPlaceImage.setImageResource(item.getPlaceImageRes());

        if (item.isReviewed()) {
            // ── 已评价状态 ──
            holder.tvStatus.setText("已评价");
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_reviewed);
            holder.tvStatus.setTextColor(holder.itemView.getContext()
                    .getColor(R.color.profile_primary));

            // 显示评分区域，隐藏待评价提示
            holder.starContainer.setVisibility(View.VISIBLE);
            holder.pendingContainer.setVisibility(View.GONE);

            // 设置星级
            int fullStars = (int) item.getRating();
            for (int i = 0; i < 5; i++) {
                ImageView star = holder.stars[i];
                if (star != null) {
                    star.setImageResource(i < fullStars
                            ? R.drawable.ic_star_filled
                            : R.drawable.ic_star_outline);
                }
            }

            // 评分数字
            holder.tvRating.setText(String.valueOf(item.getRating()));

            // 评论文本
            holder.tvReviewText.setText(item.getReviewText());
            holder.tvReviewText.setVisibility(View.VISIBLE);

            // 标签
            List<String> tags = item.getTags();
            if (tags != null && !tags.isEmpty()) {
                holder.tagsContainer.setVisibility(View.VISIBLE);
                TextView[] tagViews = {holder.tag1, holder.tag2, holder.tag3};
                for (int i = 0; i < tagViews.length; i++) {
                    if (i < tags.size()) {
                        tagViews[i].setText(tags.get(i));
                        tagViews[i].setVisibility(View.VISIBLE);
                    } else {
                        tagViews[i].setVisibility(View.GONE);
                    }
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

            // 隐藏评分区，显示待评价提示
            holder.starContainer.setVisibility(View.GONE);
            holder.pendingContainer.setVisibility(View.VISIBLE);

            // 隐藏评论文本
            holder.tvReviewText.setVisibility(View.GONE);
            holder.tagsContainer.setVisibility(View.GONE);

            // 按钮：隐藏删除，显示去评价
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnRate.setVisibility(View.VISIBLE);
        }
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