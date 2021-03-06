package com.github.marlowww.hidemocklocation;

import android.content.ContentResolver;
import android.provider.Settings;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class XposedModule implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public XSharedPreferences prefs;

    public XC_ProcessNameMethodHook hideAllowMockSettingHook;
    public XC_ProcessNameMethodHook hideMockProviderHook;

    class XC_ProcessNameMethodHook extends XC_MethodHook {

        private String processName;

        private XC_MethodHook init(String processName){
            this.processName = processName;
            return this;
        }
        boolean isHidingEnabled() {
            Common.ListType listType = getListType();
            Set<String> apps = getAppList(listType);

            switch (listType) {
                case BLACKLIST:
                    if (apps.contains(processName))
                        return true;
                    break;
                case WHITELIST:
                    if (!apps.contains(processName))
                        return true;
            }
        return false;
        }
    }

    public Common.ListType getListType() {
        prefs.reload();
        return prefs.getString(Common.PREF_LIST_TYPE, Common.ListType.BLACKLIST.toString())
            .equals(Common.ListType.BLACKLIST.toString())
                ? Common.ListType.BLACKLIST : Common.ListType.WHITELIST;
    }

    public Set<String> getAppList(Common.ListType type) {
        prefs.reload();
        return prefs.getStringSet(type.toString(), new HashSet<String>(0));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getString",
                ContentResolver.class, String.class, hideAllowMockSettingHook.init(lpparam.processName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getInt",
                ContentResolver.class, String.class, hideAllowMockSettingHook.init(lpparam.processName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getFloat",
                ContentResolver.class, String.class, hideAllowMockSettingHook.init(lpparam.processName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getLong",
                ContentResolver.class, String.class, hideAllowMockSettingHook.init(lpparam.processName));

        if (Common.JB_MR2_NEWER)
            XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader,
                    "isFromMockProvider", hideMockProviderHook.init(lpparam.processName));

        // Self hook - informing Activity that Xposed module is enabled
        if(lpparam.packageName.equals(Common.PACKAGE_NAME))
            XposedHelpers.findAndHookMethod(Common.ACTIVITY_NAME, lpparam.classLoader, "isModuleEnabled",
                    XC_MethodReplacement.returnConstant(true));
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        prefs = new XSharedPreferences(Common.PACKAGE_NAME, Common.PACKAGE_PREFERENCES);
        prefs.makeWorldReadable();

        hideAllowMockSettingHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (isHidingEnabled()) {
                    String methodName = param.method.getName();
                    String setting = (String) param.args[1];
                    if (setting.equals(Settings.Secure.ALLOW_MOCK_LOCATION)) {
                        switch (methodName) {
                            case "getInt":
                                param.setResult(0);
                                break;
                            case "getString":
                                param.setResult("0");
                                break;
                            case "getFloat":
                                param.setResult(0.0f);
                                break;
                            case "getLong":
                                param.setResult(0L);
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        };

        hideMockProviderHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if(isHidingEnabled())
                    param.setResult(false);
            }
        };
    }
}