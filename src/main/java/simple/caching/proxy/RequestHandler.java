package simple.caching.proxy;

import lombok.extern.slf4j.Slf4j;
import simple.caching.proxy.cache.CacheHandler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;

@Slf4j
public class RequestHandler implements Runnable {

    Socket clientSocket;
    BufferedReader proxyToClientBr;
    BufferedWriter proxyToClientBw;


    public RequestHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        try {
            this.clientSocket.setSoTimeout(2000);
            proxyToClientBr = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            proxyToClientBw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {

        // Get Request from client
        String requestString;
        try {
            requestString = proxyToClientBr.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            log.debug("Error reading request from client");
            return;
        }

        // Parse out URL
        log.debug("Request Received " + requestString);
        // Get the Request type
        String request = requestString.substring(0, requestString.indexOf(' '));

        // remove request type and space
        String urlString = requestString.substring(requestString.indexOf(' ') + 1);

        // Remove everything past next space
        urlString = urlString.substring(0, urlString.indexOf(' '));

        // Prepend http:// if necessary to create correct URL
        if (!urlString.startsWith("http")) {
            String temp = "http://";
            urlString = temp + urlString;
        }


        // Check if site is blocked
        if (CacheHandler.isBlocked(urlString)) {
            log.debug("Blocked site requested : " + urlString);
            blockedSiteRequested();
            return;
        }


        // Check request type
        if (request.equals("CONNECT")) {
            log.debug("HTTPS Request for : " + urlString + "\n");
            handleHTTPSRequest(urlString);
        } else {
            // Check if we have a cached copy
            File file;
            if ((file = CacheHandler.getCachedPage(urlString)) != null) {
                log.debug("Cached Copy found for : " + urlString + "\n");
                sendCachedPageToClient(file);
            } else {
                log.debug("HTTP GET for : " + urlString + "\n");
                sendNonCachedToClient(urlString);
            }
        }
    }


    private void sendCachedPageToClient(File cachedFile) {
        // Read from File containing cached web page
        try {
            // If file is an image write data to client using buffered image.
            String fileExtension = cachedFile.getName().substring(cachedFile.getName().lastIndexOf('.'));

            // Response that will be sent to the server
            String response;
            if ((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
                    fileExtension.contains(".jpeg") || fileExtension.contains(".gif")) {
                // Read in image from storage
                BufferedImage image = ImageIO.read(cachedFile);

                if (image == null) {
                    log.debug("Image " + cachedFile.getName() + " was null");
                    response = "HTTP/1.0 404 NOT FOUND \n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    proxyToClientBw.write(response);
                    proxyToClientBw.flush();
                } else {
                    response = "HTTP/1.0 200 OK\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    proxyToClientBw.write(response);
                    proxyToClientBw.flush();
                    ImageIO.write(image, fileExtension.substring(1), clientSocket.getOutputStream());
                }
            }

            // Standard text based file requested
            else {
                BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(cachedFile)));

                response = "HTTP/1.0 200 OK\n" +
                        "Proxy-agent: ProxyServer/1.0\n" +
                        "\r\n";
                proxyToClientBw.write(response);
                proxyToClientBw.flush();

                String line;
                while ((line = cachedFileBufferedReader.readLine()) != null) {
                    proxyToClientBw.write(line);
                }
                proxyToClientBw.flush();

                // Close resources
                cachedFileBufferedReader.close();
            }

            // Close Down Resources
            if (proxyToClientBw != null) {
                proxyToClientBw.close();
            }

        } catch (IOException e) {
            log.debug("Error Sending Cached file to client");
            e.printStackTrace();
        }
    }


    private void sendNonCachedToClient(String urlString) {

        try {

            // Compute a logical file name as per schema
            // This allows the files on stored on disk to resemble that of the URL it was taken from
            int fileExtensionIndex = urlString.lastIndexOf(".");
            String fileExtension;

            // Get the type of file
            fileExtension = urlString.substring(fileExtensionIndex);

            // Get the initial file name
            String fileName = urlString.substring(0, fileExtensionIndex);


            // Trim off http://www. as no need for it in file name
            fileName = fileName.substring(fileName.indexOf('.') + 1);

            // Remove any illegal characters from file name
            fileName = fileName.replace("/", "__");
            fileName = fileName.replace('.', '_');

            // Trailing / result in index.html of that directory being fetched
            if (fileExtension.contains("/")) {
                fileExtension = fileExtension.replace("/", "__");
                fileExtension = fileExtension.replace('.', '_');
                fileExtension += ".html";
            }

            fileName = fileName + fileExtension;


            // Attempt to create File to cache to
            boolean caching = true;
            File fileToCache = null;
            BufferedWriter fileToCacheBW = null;

            try {
                // Create File to cache
                fileToCache = new File("cached/" + fileName);

                if (!fileToCache.exists()) {
                    fileToCache.createNewFile();
                }

                // Create Buffered output stream to write to cached copy of file
                fileToCacheBW = new BufferedWriter(new FileWriter(fileToCache));
            } catch (IOException e) {
                log.debug("Couldn't cache: " + fileName);
                caching = false;
                e.printStackTrace();
            } catch (NullPointerException e) {
                log.debug("NPE opening file");
            }


            // Check if file is an image
            URL remoteURL = new URL(urlString);
            if ((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
                    fileExtension.contains(".jpeg") || fileExtension.contains(".gif")) {
                // Create the URL
                BufferedImage image = ImageIO.read(remoteURL);

                if (image != null) {
                    // Cache the image to disk
                    assert fileToCache != null;
                    ImageIO.write(image, fileExtension.substring(1), fileToCache);

                    // Send response code to client
                    String line = "HTTP/1.0 200 OK\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    proxyToClientBw.write(line);
                    proxyToClientBw.flush();

                    // Send them the image data
                    ImageIO.write(image, fileExtension.substring(1), clientSocket.getOutputStream());

                    // No image received from remote server
                } else {
                    log.debug("Sending 404 to client as image wasn't received from server"
                            + fileName);
                    String error = "HTTP/1.0 404 NOT FOUND\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    proxyToClientBw.write(error);
                    proxyToClientBw.flush();
                    return;
                }
            }

            // File is a text file
            else {

                // Create the URL
                // Create a connection to remote server
                HttpURLConnection proxyToServerCon = (HttpURLConnection) remoteURL.openConnection();
                proxyToServerCon.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded");
                proxyToServerCon.setRequestProperty("Content-Language", "en-US");
                proxyToServerCon.setUseCaches(false);
                proxyToServerCon.setDoOutput(true);

                // Create Buffered Reader from remote Server
                BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));


                // Send success code to client
                String line = "HTTP/1.0 200 OK\n" +
                        "Proxy-agent: ProxyServer/1.0\n" +
                        "\r\n";
                proxyToClientBw.write(line);


                // Read from input stream between proxy and remote server
                while ((line = proxyToServerBR.readLine()) != null) {
                    // Send on data to client
                    proxyToClientBw.write(line);

                    // Write to our cached copy of the file
                    if (caching) {
                        assert fileToCacheBW != null;
                        fileToCacheBW.write(line);
                    }
                }

                // Ensure all data is sent by this point
                proxyToClientBw.flush();

                // Close Down Resources
                proxyToServerBR.close();
            }


            if (caching) {
                // Ensure data written and add to our cached hash maps
                assert fileToCacheBW != null;
                fileToCacheBW.flush();
                CacheHandler.addCachedPage(urlString, fileToCache);
            }

            // Close down resources
            if (fileToCacheBW != null) {
                fileToCacheBW.close();
            }

            if (proxyToClientBw != null) {
                proxyToClientBw.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleHTTPSRequest(String urlString) {
        // Extract the URL and port of remote
        String url = urlString.substring(7);
        String[] pieces = url.split(":");
        url = pieces[0];
        int port = Integer.parseInt(pieces[1]);

        try {
            // Only first line of HTTPS request has been read at this point (CONNECT *)
            // Read (and throw away) the rest of the initial data on the stream
            for (int i = 0; i < 5; i++) {
                proxyToClientBr.readLine();
            }

            // Get actual IP associated with this URL through DNS
            InetAddress address = InetAddress.getByName(url);

            // Open a socket to the remote server
            Socket proxyToServerSocket = new Socket(address, port);
            proxyToServerSocket.setSoTimeout(5000);

            // Send Connection established to the client
            String line = "HTTP/1.0 200 Connection established\r\n" +
                    "Proxy-Agent: ProxyServer/1.0\r\n" +
                    "\r\n";
            proxyToClientBw.write(line);
            proxyToClientBw.flush();

            // Client and Remote will both start sending data to proxy at this point
            // Proxy needs to asynchronously read data from each party and send it to the other party

            //Create a Buffered Writer between proxy and remote
            BufferedWriter proxyToServerBW = new BufferedWriter(
                    new OutputStreamWriter(proxyToServerSocket.getOutputStream()));
            // Create Buffered Reader from proxy and remote
            BufferedReader proxyToServerBR = new BufferedReader(
                    new InputStreamReader(proxyToServerSocket.getInputStream()));
            // Create a new thread to listen to client and transmit to server
            ClientToServerHttpsTransmit clientToServerHttps =
                    new ClientToServerHttpsTransmit(clientSocket.getInputStream(), proxyToServerSocket.getOutputStream());

            /*
             * Thread that is used to transmit data read from client to server when using HTTPS
             * Reference to this is required so it can be closed once completed.
             */
            Thread httpsClientToServer = new Thread(clientToServerHttps);
            httpsClientToServer.start();


            // Listen to remote server and relay to client
            try {
                byte[] buffer = new byte[4096];
                int read;
                do {
                    read = proxyToServerSocket.getInputStream().read(buffer);
                    if (read > 0) {
                        clientSocket.getOutputStream().write(buffer, 0, read);
                        if (proxyToServerSocket.getInputStream().available() < 1) {
                            clientSocket.getOutputStream().flush();
                        }
                    }
                } while (read >= 0);
            } catch (SocketTimeoutException ignored) {

            } catch (IOException e) {
                e.printStackTrace();
            }


            // Close Down Resources
            proxyToServerSocket.close();

            proxyToServerBR.close();

            proxyToServerBW.close();

            if (proxyToClientBw != null) {
                proxyToClientBw.close();
            }


        } catch (SocketTimeoutException e) {
            String line = "HTTP/1.0 504 Timeout Occurred after 10s\n" +
                    "User-Agent: ProxyServer/1.0\n" +
                    "\r\n";
            try {
                proxyToClientBw.write(line);
                proxyToClientBw.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } catch (Exception e) {
            log.debug("Error on HTTPS : " + urlString);
            e.printStackTrace();
        }
    }

    private void blockedSiteRequested() {
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            String line = "HTTP/1.0 403 Access Forbidden \n" +
                    "User-Agent: ProxyServer/1.0\n" +
                    "\r\n";
            bufferedWriter.write(line);
            bufferedWriter.flush();
        } catch (IOException e) {
            log.debug("Error writing to client when requested a blocked site");
            e.printStackTrace();
        }
    }

    static class ClientToServerHttpsTransmit implements Runnable {

        InputStream proxyToClientIS;
        OutputStream proxyToServerOS;

        /**
         * Creates Object to Listen to Client and Transmit that data to the server
         *
         * @param proxyToClientIS Stream that proxy uses to receive data from client
         * @param proxyToServerOS Stream that proxy uses to transmit data to remote server
         */
        public ClientToServerHttpsTransmit(InputStream proxyToClientIS, OutputStream proxyToServerOS) {
            this.proxyToClientIS = proxyToClientIS;
            this.proxyToServerOS = proxyToServerOS;
        }

        @Override
        public void run() {
            try {
                // Read byte by byte from client and send directly to server
                byte[] buffer = new byte[4096];
                int read;
                do {
                    read = proxyToClientIS.read(buffer);
                    if (read > 0) {
                        proxyToServerOS.write(buffer, 0, read);
                        if (proxyToClientIS.available() < 1) {
                            proxyToServerOS.flush();
                        }
                    }
                } while (read >= 0);
            } catch (SocketTimeoutException ste) {
                // TODO: handle exception
            } catch (IOException e) {
                log.debug("Proxy to client HTTPS read timed out");
                e.printStackTrace();
            }
        }
    }
}



