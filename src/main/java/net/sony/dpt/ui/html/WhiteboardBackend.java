package net.sony.dpt.ui.html;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.sony.dpt.command.device.TakeScreenshotCommand;
import net.sony.util.LogWriter;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WhiteboardBackend implements HttpHandler {

    private final TakeScreenshotCommand takeScreenshotCommand;
    private String lastScreenshotBase64;
    private final Object screenshotLock;
    private String frontendHtml;

    private final LogWriter logWriter;

    public WhiteboardBackend(final TakeScreenshotCommand takeScreenshotCommand, final LogWriter logWriter) throws IOException {
        this.logWriter = logWriter;
        this.takeScreenshotCommand = takeScreenshotCommand;
        this.screenshotLock = new Object();


        try (InputStream frontendHtmlStream = WhiteboardBackend.class.getClassLoader().getResourceAsStream("whiteboard/frontend.html")) {
            if (frontendHtmlStream != null) {
                this.frontendHtml = IOUtils.toString(frontendHtmlStream, StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Open a server distributing both screenshot from the DPT and an html frontend to read them
     */
    public void bind(int port) throws IOException {
        logWriter.log("We will open a new server binding on 0.0.0.0:" + port);

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/lastImage", this);
        server.createContext("/frontend", this);
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        logWriter.log("We will regularly pool the DPT for new image data");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try(InputStream imageStream = takeScreenshotCommand.fastScreenshot()) {
                writeLastScreenshotBase64(IOUtils.toByteArray(imageStream));
            } catch (IOException | InterruptedException ignored) {
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public int bind() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        serverSocket.close();
        bind(port);
        return port;
    }

    public String getLastScreenshotBase64() {
        synchronized (screenshotLock) {
            return lastScreenshotBase64;
        }
    }

    public void writeLastScreenshotBase64(byte[] jpegContent) {
        String temp = Base64.getEncoder().encodeToString(jpegContent);
        if (temp != null) {
            synchronized (screenshotLock) {
                lastScreenshotBase64 = temp;
            }
        }
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (!"GET".equals(httpExchange.getRequestMethod())) return;

        switch (httpExchange.getRequestURI().getPath()) {
            case "/lastImage":
                String lastImage = getLastScreenshotBase64();
                if (lastImage == null || lastImage.isEmpty()) {
                    error(httpExchange);
                } else {
                    httpExchange.getResponseHeaders().add("content-type", "image/jpeg");
                    writeString(httpExchange, lastImage);
                }
                break;
            case "/frontend":
                writeString(httpExchange, frontendHtml);
                break;
            default:
                error(httpExchange);
        }
    }

    public void error(HttpExchange httpExchange) throws IOException {
        httpExchange.sendResponseHeaders(500, 0);
    }

    public void writeString(HttpExchange httpExchange, String content) throws IOException {
        if (content == null) {
            error(httpExchange);
            return;
        }
        httpExchange.sendResponseHeaders(200, content.length());
        httpExchange.getResponseBody().write(content.getBytes());
        httpExchange.getResponseBody().flush();
        httpExchange.getResponseBody().close();
    }
}
