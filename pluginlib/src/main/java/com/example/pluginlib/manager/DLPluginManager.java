/*
 * Copyright (C) 2014 singwhatiwanna(任玉刚) <singwhatiwanna@gmail.com>
 *
 * collaborator:田啸,宋思宇,Mr.Simple
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.pluginlib.manager;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;

import com.example.pluginlib.constant.DLConstants;
import com.example.pluginlib.constant.DLIntent;
import com.example.pluginlib.model.DLPluginPackage;
import com.example.pluginlib.plugin.DLBasePluginService;
import com.example.pluginlib.proxy.DLProxyActivity;
import com.example.pluginlib.proxy.DLProxyFragmentActivity;
import com.example.pluginlib.proxy.DLProxyService;
import com.example.pluginlib.utils.LOG;

import java.lang.reflect.Method;
import java.util.HashMap;

import dalvik.system.DexClassLoader;

public class DLPluginManager {

    /**
     * 跳转成功{@link startPluginActivity(Activity, DLIntent)}
     */
    public static final int START_RESULT_SUCCESS = 0;
    /**
     * 没找到跳转包名{@link startPluginActivity(Activity, DLIntent)}
     */
    public static final int START_RESULT_NO_PKG = 1;
    /**
     * 没有找到跳转的Class{@link startPluginActivity(Activity, DLIntent)}
     */
    public static final int START_RESULT_NO_CLASS = 2;
    /**
     * 失败返回值{@link startPluginActivity(Context , DLIntent)}
     */
    public static final int START_RESULT_TYPE_ERROR = 3;


    private static DLPluginManager sInstance;
    private Context mContext;
    private final HashMap<String, DLPluginPackage> mPackagesHolder = new HashMap<String, DLPluginPackage>();

    private int mFrom = DLConstants.FROM_INTERNAL;

    private String mNativeLibDir;

    private int mResult;
    private String dexOutputPath;

    private DLPluginManager(Context context) {
        mContext = context.getApplicationContext();
        mNativeLibDir = mContext.getDir("lib", Context.MODE_PRIVATE).getAbsolutePath();
        dexOutputPath = mContext.getDir("dex", Context.MODE_PRIVATE).getAbsolutePath();
    }

    public static DLPluginManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (DLPluginManager.class) {
                if (sInstance == null) {
                    sInstance = new DLPluginManager(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * 宿主加载插件apk,默认有.so文件
     *
     * @param dexPath
     * @return
     */
    public DLPluginPackage loadApk(String dexPath) {
        return loadApk(dexPath, true);
    }

    /**
     * 宿主加载插件apk
     *
     * @param dexPath
     * @param hasSoLib 是否有.so文件
     * @return
     */
    public DLPluginPackage loadApk(final String dexPath, boolean hasSoLib) {
        mFrom = DLConstants.FROM_EXTERNAL;
        PackageInfo packageInfo = mContext.getPackageManager().getPackageArchiveInfo(dexPath,
                PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES);
        if (packageInfo == null) {
            return null;
        }
        DLPluginPackage pluginPackage = preparePluginEnv(packageInfo, dexPath);
        if (hasSoLib) {
            copySoLib(dexPath);
        }
        return pluginPackage;
    }

    /**
     * 初始化运行时的插件  类加载器,资源文件等
     *
     * @param packageInfo
     * @param dexPath
     * @return
     */
    private DLPluginPackage preparePluginEnv(PackageInfo packageInfo, String dexPath) {

        DLPluginPackage pluginPackage = mPackagesHolder.get(packageInfo.packageName);
        if (pluginPackage != null) {
            return pluginPackage;
        }
        DexClassLoader dexClassLoader = createDexClassLoader(dexPath);
        Resources resources = createResources(dexPath);
        //加载的类加载器,资源文件封装
        pluginPackage = new DLPluginPackage(dexClassLoader, resources, packageInfo);
        mPackagesHolder.put(packageInfo.packageName, pluginPackage);
        return pluginPackage;
    }

    /**
     * 根据路径加载apk、dex和jar进类加载器
     * 创建 包路径下文件
     *
     * @param dexPath
     * @return
     */
    private DexClassLoader createDexClassLoader(String dexPath) {
        DexClassLoader loader = new DexClassLoader(dexPath, dexOutputPath, mNativeLibDir, mContext.getClassLoader());
        return loader;
    }

    /**
     * 根据路径加载资源文件
     *
     * @param dexPath
     * @return
     */
    private Resources createResources(String dexPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, dexPath);

            Resources superRes = mContext.getResources();
            Resources resources = new Resources(assetManager, superRes.getDisplayMetrics(), superRes.getConfiguration());
            return resources;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    /**
     * 包路获取DLPluginPackage方法
     *
     * @param packageName
     * @return
     */
    public DLPluginPackage getPackage(String packageName) {
        return mPackagesHolder.get(packageName);
    }

    /**
     * 拷贝.so文件到lib下
     *
     * @param dexPath
     */
    private void copySoLib(String dexPath) {
        SoLibManager.getSoLoader().copyPluginSoLib(mContext, dexPath, mNativeLibDir);
    }

    /**
     * 跳转插件activity
     *
     * @param context
     * @param dlIntent 继承Intent的对象携带包名和class
     * @return
     */
    public int startPluginActivity(Context context, DLIntent dlIntent) {
        return startPluginActivityForResult(context, dlIntent, -1);
    }

    /**
     * 跳转插件activity
     *
     * @param context
     * @param dlIntent    继承Intent的对象携带包名和class
     * @param requestCode
     * @return One of below:
     * {@link #START_RESULT_SUCCESS}
     * {@link #START_RESULT_NO_PKG}
     * {@link #START_RESULT_NO_CLASS}
     * {@link #START_RESULT_TYPE_ERROR}
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public int startPluginActivityForResult(Context context, DLIntent dlIntent, int requestCode) {
        //不是插件,内部Activity直接启动
        if (mFrom == DLConstants.FROM_INTERNAL) {
            dlIntent.setClassName(context, dlIntent.getPluginClass());
            performStartActivityForResult(context, dlIntent, requestCode);
            return DLPluginManager.START_RESULT_SUCCESS;
        }
        //获取携带过来的包名
        String packageName = dlIntent.getPluginPackage();
        if (TextUtils.isEmpty(packageName)) {
            throw new NullPointerException("disallow null packageName.");
        }
        //获取map中加载进去的apk包名
        DLPluginPackage pluginPackage = mPackagesHolder.get(packageName);
        if (pluginPackage == null) {
            return START_RESULT_NO_PKG;
        }
        //根据预埋的Activity全路径,获取Class
        Class<?> clazz = loadPluginClass(pluginPackage.classLoader, dlIntent.getPluginClass());
        if (clazz == null) {
            return START_RESULT_NO_CLASS;
        }
        //预埋的代理Activity
        Class<? extends Activity> activityClass = getProxyActivityClass(clazz);
        if (activityClass == null) {
            return START_RESULT_TYPE_ERROR;
        }
        //DLIntent 传递包名,要替换预埋的Activity参考路径,预埋的Activity
        dlIntent.putExtra(DLConstants.EXTRA_CLASS, dlIntent.getPluginClass());
        dlIntent.putExtra(DLConstants.EXTRA_PACKAGE, packageName);
        dlIntent.setClass(mContext, activityClass);
        LOG.d("预埋Activity:" + activityClass);
        performStartActivityForResult(context, dlIntent, requestCode);
        return START_RESULT_SUCCESS;
    }

    /**
     * 启动Activity,启动的是预埋的activity
     * One of below:
     * {@link DLProxyActivity,DLProxyFragmentActivity}
     *
     * @param context
     * @param dlIntent
     * @param requestCode
     */
    private void performStartActivityForResult(Context context, DLIntent dlIntent, int requestCode) {
        LOG.d("插件Activity:" + dlIntent.getPluginClass());
        if (context instanceof Activity) {
            ((Activity) context).startActivityForResult(dlIntent, requestCode);
        } else {
            context.startActivity(dlIntent);
        }
    }

    /**
     * 预埋的activity,获取class对象,代理跳转插件activity
     *
     * @param clazz
     * @return
     */
    private Class<? extends Activity> getProxyActivityClass(Class<?> clazz) {
        Class<? extends Activity> activityClass = null;
        if (FragmentActivity.class.isAssignableFrom(clazz)) {
            activityClass = DLProxyFragmentActivity.class;
        } else if (Activity.class.isAssignableFrom(clazz)) {
            activityClass = DLProxyActivity.class;
        }
        return activityClass;
    }

    /*****************插件Service********************/

    /**
     * 启动插件Service
     *
     * @param context
     * @param dlIntent
     * @return
     */
    public int startPluginService(final Context context, final DLIntent dlIntent) {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            dlIntent.setClassName(context, dlIntent.getPluginClass());
            context.startService(dlIntent);
            return DLPluginManager.START_RESULT_SUCCESS;
        }
        fetchProxyServiceClass(dlIntent, new OnFetchProxyServiceClass() {
            @Override
            public void onFetch(int result, Class<? extends Service> proxyServiceClass) {
                // TODO Auto-generated method stub
                if (result == START_RESULT_SUCCESS) {
                    dlIntent.setClass(context, proxyServiceClass);
                    // start代理Service
                    context.startService(dlIntent);
                }
                mResult = result;
            }
        });

        return mResult;
    }

    /**
     * 停用插件Service
     *
     * @param context
     * @param dlIntent
     * @return
     */
    public int stopPluginService(final Context context, final DLIntent dlIntent) {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            dlIntent.setClassName(context, dlIntent.getPluginClass());
            context.stopService(dlIntent);
            return DLPluginManager.START_RESULT_SUCCESS;
        }
        fetchProxyServiceClass(dlIntent, new OnFetchProxyServiceClass() {
            @Override
            public void onFetch(int result, Class<? extends Service> proxyServiceClass) {
                // TODO Auto-generated method stub
                if (result == START_RESULT_SUCCESS) {
                    dlIntent.setClass(context, proxyServiceClass);
                    // stop代理Service
                    context.stopService(dlIntent);
                }
                mResult = result;
            }
        });
        return mResult;
    }

    /**
     * 绑定插件Service
     *
     * @param context
     * @param dlIntent
     * @param conn
     * @param flags
     * @return
     */
    public int bindPluginService(final Context context, final DLIntent dlIntent, final ServiceConnection conn,
                                 final int flags) {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            dlIntent.setClassName(context, dlIntent.getPluginClass());
            context.bindService(dlIntent, conn, flags);
            return DLPluginManager.START_RESULT_SUCCESS;
        }
        fetchProxyServiceClass(dlIntent, new OnFetchProxyServiceClass() {
            @Override
            public void onFetch(int result, Class<? extends Service> proxyServiceClass) {
                // TODO Auto-generated method stub
                if (result == START_RESULT_SUCCESS) {
                    dlIntent.setClass(context, proxyServiceClass);
                    // Bind代理Service
                    context.bindService(dlIntent, conn, flags);
                }
                mResult = result;
            }
        });
        return mResult;
    }

    /**
     * 解绑插件Service
     *
     * @param context
     * @param dlIntent
     * @param conn
     * @return
     */
    public int unBindPluginService(final Context context, DLIntent dlIntent, final ServiceConnection conn) {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            context.unbindService(conn);
            return DLPluginManager.START_RESULT_SUCCESS;
        }
        fetchProxyServiceClass(dlIntent, new OnFetchProxyServiceClass() {
            @Override
            public void onFetch(int result, Class<? extends Service> proxyServiceClass) {
                // TODO Auto-generated method stub
                if (result == START_RESULT_SUCCESS) {
                    // unBind代理Service
                    context.unbindService(conn);
                }
                mResult = result;
            }
        });
        return mResult;
    }

    /**
     * 获取代理ServiceClass
     *
     * @param dlIntent
     * @param fetchProxyServiceClass
     */
    private void fetchProxyServiceClass(DLIntent dlIntent, OnFetchProxyServiceClass fetchProxyServiceClass) {
        String packageName = dlIntent.getPluginPackage();
        if (TextUtils.isEmpty(packageName)) {
            throw new NullPointerException("disallow null packageName.");
        }
        DLPluginPackage pluginPackage = mPackagesHolder.get(packageName);
        if (pluginPackage == null) {
            fetchProxyServiceClass.onFetch(START_RESULT_NO_PKG, null);
            return;
        }
        // 获取要启动的Service的全名
        String className = dlIntent.getPluginClass();
        Class<?> clazz = loadPluginClass(pluginPackage.classLoader, className);
        if (clazz == null) {
            fetchProxyServiceClass.onFetch(START_RESULT_NO_CLASS, null);
            return;
        }
        Class<? extends Service> proxyServiceClass = getProxyServiceClass(clazz);
        if (proxyServiceClass == null) {
            fetchProxyServiceClass.onFetch(START_RESULT_TYPE_ERROR, null);
            return;
        }

        //DLIntent 传递包名,要替换预埋的Service参考路径,预埋的Service
        dlIntent.putExtra(DLConstants.EXTRA_CLASS, className);
        dlIntent.putExtra(DLConstants.EXTRA_PACKAGE, packageName);
        fetchProxyServiceClass.onFetch(START_RESULT_SUCCESS, proxyServiceClass);
    }

    /**
     * 预埋的DLProxyService,获取Service对象,代理跳转插件Service
     *
     * @param clazz
     * @return
     */
    private Class<? extends Service> getProxyServiceClass(Class<?> clazz) {
        Class<? extends Service> proxyServiceClass = null;
        if (DLBasePluginService.class.isAssignableFrom(clazz)) {
            proxyServiceClass = DLProxyService.class;
        }
        // 后续可能还有IntentService，待补充

        return proxyServiceClass;
    }

    /**
     * 根据文件全路径获取Class文件
     *
     * @param classLoader
     * @param className
     * @return
     */
    private Class<?> loadPluginClass(ClassLoader classLoader, String className) {
        Class<?> clazz = null;
        try {
            clazz = Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return clazz;
    }

    private interface OnFetchProxyServiceClass {
        public void onFetch(int result, Class<? extends Service> proxyServiceClass);
    }

}
