package com.example.pluginlib.plugin.inter;


import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

import com.example.pluginlib.model.DLPluginPackage;
import com.example.pluginlib.plugin.DLBasePluginService;

/**
 * note: 宿主Service,插件生命周期关联接口
 *
 * @author yangfeicao
 * @see {@link DLBasePluginService}
 */
public interface DLServicePlugin {

    public void onCreate();

    public void onStart(Intent intent, int startId);

    public int onStartCommand(Intent intent, int flags, int startId);

    public void onDestroy();

    public void onConfigurationChanged(Configuration newConfig);

    public void onLowMemory();

    public void onTrimMemory(int level);

    public IBinder onBind(Intent intent);

    public boolean onUnbind(Intent intent);

    public void onRebind(Intent intent);

    public void onTaskRemoved(Intent rootIntent);

    public void attach(Service proxyService, DLPluginPackage pluginPackage);
}
