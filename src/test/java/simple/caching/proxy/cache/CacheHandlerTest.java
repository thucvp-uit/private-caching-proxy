package simple.caching.proxy.cache;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;

import java.io.File;
import java.io.IOException;

@Slf4j
class CacheHandlerTest {

    @BeforeEach
    void setUp() {
        log.info("Start test");
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testLoadBlockSites() throws IOException, ClassNotFoundException {
        CacheHandler.loadBlockSites();
        Assert.assertTrue(new File("blocked_sites.txt").exists());
    }

    @Test
    void testLoadCacheSites() throws IOException, ClassNotFoundException {
        CacheHandler.loadCacheSites();
        Assert.assertTrue(new File("cached_sites.txt").exists());
    }

    @Test
    void testGetCachedPage() {
        File result = CacheHandler.getCachedPage("url");
        Assertions.assertNull(result);
    }

    @Test
    void testAddCachedPage() {
        CacheHandler.addCachedPage("urlString", new File("cached_sites.txt"));
    }

    @Test
    void testIsBlocked() {
        log.warn("something to print");
        boolean result = CacheHandler.isBlocked("url");
        Assertions.assertFalse(result);
    }

    @Test
    void testWriteBlockSitesToFile() throws IOException {
        CacheHandler.writeBlockSitesToFile();
    }

    @Test
    void testWriteCacheToFile() throws IOException {
        CacheHandler.writeCacheToFile();
    }

    @Test
    void testAddBlockSite() {
        CacheHandler.addBlockSite("google.com");
        boolean isBlocked = CacheHandler.isBlocked("google.com");
        Assert.assertTrue(isBlocked);
    }

    @Test
    void testPrintCurrentCachedSites() {
        CacheHandler.printCurrentCachedSites();
    }

    @Test
    void testPrintCurrentBlockedSites() {
        CacheHandler.addBlockSite("google.com");
        CacheHandler.printCurrentBlockedSites();
    }
}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme