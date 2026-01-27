package de.mhus.nimbus.world.shared.archive;

import java.io.IOException;
import java.io.InputStream;

public interface WArchiveService {

    void archive(String path, InputStream stream) throws IOException;

}
