package de.mhus.nimbus.world.shared.archive;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class WArchiveToFile implements WArchiveService {

    @Value("${nimbus.world.archive.path:external/nimbus-archive}")
    private String archivePath;

    @PostConstruct
    public void init() {
        log.info("Archive path set to {}", archivePath);
        new File(archivePath).mkdirs();
    }

    @Override
    public void archive(String path, InputStream stream) throws IOException {
        path = normalzePath(path);
        var date = new java.text.SimpleDateFormat("yyyy/MM/dd/HH-mm-ss-SSS").format(new java.util.Date());
        File target = new File(archivePath + "/" + date + "/" + path);
        target.getParentFile().mkdirs();
        log.info("Archive to file {}", target.getAbsolutePath());
        java.nio.file.Files.copy(stream, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private String normalzePath(String path) {
        path = path.replace('\\', '/').replaceAll("//+", "/").replaceAll("/\\./", "/");
        if (path.startsWith("/")) path = path.substring(1);
        return path;
    }
}
