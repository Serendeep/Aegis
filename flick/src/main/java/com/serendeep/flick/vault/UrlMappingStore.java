package com.serendeep.flick.vault;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class UrlMappingStore {
    private static final String PREFS_NAME = "flick_url_mappings";
    private static final String KEY_MAPPINGS = "mappings";

    private final SharedPreferences _prefs;

    public UrlMappingStore(Context context) {
        _prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getMapping(String urlPattern) {
        Map<String, String> mappings = getAllMappings();
        return mappings.get(urlPattern);
    }

    public void setMapping(String urlPattern, String entryUuid) {
        Map<String, String> mappings = getAllMappings();
        mappings.put(urlPattern, entryUuid);
        saveMappings(mappings);
    }

    public void removeMapping(String urlPattern) {
        Map<String, String> mappings = getAllMappings();
        mappings.remove(urlPattern);
        saveMappings(mappings);
    }

    public Map<String, String> getAllMappings() {
        String json = _prefs.getString(KEY_MAPPINGS, null);
        if (json == null) {
            return new HashMap<>();
        }

        try {
            JSONObject obj = new JSONObject(json);
            Map<String, String> mappings = new HashMap<>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                mappings.put(key, obj.getString(key));
            }
            return mappings;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void saveMappings(Map<String, String> mappings) {
        JSONObject obj = new JSONObject(mappings);
        _prefs.edit().putString(KEY_MAPPINGS, obj.toString()).apply();
    }
}
