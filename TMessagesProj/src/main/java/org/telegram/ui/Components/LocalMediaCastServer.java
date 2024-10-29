package org.telegram.ui.Components;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

public class LocalMediaCastServer {

    private static final int PORT = 8080;
    private static final String SCHEME_FILE = "file";
    private static final String SCHEME_CONTENT = "content";
    private static final String SCHEME_TG = "tg";
    private static final String TAG = "LocalMediaCastServer";

    @Nullable
    private static LocalMediaCastServer instance;

    @NonNull
    private final Application application;

    @NonNull
    private final DataSource.Factory dataSourceFactory;

    @Nullable
    private ServerSocket serverSocket;

    @Nullable
    private volatile Uri uri;

    private volatile boolean isRunning;


    private LocalMediaCastServer(
        @NonNull Context context,
        @NonNull DataSource.Factory factory
    ) {
        this.application = (Application) context.getApplicationContext();
        this.dataSourceFactory = factory;
    }


    @NonNull
    public static LocalMediaCastServer getInstance(
        @NonNull Context context,
        @NonNull DataSource.Factory factory
    ) {
        if (instance == null) {
            instance = new LocalMediaCastServer(context, factory);
        }
        return instance;
    }

    public void setUri(@Nullable Uri uri) {
        this.uri = uri;
        Log.i(TAG, "Using uri " + uri);
    }

    @Nullable
    public Uri getContentUri() {
        String ip = getWifiIp();
        return ip != null ? Uri.parse("http://" + ip + ":" + PORT + "/" + System.currentTimeMillis()) : null;
    }

    @Nullable
    private String getWifiIp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager cm = (ConnectivityManager) application
                .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    LinkProperties linkProperties = cm.getLinkProperties(cm.getActiveNetwork());
                    if (linkProperties != null) {
                        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                            InetAddress inetAddress = linkAddress.getAddress();
                            if (inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                }
            }
        } else {
            WifiManager wifiManager = (WifiManager) application.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    (ipAddress & 0xff),
                    (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff),
                    (ipAddress >> 24 & 0xff)
                );
            }
        }

        return null;
    }

    public void start() {
        if (isRunning) {
            return;
        }

        isRunning = true;

        new Thread(() -> {
            while (isRunning) {
                try {
                    ServerSocket serverSocket = getServerSocket();
                    if (serverSocket == null) {
                        Log.e(TAG, "Can't run the server: socket is null");
                        isRunning = false;
                        return;
                    }
                    if (isRunning) {
                        Socket socket = serverSocket.accept();
                        handleRequest(socket);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error accepting connection", e);
                }
            }
        }).start();


        Log.i(TAG, "Server is started");
    }

    private void handleRequest(@NonNull Socket socket) {
        Uri uri = this.uri;

        OutputStream socketOutputStream = null;
        BufferedReader requestReader = null;
        DataSource dataSource = null;
        boolean keepAlive = false;

        try {
            socketOutputStream = socket.getOutputStream();

            if (uri == null) {
                String response = "HTTP/1.1 404 Not Found\r\n\r\n";
                socketOutputStream.write(response.getBytes());
                return;
            }

            long fileLength = getFileLength(uri);

            requestReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String req = "";
            String line = requestReader.readLine();
            req += line + "\n";
            String range = null;

            while (line != null && !line.isEmpty()) {
                if (line.startsWith("Range:")) {
                    range = line.substring(6).trim();
                }
                if (line.equalsIgnoreCase("Connection: keep-alive")) {
                    keepAlive = true;
                }
                line = requestReader.readLine();
                req += line + "\n";
            }
            Log.i(TAG, req);


            if (range != null) {
                String[] ranges = range.replace("bytes=", "").split("-");
                long start = Long.parseLong(ranges[0]);
                long end = ranges.length > 1 ? Long.parseLong(ranges[1]) : fileLength -1;
                long contentLength = end - start + 1;

                boolean isContentLengthValid = contentLength != C.TIME_UNSET;
                String responseHeaders = "HTTP/1.1 206 Partial Content\r\n" +
                    "Content-Type: video/mp4\r\n" +
                    "Content-Length: " + contentLength + "\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Content-Range: bytes " + start + "-" + end + "/" + fileLength + "\r\n" +
                    (keepAlive && isContentLengthValid ? "Connection: keep-alive\r\n" : "Connection: close\r\n") +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "\r\n";
                //Log.i(TAG, responseHeaders);
                socketOutputStream.write(responseHeaders.getBytes());

                if (isContentLengthValid) {
                    DataSpec dataSpec = new DataSpec(uri, start, contentLength);
                    dataSource = dataSourceFactory.createDataSource();
                    dataSource.open(dataSpec);

                    byte[] buffer = new byte[4096];
                    long bytesSent = 0;
                    int bytesRead;
                    while (bytesSent < contentLength && (bytesRead = dataSource.read(buffer, 0, buffer.length)) != -1) {
                        socketOutputStream.write(buffer, 0, bytesRead);
                        bytesSent += bytesRead;
                    }
                } else {
                    keepAlive = false;
                }
            } else {
                String responseHeaders = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: video/mp4\r\n" +
                    "Content-Length: " + fileLength + "\r\n" +
                    (keepAlive ? "Connection: keep-alive\r\n" : "Connection: close\r\n") +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "\r\n";
                //Log.i(TAG, responseHeaders);
                socketOutputStream.write(responseHeaders.getBytes());

                byte[] buffer = new byte[4096];
                int bytesRead;
//                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
//                    socketOutputStream.write(buffer, 0, bytesRead);
//                }
            }

            if (!keepAlive) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error handling request", e);
        } finally {
            try {
                if (socketOutputStream != null) {
                    socketOutputStream.close();
                }
                if (requestReader != null) {
                    requestReader.close();
                }
                if (dataSource != null) {
                    dataSource.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "Can't close streams");
            }
        }
    }

    private long getFileLength(@NonNull Uri uri) {
        String scheme = uri.getScheme();
        if (scheme != null) {
            switch (scheme) {
                case SCHEME_CONTENT:
                    String[] projection = new String[] { MediaStore.Video.Media.SIZE };
                    Cursor cursor = application.getContentResolver().query(uri, projection, null, null, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            long size = cursor.getLong(0);
                            cursor.close();
                            return size;
                        }
                        cursor.close();
                    }
                    break;
                case SCHEME_FILE:
                    File file = new File(uri.getPath());
                    return file.length();
                case SCHEME_TG:
                    try {
                        return Long.parseLong(uri.getQueryParameter("size"));
                    } catch (NumberFormatException nfe) {
                        Log.w(TAG, "Unable to parse file size from tg uri");
                        return 0;
                    }
            }
        }

        Log.w(TAG, "Can't read file length by provided uri " + uri);
        return 0;
    }

    public void stop() {
        if (serverSocket == null || !isRunning) {
            return;
        }

        try {
            isRunning = false;
            serverSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error stopping server", e);
        }
    }

    @Nullable
    private ServerSocket getServerSocket() {
        if (serverSocket == null || serverSocket.isClosed()) {
            try {
                serverSocket = new ServerSocket(PORT);
            } catch (IOException ioe) {
                Log.w(TAG, "Error creating server", ioe);
                return null;
            }
        }
        return serverSocket;
    }

}

