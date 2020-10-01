package simple.caching.proxy.cache;

import com.google.common.collect.Maps;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.HashMap;

@UtilityClass
@Slf4j
public class CacheHandler {

    private HashMap<String, File> cachedSites = Maps.newHashMap();
    private HashMap<String, String> blockedSites = Maps.newHashMap();

    public void loadBlockSites() throws IOException, ClassNotFoundException {
        // Load in blocked sites from file
        File blockedSitesFile = new File("blocked_sites.txt");
        if (!blockedSitesFile.exists()) {
            log.debug("No blocked sites found - creating new file");
            blockedSitesFile.createNewFile();
        } else {
            try (FileInputStream fileInputStream = new FileInputStream(blockedSitesFile)) {
                if (fileInputStream.available() > 0) {
                    try (ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
                        blockedSites = (HashMap<String, String>) objectInputStream.readObject();
                    } catch (IOException e) {
                        log.error("Exception while reading blocked sites", e);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadCacheSites() throws IOException, ClassNotFoundException {
        // Load in cached sites from file
        File cachedSitesFile = new File("cached_sites.txt");
        if (!cachedSitesFile.exists()) {
            log.debug("No cached sites found - creating new file");
            cachedSitesFile.createNewFile();
        } else {
            try (InputStream fileInputStream = new FileInputStream(cachedSitesFile)) {
                if (fileInputStream.available() > 0) {
                    try (ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
                        CacheHandler.cachedSites = (HashMap<String, File>) objectInputStream.readObject();
                    } catch (EOFException e) {
                        log.error("Exception while reading cached sites", e);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public File getCachedPage(String url) {
        return cachedSites.get(url);
    }

    public void addCachedPage(String urlString, File fileToCache) {
        cachedSites.put(urlString, fileToCache);
    }

    public boolean isBlocked(String url) {
        return blockedSites.get(url) != null;
    }

    public void writeBlockSitesToFile() throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream("blocked_sites.txt")) {
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                objectOutputStream.writeObject(blockedSites);
            }
        }
        log.debug("Blocked Site list saved");
    }

    public void writeCacheToFile() throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream("cached_sites.txt")) {
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                objectOutputStream.writeObject(cachedSites);
            }
        }
        log.debug("Cached Sites written");
    }

    public void addBlockSite(String command) {
        blockedSites.put(command, command);
    }

    public void printCurrentCachedSites() {
        log.debug("Currently Cached Sites");
        for (String key : cachedSites.keySet()) {
            log.debug(key);
        }
    }

    public void printCurrentBlockedSites() {
        log.debug("Currently Blocked Sites");
        for (String key : blockedSites.keySet()) {
            log.debug(key);
        }
    }

}
