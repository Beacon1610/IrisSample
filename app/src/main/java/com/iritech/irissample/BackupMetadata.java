package com.iritech.irissample;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public final class BackupMetadata {

    public static final int CURRENT_FORMAT_VERSION = 1;

    @SerializedName("format")
    public String format = "IrisSample Backup";

    @SerializedName("backupFormatVersion")
    public int backupFormatVersion = CURRENT_FORMAT_VERSION;

    @SerializedName("applicationId")
    public String applicationId;

    @SerializedName("appVersionName")
    public String appVersionName;

    @SerializedName("appVersionCode")
    public long appVersionCode;

    @SerializedName("databaseName")
    public String databaseName;

    @SerializedName("databaseVersion")
    public int databaseVersion;

    @SerializedName("createdAtUtc")
    public String createdAtUtc;

    @SerializedName("irisRepositoryIncluded")
    public boolean irisRepositoryIncluded;

    @SerializedName("rawIrisCaptureIncluded")
    public boolean rawIrisCaptureIncluded = false;

    @SerializedName("adminAvatarCount")
    public int adminAvatarCount;

    @SerializedName("studentAvatarCount")
    public int studentAvatarCount;

    @SerializedName("entries")
    public List<Entry> entries = new ArrayList<>();

    @SerializedName("warnings")
    public List<String> warnings = new ArrayList<>();

    public void addEntry(
            String path,
            String type,
            String ownerId,
            long size,
            String sha256
    ) {
        entries.add(new Entry(
                path,
                type,
                ownerId,
                size,
                sha256
        ));
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.trim().isEmpty()) {
            warnings.add(warning);
        }
    }

    public static final class Entry {

        @SerializedName("path")
        public String path;

        @SerializedName("type")
        public String type;

        @SerializedName("ownerId")
        public String ownerId;

        @SerializedName("size")
        public long size;

        @SerializedName("sha256")
        public String sha256;

        public Entry(
                String path,
                String type,
                String ownerId,
                long size,
                String sha256
        ) {
            this.path = path;
            this.type = type;
            this.ownerId = ownerId;
            this.size = size;
            this.sha256 = sha256;
        }
    }
}