package com.iritech.irissample.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.iritech.irissample.R;
import com.iritech.irissample.model.AttendanceRecord;

import java.util.List;

/**
 * Adapter để hiển thị danh sách điểm danh trong RecyclerView.
 * Hiển thị: Mã SV | Họ tên | Trạng thái | Giờ điểm danh
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

        String studentId = record.getStudentId() == null ? "" : record.getStudentId();
        String fullName = record.getFullName() == null ? "" : record.getFullName();

        holder.textStudentId.setText(studentId);
        holder.textFullName.setText(fullName);

        if (record.isLate()) {
            holder.textStatus.setText("Đi muộn");
            holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.colorWarning));
            holder.textStatus.setBackgroundResource(R.drawable.badge_attendance_late);
            holder.textTime.setText("Giờ: " + record.getShortTime());
            holder.textTime.setTextColor(ContextCompat.getColor(context, R.color.colorWarning));
        } else if (record.isAttended()) {
            holder.textStatus.setText("Có mặt");
            holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.colorSuccess));
            holder.textStatus.setBackgroundResource(R.drawable.badge_attendance_present);
            holder.textTime.setText("Giờ: " + record.getShortTime());
            holder.textTime.setTextColor(ContextCompat.getColor(context, R.color.colorPrimaryDark));
        } else {
            holder.textStatus.setText("Vắng");
            holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.colorError));
            holder.textStatus.setBackgroundResource(R.drawable.badge_attendance_absent);
            holder.textTime.setText("Giờ: --");
            holder.textTime.setTextColor(ContextCompat.getColor(context, R.color.colorTextHint));
        }

        holder.itemView.setContentDescription(
                studentId + ", " + fullName + ", " +
                        (record.isLate()
                                ? "Đi muộn lúc " + record.getShortTime()
                                : record.isAttended()
                                ? "Có mặt lúc " + record.getShortTime()
                                : "Vắng")
        );
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
        TextView textTime;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textStudentId = itemView.findViewById(R.id.textStudentId);
            textFullName = itemView.findViewById(R.id.textFullName);
            textStatus = itemView.findViewById(R.id.textStatus);
            textTime = itemView.findViewById(R.id.textTime);
        }
    }
}
