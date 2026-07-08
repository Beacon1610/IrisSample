package com.iritech.irissample.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.iritech.irissample.R;
import com.iritech.irissample.model.StudentAttendanceStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StudentAttendanceStatsAdapter
        extends RecyclerView.Adapter<StudentAttendanceStatsAdapter.ViewHolder> {

    public interface OnStudentClickListener {
        void onStudentClick(StudentAttendanceStats stats);
    }
    private final OnStudentClickListener listener;
    private final Context context;
    private List<StudentAttendanceStats> items;

    public StudentAttendanceStatsAdapter(
            Context context,
            List<StudentAttendanceStats> items,
            OnStudentClickListener listener
    ) {
        this.context = context;
        this.items = items == null ? new ArrayList<>() : items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(
                R.layout.item_student_attendance_stats,
                parent,
                false
        );
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentAttendanceStats stats = items.get(position);
        String studentId = stats.getStudentId() == null ? "" : stats.getStudentId();
        String studentName = stats.getStudentName() == null ? "" : stats.getStudentName();
        int rate = stats.getRoundedAttendanceRate();
        int lateRate = stats.getRoundedLateRate();

        holder.textStudentName.setText(studentName);
        holder.textStudentId.setText(studentId);
        holder.textPresentSessions.setText(String.format(
                Locale.getDefault(),
                "Có mặt: %d/%d buổi",
                stats.getPresentCount(),
                stats.getTotalSessions()
        ));
        holder.textLateSessions.setText(String.format(
                Locale.getDefault(),
                "Muộn: %d buổi (%d%%)",
                stats.getLateCount(),
                lateRate
        ));
        holder.textAbsentSessions.setText(String.format(
                Locale.getDefault(),
                "Vắng: %d buổi",
                stats.getAbsentCount()
        ));
        holder.textAttendanceRate.setText(String.format(
                Locale.getDefault(),
                "Tỷ lệ: %d%%",
                rate
        ));
        holder.progressAttendanceRate.setProgress(rate);

        boolean lowAttendance = stats.needsAttention();
        boolean oftenLate = stats.getTotalSessions() > 0 && stats.getLateRate() >= 30d;
        boolean needsAttention = lowAttendance || oftenLate;
        holder.textAttention.setVisibility(needsAttention ? View.VISIBLE : View.GONE);

        if (needsAttention) {
            holder.textAttention.setText(lowAttendance ? "Cần chú ý" : "Hay đi muộn");
            holder.textAttention.setTextColor(ContextCompat.getColor(
                    context,
                    lowAttendance ? R.color.colorError : R.color.colorWarning
            ));
            holder.textAttention.setBackgroundResource(
                    lowAttendance
                            ? R.drawable.badge_attendance_absent
                            : R.drawable.badge_attendance_late
            );
        }

        int rateColor = ContextCompat.getColor(
                context,
                lowAttendance ? R.color.colorError : R.color.colorSuccess
        );
        holder.textAttendanceRate.setTextColor(rateColor);
        holder.progressAttendanceRate.setProgressTintList(
                android.content.res.ColorStateList.valueOf(rateColor)
        );

        holder.itemView.setContentDescription(
                String.format(
                Locale.getDefault(),
                "%s, %s, có mặt %d trên %d buổi, đi muộn %d buổi, tỷ lệ đi muộn %d phần trăm, vắng %d buổi, tỷ lệ %d phần trăm%s",
                studentId,
                studentName,
                stats.getPresentCount(),
                stats.getTotalSessions(),
                stats.getLateCount(),
                lateRate,
                stats.getAbsentCount(),
                rate,
                lowAttendance ? ", cần chú ý" : oftenLate ? ", hay đi muộn" : ""
        ));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStudentClick(stats);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateList(List<StudentAttendanceStats> newItems) {
        items = newItems == null ? new ArrayList<>() : newItems;
        notifyDataSetChanged();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textStudentName;
        final TextView textStudentId;
        final TextView textPresentSessions;
        final TextView textLateSessions;
        final TextView textAbsentSessions;
        final TextView textAttendanceRate;
        final TextView textAttention;
        final ProgressBar progressAttendanceRate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textStudentName = itemView.findViewById(R.id.textStatsStudentName);
            textStudentId = itemView.findViewById(R.id.textStatsStudentId);
            textPresentSessions = itemView.findViewById(R.id.textPresentSessions);
            textLateSessions = itemView.findViewById(R.id.textLateSessions);
            textAbsentSessions = itemView.findViewById(R.id.textAbsentSessions);
            textAttendanceRate = itemView.findViewById(R.id.textStudentAttendanceRate);
            textAttention = itemView.findViewById(R.id.textAttendanceAttention);
            progressAttendanceRate = itemView.findViewById(R.id.progressStudentAttendanceRate);
        }
    }
}
