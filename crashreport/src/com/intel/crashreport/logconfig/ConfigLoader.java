
package com.intel.crashreport.logconfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.intel.crashreport.logconfig.bean.IntentLogSetting;
import com.intel.crashreport.logconfig.bean.IntentLogSetting.IntentExtra;
import com.intel.crashreport.logconfig.bean.LogConfig;
import com.intel.crashreport.logconfig.bean.LogSetting;

public class ConfigLoader {

    private static ConfigLoader mConfigLoader;
    private static final String M_ASSETS_DIR = "logconfig";
    private Context c;
    private Gson mGson;

    private ConfigLoader(Context c) {
        this.c = c;
        GsonBuilder mGsonBuilder = new GsonBuilder();
        mGsonBuilder.registerTypeAdapter(LogSetting.class, new JsonLogSettingAdapter());
        IntentLogSetting s = new IntentLogSetting();
        mGsonBuilder.registerTypeAdapter(IntentExtra.class,
                s.new JsonIntentExtraAdapter());
        mGson = mGsonBuilder.create();
    }

    public static synchronized ConfigLoader getInstance(Context c) {
        if (mConfigLoader == null)
            mConfigLoader = new ConfigLoader(c);
        return mConfigLoader;
    }

    public ArrayList<String> getList() {
        AssetManager ass = c.getAssets();
        File mDir = c.getDir(M_ASSETS_DIR, Context.MODE_WORLD_READABLE
                | Context.MODE_WORLD_WRITEABLE);
        ArrayList<String> mConfigList = new ArrayList<String>();
        try {
            String mList[] = ass.list(M_ASSETS_DIR);
            if (mList != null)
                for (String config : mList)
                    mConfigList.add(config);
            mList = mDir.list();
            if (mList != null)
                for (String config : mList)
                    mConfigList.add(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mConfigList;
    }

    public LogConfig getConfig(String name) {
        LogConfig config = null;
        try {
            InputStreamReader is = getConfigFile(name);
            if (is != null) {
                config = mGson.fromJson(is, LogConfig.class);
                if (config != null)
                    config.setName(name);
                is.close();
            } else
                throw new FileNotFoundException(name);
        } catch (FileNotFoundException e) {
            Log.w("LogConfig", "Config not found : " + name);
        } catch (JsonSyntaxException e) {
            Log.w("LogConfig", "JsonSyntaxException in file : " + name, e);
        } catch (JsonIOException e) {
            Log.w("LogConfig", "JsonIOException while reading file : " + name, e);
        } catch (IOException e) {
            Log.w("LogConfig", "IOException while closing file : " + name, e);
        }
        return config;
    }

    public LogConfig getConfig(LogConfig config) {
        return getConfig(config.getName());
    }

    private InputStreamReader getConfigFile(String name) throws FileNotFoundException {
        try {
            InputStreamReader is = new InputStreamReader(c.getAssets().open(
                    M_ASSETS_DIR + File.separator + name));
            return is;
        } catch (IOException e) {
            File mDir = c.getDir(M_ASSETS_DIR, Context.MODE_WORLD_READABLE
                    | Context.MODE_WORLD_WRITEABLE);
            File mConfigFile = new File(mDir, name);
            if (mConfigFile.exists())
                return new InputStreamReader(new FileInputStream(mConfigFile));
        }
        return null;
    }

}
