package me.twister915.perms.bukkit;

import lombok.Getter;
import me.twister915.perms.model.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;
import tech.rayline.core.library.IgnoreLibraries;
import tech.rayline.core.library.LibraryHandler;
import tech.rayline.core.library.MavenLibraries;
import tech.rayline.core.plugin.RedemptivePlugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class TwistedPermsBukkit extends RedemptivePlugin implements ResourceFaucet {
    @Getter private static TwistedPermsBukkit instance;

    private BukkitThreadModel threadModel;
    private PermissionsManager permissionsManager;
    private Yaml yaml = new Yaml(new CustomClassLoaderConstructor(getClass().getClassLoader()));

    @Override
    protected void onModuleEnable() throws Exception {
        instance = this;
        threadModel = new BukkitThreadModel(this);
        permissionsManager = new PermissionsManager(new BukkitPlayerSource(this), threadModel, getDataSource(), this);
    }

    protected PreDataSource getDataSource() throws Exception {
        String dataSource = getConfig().getString("data-source", "sql");
        switch (dataSource.toLowerCase().trim()) {
            case "sql":
                return construct(SQLDataSource.class);
        }
        throw new IllegalArgumentException("Invalid data source specified!");
    }

    private PreDataSource construct(Class<? extends PreDataSource> dataSourceType) throws Exception {
        MavenLibraries annotation = dataSourceType.getAnnotation(MavenLibraries.class);
        if (annotation != null && !getClass().isAnnotationPresent(IgnoreLibraries.class))
            LibraryHandler.load(this, annotation.value());

        Constructor<?> constructor = dataSourceType.getDeclaredConstructors()[0];
        Class<?> configType = constructor.getParameterTypes()[0];
        return (PreDataSource) constructor.newInstance(yaml.loadAs(getResource("database.yml"), configType));
    }

    @Override
    protected void onModuleDisable() throws Exception {
        permissionsManager.onDisable();
    }

    @Override
    public List<String> readResource(String name) throws IOException {
        InputStream resource = getResource(name);
        if (resource == null)
            throw new IllegalArgumentException("Invalid name supplied!");

        StringBuilder builder = new StringBuilder();
        List<String> lines = new ArrayList<>();

        int c;
        while ((c = resource.read()) > 0) {
            char c1 = (char) c;
            if (c1 == '\n') {
                lines.add(builder.toString());
                builder = new StringBuilder();
            }
            else
                builder.append(c1);
        }

        lines.add(builder.toString());
        return lines;
    }
}
