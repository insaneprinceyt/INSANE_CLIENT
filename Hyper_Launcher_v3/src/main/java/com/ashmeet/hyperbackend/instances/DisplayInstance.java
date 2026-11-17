package com.ashmeet.hyperbackend.instances;

import android.util.Log;
import com.ashmeet.hyperbackend.utils.JSONUtils;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

public class DisplayInstance {
    public transient File mInstanceRoot;
    public String name;
    public String versionId;
    public String icon;
    public boolean isPinned;

    protected void sanitize() {
        sanitizeIcon();
    }

    protected DisplayInstance() {
    }

    protected File getInstanceIconLocation() {
        return new File(mInstanceRoot, "icon.webp");
    }

    private void sanitizeIcon() {
        if(!InstanceIconProvider.hasStaticIcon(icon)) {
            icon = InstanceIconProvider.FALLBACK_ICON_NAME;
        }
    }

    public void write() throws IOException {
        // A list/pin edit must not erase full Instance settings or upgrade folder aliases.
        File metadata = Instances.metadataLocation(mInstanceRoot);
        JsonObject value = metadata.isFile() ? JSONUtils.readFromFile(metadata, JsonObject.class) : null;
        if (value == null) value = new JsonObject();
        value.addProperty("name", name);
        value.addProperty("versionId", versionId);
        value.addProperty("icon", icon);
        value.addProperty("isPinned", isPinned);
        JSONUtils.writeToFile(metadata, value);
    }

    public void maybeWrite() {
        try {
            write();
        } catch (IOException e) {
            Log.e("DisplayInstance", "Failed to write", e);
        }
    }
}
