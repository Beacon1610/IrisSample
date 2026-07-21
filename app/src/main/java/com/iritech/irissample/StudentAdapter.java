package com.iritech.irissample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

public class StudentAdapter extends BaseAdapter {

    private Context context;
    private ArrayList<String> studentNames;
    private ArrayList<String> studentIds;
    private ArrayList<String> avatarPaths;
    private OnStudentActionListener listener;

    public interface OnStudentActionListener {
        void onEnrollClick(String studentId);
        void onUnenrollClick(String studentId);
        void onEditClick(String studentId);
        void onCheckInClick(String studentId);
    }

    public StudentAdapter(
            Context context,
            ArrayList<String> studentNames,
            ArrayList<String> studentIds,
            ArrayList<String> avatarPaths,
            OnStudentActionListener listener
    ) {
        this.context = context;
        this.studentNames = studentNames;
        this.studentIds = studentIds;
        this.avatarPaths = avatarPaths;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return studentNames.size();
    }

    @Override
    public Object getItem(int position) {
        return studentNames.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_student, parent, false);

            holder = new ViewHolder();
            holder.imgAvatar = convertView.findViewById(R.id.tvAvatar);
            holder.tvStudentName = convertView.findViewById(R.id.tvStudentName);
            holder.btnEnroll = convertView.findViewById(R.id.btnEnroll);
            holder.btnUnenroll = convertView.findViewById(R.id.btnUnenroll);
            holder.btnEdit = convertView.findViewById(R.id.btnEdit);
            holder.btnCheckIn = convertView.findViewById(R.id.btnCheckIn);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String studentName = studentNames.get(position);
        String studentId = studentIds.get(position);

        holder.tvStudentName.setText(studentName);

        String avatarPath = "";
        if (avatarPaths != null && position < avatarPaths.size()) {
            avatarPath = avatarPaths.get(position);
        }

        bindAvatar(holder.imgAvatar, avatarPath);

        holder.btnEnroll.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEnrollClick(studentId);
            }
        });

        holder.btnUnenroll.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUnenrollClick(studentId);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(studentId);
            }
        });

        holder.btnCheckIn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCheckInClick(studentId);
            }
        });

        return convertView;
    }

    private void bindAvatar(ImageView imgAvatar, String avatarPath) {
        if (avatarPath != null && !avatarPath.trim().isEmpty()) {
            File avatarFile = new File(avatarPath);

            if (avatarFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(avatarPath);

                if (bitmap != null) {
                    imgAvatar.setImageBitmap(bitmap);
                    return;
                }
            }
        }

        imgAvatar.setImageResource(R.drawable.avatar_circle_bg);
    }

    private static class ViewHolder {
        ImageView imgAvatar;
        TextView tvStudentName;
        ImageButton btnEnroll;
        ImageButton btnUnenroll;
        ImageButton btnEdit;
        ImageButton btnCheckIn;
    }
}