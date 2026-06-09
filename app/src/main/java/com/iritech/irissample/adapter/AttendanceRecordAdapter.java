package com.iritech.irissample.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.iritech.irissample.R;
import com.iritech.irissample.model.AttendanceRecord;

import java.util.List;

/**
 * Adapter để hiển thị danh sách điểm danh trong RecyclerView.
 * Hiển thị: Mã SV | Họ tên | Trạng thái | Ngày | Giờ
 */
public class AttendanceRecordAdapter extends RecyclerView.Adapter<AttendanceRecordAdapter.ViewHolder> {

    private Context context;
    private List<AttendanceRecord> recordList;

    public AttendanceRecordAdapter(Context context, List<AttendanceRecord> recordList) {
        this.context = context;
        this.recordList = recordList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendance_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord record = recordList.get(position);

        holder.textStudentId.setText(record.getStudentId());
        holder.textFullName.setText(record.getFullName());

        if (record.isAttended()) {
            holder.textStatus.setText("OK");
            holder.textStatus.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            holder.textDate.setText(record.getShortDate());
            holder.textTime.setText(record.getShortTime());
            holder.textDate.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            holder.textTime.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
        } else {
            holder.textStatus.setText("X");
            holder.textStatus.setTextColor(context.getResources().getColor(android.R.color.holo_red_light));
            holder.textDate.setText("--");
            holder.textTime.setText("--");
            holder.textDate.setTextColor(context.getResources().getColor(android.R.color.holo_red_light));
            holder.textTime.setTextColor(context.getResources().getColor(android.R.color.holo_red_light));
        }

        // Alternate row background
        if (position % 2 == 0) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(android.R.color.white));
        } else {
            holder.itemView.setBackgroundColor(0xFFF5F5F5); // Light gray
        }
    }

    @Override
    public int getItemCount() {
        return recordList != null ? recordList.size() : 0;
    }

    /**
     * Cập nhật danh sách mới
     */
    public void updateList(List<AttendanceRecord> newList) {
        this.recordList = newList;
        notifyDataSetChanged();
    }

    /**
     * Lấy số lượng sinh viên đã điểm danh
     */
    public int getAttendedCount() {
        int count = 0;
        if (recordList != null) {
            for (AttendanceRecord record : recordList) {
                if (record.isAttended()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textStudentId;
        TextView textFullName;
        TextView textStatus;
        TextView textDate;
        TextView textTime;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textStudentId = itemView.findViewById(R.id.textStudentId);
            textFullName = itemView.findViewById(R.id.textFullName);
            textStatus = itemView.findViewById(R.id.textStatus);
            textDate = itemView.findViewById(R.id.textDate);
            textTime = itemView.findViewById(R.id.textTime);
        }
    }
}
