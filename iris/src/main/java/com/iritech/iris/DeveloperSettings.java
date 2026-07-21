package com.iritech.iris;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Developer Settings để bypass nhận diện mống mắt khi chưa có thiết bị
 * Giúp phát triển các tính năng khác mà không cần chờ thiết bị thật
 */
public class DeveloperSettings {
    
    private static final String PREFS_NAME = "developer_settings";
    private static final String KEY_MOCK_MODE = "mock_mode_enabled";
    
    private final SharedPreferences prefs;
    
    public DeveloperSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Bật/tắt Mock Mode (bypass nhận diện mống mắt)
     */
    public void setMockModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MOCK_MODE, enabled).apply();
    }
    
    /**
     * Kiểm tra Mock Mode có đang bật không
     */
    public boolean isMockModeEnabled() {
        return prefs.getBoolean(KEY_MOCK_MODE, false);
    }
    
    /**
     * Simulate thành công nhận diện mống mắt
     * @return Fake template data
     */
    public byte[] generateMockTemplate() {
        // Fake template 512 bytes
        byte[] template = new byte[512];
        for (int i = 0; i < template.length; i++) {
            template[i] = (byte) (Math.random() * 256);
        }
        return template;
    }
}
