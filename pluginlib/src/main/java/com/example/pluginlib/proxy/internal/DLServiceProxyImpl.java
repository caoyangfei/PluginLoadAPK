package com.example.pluginlib.proxy.internal;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.example.pluginlib.manager.DLPluginManager;
import com.example.pluginlib.model.DLPluginPackage;
import com.example.pluginlib.plugin.inter.DLServicePlugin;
import com.example.pluginlib.proxy.inter.DLServiceAttachable;
import com.example.pluginlib.constant.DLConfigs;
import com.example.pluginlib.constant.DLConstants;

import java.lang.reflect.Constructor;

public class DLServiceProxyImpl {
    
    private static final String TAG = "DLServiceProxyImpl";
    private Service mProxyService;
    private DLServicePlugin mRemoteService;
    
    public DLServiceProxyImpl(Service service) {
        mProxyService = service;
    }
    
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void init(Intent intent) {
     // set the extra's class loader
        intent.setExtrasClassLoader(DLConfigs.sPluginClassloader);

        String packageName = intent.getStringExtra(DLConstants.EXTRA_PACKAGE);
        String clazz = intent.getStringExtra(DLConstants.EXTRA_CLASS);
        Log.d(TAG, "clazz=" + clazz + " packageName=" + packageName);
        
        DLPluginManager pluginManager = DLPluginManager.getInstance(mProxyService);
        DLPluginPackage pluginPackage = pluginManager.getPackage(packageName);
        
        try {
            Class<?> localClass = pluginPackage.classLoader.loadClass(clazz);
            Constructor<?> localConstructor = localClass.getConstructor(new Class[] {});
            Object instance = localConstructor.newInstance(new Object[] {});
            mRemoteService = (DLServicePlugin) instance;
            ((DLServiceAttachable) mProxyService).attach(mRemoteService, pluginManager);
            Log.d(TAG, "instance = " + instance);
            // attach the proxy activity and plugin package to the
            // mPluginActivity
            mRemoteService.attach(mProxyService, pluginPackage);

            Bundle bundle = new Bundle();
            bundle.putInt(DLConstants.FROM, DLConstants.FROM_EXTERNAL);
            mRemoteService.onCreate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
