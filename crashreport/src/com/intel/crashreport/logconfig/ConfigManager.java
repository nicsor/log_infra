
package com.intel.crashreport.logconfig;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.intel.crashreport.logconfig.bean.ConfigStatus;
import com.intel.crashreport.logconfig.bean.ConfigStatus.ConfigState;
import com.intel.crashreport.logconfig.bean.LogConfig;
import com.intel.crashreport.logconfig.ui.LogConfigHomeActivity;

public class ConfigManager implements IConfigServiceClient {

    private static ConfigManager mConfigManager;
    private Context mCtx;
    private ConfigLoader mConfigLoader;
    private Storage mStorage;
    private ArrayList<ConfigStatus> listConfigStatus;
    private ConfigServiceClient mClient = null;
    private LogConfigHomeActivity mActivity;

    private ConfigManager(Context c) {
        this.mCtx = c;
        this.mConfigLoader = ConfigLoader.getInstance(c);
        this.mStorage = new Storage(c);
    }

    public static synchronized ConfigManager getInstance(Context c) {
        if (mConfigManager == null)
            mConfigManager = new ConfigManager(c);
        return mConfigManager;
    }

    public ArrayList<ConfigStatus> getConfigStatusList() {
        if (listConfigStatus == null)
            listConfigStatus = loadBaseConfigStatusList();
        return listConfigStatus;
    }

    private ArrayList<ConfigStatus> loadBaseConfigStatusList() {
        List<String> mConfigNames = mConfigLoader.getList();
        List<String> mConfigAppliedNames = mStorage.getAppliedConfigs();
        ArrayList<ConfigStatus> mConfigStatusList = new ArrayList<ConfigStatus>();
        for (String configName : mConfigNames) {
            ConfigStatus cs = new ConfigStatus(configName);
            if (mConfigAppliedNames.contains(configName))
                cs.setState(ConfigState.ON);
            LogConfig mConfig = mConfigLoader.getConfig(configName);
            if (mConfig != null)
                cs.setDescription(mConfig.getDescription());
            else
                cs.setDescription("! Bad file ! " + configName);
            mConfigStatusList.add(cs);
        }
        return mConfigStatusList;
    }

    private ArrayList<String> getDefaultConfigNames() {
        List<String> mConfigNames = mConfigLoader.getList();
        ArrayList<String> defaultConfigs = new ArrayList<String>();
        LogConfig conf;
        for (String configName : mConfigNames) {
            conf = mConfigLoader.getConfig(configName);
            if (conf.isAppliedByDefault()) {
                Log.d("LogConfig", "Default config : " + configName);
                defaultConfigs.add(configName);
            }
        }
        return defaultConfigs;
    }

    public void saveConfigStatus() {
        ArrayList<String> mConfigAppliedNames = new ArrayList<String>();
        for (ConfigStatus cs : getConfigStatusList()) {
            if (cs.getState() == ConfigState.ON)
                mConfigAppliedNames.add(cs.getName());
        }
        mStorage.saveAppliedConfigs(mConfigAppliedNames);
    }

    public List<ConfigStatus> getPersistantConfigList() {
        Log.d("LogConfig", "Get persistant configs");
        List<ConfigStatus> mPersistConfigs = new ArrayList<ConfigStatus>();
        List<String> mPersistConfigNames;
        if (mStorage.isFirstBoot()) {
            Log.d("LogConfig", "First boot, get default configs");
            mPersistConfigNames = getDefaultConfigNames();
        } else {
            Log.d("LogConfig", "Get applied configs");
            mPersistConfigNames = mStorage.getAppliedConfigs();
        }
        if (mPersistConfigNames != null) {
            for (String configName : mPersistConfigNames) {
                ConfigStatus mConfigStatus = loadConfigStatus(configName);
                if (mConfigStatus != null) {
                    mConfigStatus.setState(ConfigState.TO_ON);
                    mPersistConfigs.add(mConfigStatus);
                }
            }
        }
        return mPersistConfigs;
    }

    public ConfigStatus getConfigStatus(String configName) {
        for (ConfigStatus cs : getConfigStatusList()) {
            if (cs.getName().contentEquals(configName))
                return cs;
        }
        return null;
    }

    /**
     * Load LogConfig in ConfigStatus
     */
    public ConfigStatus loadConfigStatus(String configName) {
        ConfigStatus cs = getConfigStatus(configName);
        LogConfig mLogConfig = mConfigLoader.getConfig(configName);
        if (mLogConfig != null)
            cs.setLogConfig(mLogConfig);
        return cs;
    }

    /**
     * Apply a config list
     */
    public void applyConfigs(List<String> configNames) {
        if (configNames == null || configNames.isEmpty()) {
            Log.w("LogConfig", "No config to apply");
            return;
        }
        List<ConfigStatus> mConfigs = new ArrayList<ConfigStatus>();
        for (String configName : configNames) {
            ConfigStatus mConfigStatus = loadConfigStatus(configName);
            if (mConfigStatus != null)
                mConfigs.add(mConfigStatus);
        }
        if (mClient == null)
            mClient = new ConfigServiceClient(this);
        mClient.applyConfigList(mConfigs);
    }

    public void updateAppliedConfigs(List<ConfigStatus> configs) {
        saveConfigStatus();
        if (null != mActivity)
            mActivity.updateData();
    }

    public Context getContext() {
        return mCtx;
    }

    public Looper getLooper() {
        return mCtx.getMainLooper();
    }

    public void clientFinished() {
    }

    public ArrayList<String> getConfigsName() {
        return mConfigLoader.getList();
    }

    public ArrayList<String> getConfigsDescription() {
        ArrayList<String> descriptions = new ArrayList<String>();
        for (ConfigStatus cs : getConfigStatusList()) {
            descriptions.add(cs.getDescription());
        }
        return descriptions;
    }

    public ArrayList<ConfigStatus> reloadConfigStatusList() {
        listConfigStatus = loadBaseConfigStatusList();
        return listConfigStatus;
    }

    public void setActivity(LogConfigHomeActivity activity) {
        mActivity = activity;
    }

}
