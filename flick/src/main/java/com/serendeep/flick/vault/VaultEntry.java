package com.serendeep.flick.vault;

import com.serendeep.flick.otp.OtpInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VaultEntry {
    private final String _uuid;
    private final String _type;
    private final String _name;
    private final String _issuer;
    private final String _note;
    private final boolean _favorite;
    private final OtpInfo _otpInfo;
    private final List<String> _groups;

    private VaultEntry(String uuid, String type, String name, String issuer,
                       String note, boolean favorite, OtpInfo otpInfo, List<String> groups) {
        _uuid = uuid;
        _type = type;
        _name = name;
        _issuer = issuer;
        _note = note;
        _favorite = favorite;
        _otpInfo = otpInfo;
        _groups = groups;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("type", _type);
        obj.put("uuid", _uuid);
        obj.put("name", _name);
        obj.put("issuer", _issuer);
        obj.put("note", _note);
        obj.put("favorite", _favorite);
        obj.put("info", _otpInfo.toJson());
        JSONArray groupsArr = new JSONArray();
        for (String group : _groups) {
            groupsArr.put(group);
        }
        obj.put("groups", groupsArr);
        return obj;
    }

    public static VaultEntry fromJson(JSONObject obj) throws VaultParseException {
        try {
            String type = obj.getString("type");
            String uuid = obj.getString("uuid");
            String name = obj.optString("name", "");
            String issuer = obj.optString("issuer", "");
            String note = obj.optString("note", "");
            boolean favorite = obj.optBoolean("favorite", false);

            OtpInfo otpInfo = OtpInfo.fromJson(type, obj.getJSONObject("info"));

            List<String> groups = new ArrayList<>();
            JSONArray groupsArr = obj.optJSONArray("groups");
            if (groupsArr != null) {
                for (int i = 0; i < groupsArr.length(); i++) {
                    groups.add(groupsArr.getString(i));
                }
            }

            return new VaultEntry(uuid, type, name, issuer, note, favorite, otpInfo,
                    Collections.unmodifiableList(groups));
        } catch (VaultParseException e) {
            throw e;
        } catch (Exception e) {
            throw new VaultParseException("Failed to parse entry", e);
        }
    }

    public String getUuid() {
        return _uuid;
    }

    public String getType() {
        return _type;
    }

    public String getName() {
        return _name;
    }

    public String getIssuer() {
        return _issuer;
    }

    public String getNote() {
        return _note;
    }

    public boolean isFavorite() {
        return _favorite;
    }

    public OtpInfo getOtpInfo() {
        return _otpInfo;
    }

    public List<String> getGroups() {
        return _groups;
    }
}
