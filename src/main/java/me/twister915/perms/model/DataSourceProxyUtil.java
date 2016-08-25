package me.twister915.perms.model;

import rx.Scheduler;
import rx.Single;
import rx.functions.Action0;
import rx.functions.Action1;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

class DataSourceProxyUtil {
    private DataSourceProxyUtil() {}

    @SuppressWarnings({"unchecked", "ConfusingArgumentToVarargsMethod"})
    public static IDataSourceUnsafe proxy(Object root, ThreadModel model, Action1<Throwable> handler) throws IllegalAccessException {
        if (!(root instanceof _IDataSource))
            throw new IllegalArgumentException("Invalid data source provided!");

        Class<?> aClass = root.getClass();
        Scheduler.Worker worker = model.getAsync().createWorker();
        IDataSourceUnsafe proxied = (IDataSourceUnsafe) Proxy.newProxyInstance(aClass.getClassLoader(), new Class[]{IDataSourceUnsafe.class}, (proxy, method, args) -> {
            if (method.isDefault())
                return method.invoke(proxy, args);

            if (method.getName().equals("unsafe"))
                return root;

            Method method1 = aClass.getMethod(method.getName(), method.getParameterTypes());

            if (method.getReturnType() == Single.class) {
                return Single.create(subscriber -> {
                    Action0 runnable = () -> {
                        try {
                            method1.invoke(root, args);
                        } catch (IllegalAccessException e) {
                            subscriber.onError(e);
                        } catch (InvocationTargetException e) {
                            subscriber.onError(e.getTargetException());
                        }
                    };
                    if (model.isPrimaryThread()) {
                        worker.schedule(runnable);
                    } else {
                        runnable.call();
                    }
                }).doOnError(handler);
            }

            if (!model.isPrimaryThread())
                return method1.invoke(root, args);

            if (method.getReturnType() == void.class) {
                worker.schedule(() -> {
                    try {
                        method1.invoke(root, args);
                    } catch (IllegalAccessException e) {
                        handler.call(e);
                    } catch (InvocationTargetException e) {
                        handler.call(e.getTargetException());
                    }
                });
            }

            return method1.invoke(root, args);
        });

        for (Field field : aClass.getDeclaredFields()) {
            if (field.getType() == IDataSource.class) {
                field.setAccessible(true);
                field.set(root, proxied);
                break;
            }
        }

        return proxied;
    }
}
