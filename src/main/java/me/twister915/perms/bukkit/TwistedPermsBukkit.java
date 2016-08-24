package me.twister915.perms.bukkit;

import lombok.Getter;
import me.twister915.perms.model.PermissionsManager;
import me.twister915.perms.model.ResourceFaucet;
import me.twister915.perms.model.SQLDataSource;
import me.twister915.perms.model._IDataSource;
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

    private PermissionsManager permissionsManager;
    private Yaml yaml = new Yaml(new CustomClassLoaderConstructor(getClass().getClassLoader()));

    @Override
    protected void onModuleEnable() throws Exception {
        instance = this;
        permissionsManager = new PermissionsManager(new BukkitPlayerSource(this), new BukkitThreadModel(this), getDataSource(), this);
    }

    protected _IDataSource getDataSource() throws Exception {
        String dataSource = getConfig().getString("data-source", "sql");
        switch (dataSource.toLowerCase().trim()) {
            case "sql":
                return construct(SQLDataSource.class);
        }
        throw new IllegalArgumentException("");
    }

    private <T extends _IDataSource> T construct(Class<T> dataSourceType) throws Exception {
        MavenLibraries annotation = dataSourceType.getAnnotation(MavenLibraries.class);
        if (annotation != null && !getClass().isAnnotationPresent(IgnoreLibraries.class))
            LibraryHandler.load(this, annotation.value());

        Constructor<?> constructor = dataSourceType.getDeclaredConstructors()[0];
        Class<?> configType = constructor.getParameterTypes()[0];
        return (T) constructor.newInstance(yaml.loadAs(getResource("database.yml"), configType));
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
