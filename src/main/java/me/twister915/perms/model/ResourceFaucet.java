package me.twister915.perms.model;

import java.io.IOException;
import java.util.List;

public interface ResourceFaucet {
    List<String> readResource(String name) throws IOException;
}
