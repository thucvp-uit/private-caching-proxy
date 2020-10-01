package simple.caching.proxy;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import simple.caching.proxy.cache.CacheHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Scanner;

@Slf4j
public class ProxyServer implements Runnable {

    private final ArrayList<Thread> servicingThreads = Lists.newArrayList();
    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;

    public ProxyServer(int port) {
        // Start dynamic manager on a separate thread.
        new Thread(this).start();    // Starts overridden run() method at bottom

        try {
            CacheHandler.loadCacheSites();
            CacheHandler.loadBlockSites();
        } catch (IOException e) {
            log.debug("Error loading previously cached sites file");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            log.debug("Class not found loading in previously cached sites file");
            e.printStackTrace();
        }

        try {
            // Create the Server Socket for the Proxy
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(100000);
            // Set the timeout
            log.debug("Waiting for client on port " + serverSocket.getLocalPort() + " ..");
            isRunning = true;
        }

        // Catch exceptions associated with opening socket
        catch (SocketException se) {
            log.debug("Socket Exception when connecting to client");
            se.printStackTrace();
        } catch (SocketTimeoutException ste) {
            log.debug("Timeout occurred while connecting to client");
        } catch (IOException io) {
            log.debug("IO exception when connecting to client");
        }
    }


    public void listen() {
        while (isRunning) {
            try {
                // serverSocket.accept() Blocks until a connection is made
                Socket socket = serverSocket.accept();

                // Create new Thread and pass it Runnable RequestHandler
                Thread thread = new Thread(new RequestHandler(socket));

                // Key a reference to each thread so they can be joined later if necessary
                servicingThreads.add(thread);

                thread.start();
            } catch (SocketException e) {
                // Socket exception is triggered by management system to shut down the proxy
                log.debug("Server closed");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeServer() {
        log.debug("Closing Server..");
        isRunning = false;
        try {
            CacheHandler.writeCacheToFile();

            CacheHandler.writeBlockSitesToFile();
            try {
                // Close all servicing threads
                for (Thread thread : servicingThreads) {
                    if (thread.isAlive()) {
                        System.out.print("Waiting on " + thread.getId() + " to close..");
                        thread.join();
                        log.debug(" closed");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            log.debug("Error saving cache/blocked sites");
            e.printStackTrace();
        }

        // Close Server Socket
        try {
            log.debug("Terminating Connection");
            serverSocket.close();
        } catch (Exception e) {
            log.debug("Exception closing proxy's server socket");
            e.printStackTrace();
        }

    }


    /**
     * Creates a management interface which can dynamically update the proxy configurations
     * blocked  : Lists currently blocked sites
     * cached	: Lists currently cached sites
     * close	: Closes the proxy server
     * *		: Adds * to the list of blocked sites
     */
    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);

        String command;
        while (isRunning) {
            log.debug("Enter new site to block, or type \"blocked\" to see blocked sites, \"cached\" to see cached sites, or \"close\" to close server.");
            command = scanner.nextLine();
            if (command.toLowerCase().equals("blocked")) {
                CacheHandler.printCurrentBlockedSites();
            } else if (command.toLowerCase().equals("cached")) {
                CacheHandler.printCurrentCachedSites();
            } else if (command.equals("close")) {
                isRunning = false;
                closeServer();
            } else {
                CacheHandler.addBlockSite(command);
                log.debug("{} blocked successfully", command);
            }
        }
        scanner.close();
    }
}
