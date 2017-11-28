package com.houtrry.hotfixbugsamples;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashSet;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * @author houtrry
 * @version $Rev$
 * @time 2017/11/27 16:23
 * @desc 代码来自于 http://www.jianshu.com/p/cb1f0702d59f?utm_source=androidweekly.cn&utm_medium=website
 *
 *      代码只作演示用，实际项目中，还需要另作安排
 *      代码中存在的问题，dex会重复加入elements.
 *      暂时想到的去重办法：1. 加载成功够删除dex文件（这个方法有问题，比如，这次加载了dex文件，修复了bug，那下次加载怎么办， 删除后就无法加载了。）
 *                        2. 加载前判断是否加载过该dex文件
 *                        3. 在合适的地方加载（比如说在Application里面）
 */

public class FixDexUtils {

    private static final String TAG = FixDexUtils.class.getSimpleName();

    private static final String DEX_SUFFIX = ".dex";
    private static final String APK_SUFFIX = ".apk";
    private static final String JAR_SUFFIX = ".jar";
    private static final String ZIP_SUFFIX = ".zip";
    private static final String DEX_DIR = "odex";
    private static final String OPTIMIZE_DEX_DIR = "optimize_dex";
    private static HashSet<File> loadedDex = new HashSet<>();

    static {
        loadedDex.clear();
    }

    /**
     * 加载补丁, 使用默认目录: data/data/包名/files/odex
     *
     * @param context
     */
    public static void loadFixedDex(Context context) {
        loadFixedDex(context, null);
    }

    /**
     * 加载补丁
     *
     * @param context      上下文
     * @param patchFileDir 补丁所在目录
     */
    public static void loadFixedDex(Context context, File patchFileDir) {
        if (context == null) {
            return;
        }
        Log.d(TAG, "loadFixedDex: " + patchFileDir.getAbsolutePath());
        if (patchFileDir == null) {
            // data/data/包名/files/odex（这个可以任意位置）
            patchFileDir = new File(context.getFilesDir(), DEX_DIR);
        }
        File[] listFiles = patchFileDir.listFiles();
        Log.d(TAG, "loadFixedDex: listFiles: " + listFiles + ", " + patchFileDir);
        //遍历所有的修复文件
        for (File file : listFiles) {
            String fileName = file.getName();
            Log.d(TAG, "loadFixedDex: fileName: " + fileName + ", " + file);
            if (fileName.startsWith("classes") || fileName.endsWith(DEX_SUFFIX)
                    || fileName.endsWith(APK_SUFFIX)
                    || fileName.endsWith(JAR_SUFFIX)
                    || fileName.endsWith(ZIP_SUFFIX)) {
                Log.d(TAG, "loadFixedDex: cache, fileName: " + fileName + ", " + file+", "+file.hashCode());
                //存入集合
                loadedDex.add(file);
            }
        }

        //dex 合并前的dex文件
        doDexInject(context, loadedDex);
    }

    private static void doDexInject(Context context, HashSet<File> loadedDex) {
        // data/data/包名/files/optimize_dex（这个必须是自己程序下的目录）
        String optimizeDir = context.getFilesDir().getAbsolutePath() + File.separator + OPTIMIZE_DEX_DIR;
        Log.d(TAG, "doDexInject: optimizeDir: " + optimizeDir);
        Log.d(TAG, "doDexInject: loadedDex: " + loadedDex);
        File fopt = new File(optimizeDir);

        if (!fopt.exists()) {
            fopt.mkdirs();
        }

        try {
            //加载应用程序的dex
            PathClassLoader pathLoader = (PathClassLoader) context.getClassLoader();

            for (File dex : loadedDex) {
                //加载指定的修复的dex文件
                DexClassLoader dexLoader = new DexClassLoader(dex.getAbsolutePath(),//修复好的dex(补丁)所在目录, 指的是补丁所有目录，可以是多个目录（用冒号拼接），而且可以是任意目录，比如说SD卡。
                        fopt.getAbsolutePath(),//存放dex的解压目录(用于jar.zip.apk格式的补丁), 存放从压缩包时解压出来的dex文件的目录，但不能是任意目录，它必须是程序所属的目录才行，比如：data/data/包名/xxx。
                        null,//加载dex时需要的库
                        pathLoader);//父类加载器


                //合并
                Object dexPathList = getPathList(dexLoader);
                Object pathPathList = getPathList(pathLoader);

                Object leftDexElements = getDexElements(dexPathList);
                Object rightDexElements = getDexElements(pathPathList);

                //合并完成
                Object dexElements = combineArray(leftDexElements, rightDexElements);

                Log.d(TAG, "doDexInject: leftDexElements: " + leftDexElements);
                Log.d(TAG, "doDexInject: rightDexElements: " + rightDexElements);
                Log.d(TAG, "doDexInject: dexElements: " + dexElements);

                //重新给pathList里面的Elements[] dexElements 赋值, 将上面合并后的结果赋值给dexElements
                Object pathList = getPathList(pathLoader);//此处一定要重新获取, 不要用pathPathList

                setField(pathList, pathList.getClass(), "dexElements", dexElements);
            }
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "doDexInject: ClassNotFoundException, e: " + e);
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "doDexInject: NoSuchFieldException, e: " + e);
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            Log.e(TAG, "doDexInject: IllegalAccessException, e: " + e);
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(TAG, "doDexInject: Exception, e: " + e);
            e.printStackTrace();
        }
    }

    /**
     * 反射, 给对象中的属性重新赋值
     *
     * @param obj
     * @param cl
     * @param field
     * @param value
     */
    private static void setField(Object obj, Class<?> cl, String field, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = cl.getDeclaredField(field);
        declaredField.setAccessible(true);
        declaredField.set(obj, value);
    }

    /**
     * 数组合并
     *
     * @param arrayLhs
     * @param arrayRhs
     * @return
     */
    private static Object combineArray(Object arrayLhs, Object arrayRhs) {
        final Class<?> componentType = arrayLhs.getClass().getComponentType();
        final int i = Array.getLength(arrayLhs);//得到补丁数组的长度
        final int j = Array.getLength(arrayRhs);//得到原dex数组的长度
        final int k = i + j;//得到总数组长度(补丁数组 + 原dex数组)
        Log.d(TAG, "combineArray: i: " + i + ", j: " + j + ", k: " + k);
        //创建一个类型为componentType, 长度为k的新数组
        final Object result = Array.newInstance(componentType, k);

        /**
         * public static void arraycopy(Object src,
         *          int srcPos,
         *          Object dest,
         *          int destPos,
         *          int length)
         *      src:源数组；	srcPos:源数组要复制的起始位置；
         *      dest:目的数组；	destPos:目的数组放置的起始位置；	length:复制的长度。
         *      注意：src and dest都必须是同类型或者可以进行转换类型的数组．
         */
        //将arrayLhs的内容拷进result
        System.arraycopy(arrayLhs, 0, result, 0, i);
        //将arrayRhs的内容拷进result
        System.arraycopy(arrayRhs, 0, result, i, j);
        return result;
    }

    private static Object getDexElements(Object pathList) throws NoSuchFieldException, IllegalAccessException {
        return getField(pathList, pathList.getClass(), "dexElements");
    }


    private static final String PATH_DALVIK = "dalvik.system.BaseDexClassLoader";

    private static Object getPathList(Object baseDexClassLoader) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        return getField(baseDexClassLoader, Class.forName(PATH_DALVIK), "pathList");
    }

    private static Object getField(Object obj, Class<?> cl, String field) throws NoSuchFieldException, IllegalAccessException {
        Field localField = cl.getDeclaredField(field);
        localField.setAccessible(true);
        return localField.get(obj);
    }

}
