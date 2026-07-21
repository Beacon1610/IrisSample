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

public final class StudentAttendanceDetailAdapter
        extends RecyclerView.Adapter<StudentAttendanceDetailAdapter.ViewHolder> {

    private final Context context;
    private final List<AttendanceRecord> items;

    public StudentAttendanceDetailAdapter(Context context, List<AttendanceRecord> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(
                R.layout.item_student_attendance_detail,
                parent,
                false
        );
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord record = items.get(position);

        holder.textDate.setText(record.getCheckinDate());
        holder.textTime.setText("Giờ: " + (record.isAttended() ? record.getShortTime() : "--"));

        String cutoff = record.getLateCutoffTime() == null
                || record.getLateCutoffTime().trim().isEmpty()
                ? "--"
                : record.getLateCutoffTime();
        holder.textCutoff.setText("Mốc muộn: " + cutoff);

        if (record.isLate()) {
            holder.textStatus.setText("Đi muộn");
            holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.colorWarning));
            holder.textStatus.setBackgroundResource(R.drawable.badge_attendance_late);
        } else if (record.isAttended()) {
            holder.textStatus.setText("Có mặt");
            holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.colorSuccess));
            holder.textStatus.setBackgroundResource(R.drawable.badge_attendance_present);
        } else {
            holder.textStatus.setText("Vắng");
            holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.colorError));
            holder.textStatus.setBackgroundResource(R.drawable.badge_attendance_absent);
        }
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textDate;
        final TextView textStatus;
        final TextView textTime;
        final TextView textCutoff;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textDate = itemView.findViewById(R.id.textDetailDate);
            textStatus = itemView.findViewById(R.id.textDetailStatus);
            textTime = itemView.findViewById(R.id.textDetailTime);
            textCutoff = itemView.findViewById(R.id.textDetailCutoff);
        }
    }
}