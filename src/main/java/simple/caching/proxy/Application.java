package simple.caching.proxy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

    // Main method for the program
    public static void main(String[] args) {
        log.debug("Starting server");
        // Create an instance of Proxy and begin listening for connections
        ProxyServer myProxyServer = new ProxyServer(8080);
        myProxyServer.listen();
    }
}
