package com.wizzardo.http;

import com.wizzardo.epoll.EpollServer;
import com.wizzardo.epoll.IOThread;
import com.wizzardo.http.request.Header;
import com.wizzardo.http.request.Request;
import com.wizzardo.http.response.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author: moxa
 * Date: 11/5/13
 */
public class HttpServer extends EpollServer<HttpConnection> {

    private Response staticResponse = new Response()
            .appendHeader(Header.KEY_CONNECTION, Header.VALUE_CONNECTION_KEEP_ALIVE)
            .appendHeader(Header.KEY_CONTENT_TYPE, Header.VALUE_CONTENT_TYPE_HTML_UTF8)
            .setBody("It's alive!".getBytes())
            .makeStatic();

    private BlockingQueue<HttpConnection> queue = new LinkedBlockingQueue<>();
    private int workersCount;
    private int sessionTimeoutSec = 30 * 60;
    private FiltersMapping filtersMapping = new FiltersMapping();
    private volatile Handler handler = (request, response) -> staticResponse;

    public HttpServer(int port) {
        this(null, port);
    }

    public HttpServer(String host, int port) {
        this(host, port, 0);
    }

    public HttpServer(String host, int port, int workersCount) {
        super(host, port);
        this.workersCount = workersCount;

        System.out.println("worker count: " + workersCount);
        for (int i = 0; i < workersCount; i++) {
            new Worker(queue, "worker_" + i) {
                @Override
                protected void process(HttpConnection connection) {
                    handle(connection);
                }
            };
        }
    }

    @Override
    public void run() {
        Session.createSessionsHolder(sessionTimeoutSec);
        super.run();
    }

    @Override
    protected HttpConnection createConnection(int fd, int ip, int port) {
        return new HttpConnection(fd, ip, port);
    }

    @Override
    protected IOThread<HttpConnection> createIOThread(int number, int divider) {
        return new HttpIOThread(number, divider);
    }

    private class HttpIOThread extends IOThread<HttpConnection> {

        public HttpIOThread(int number, int divider) {
            super(number, divider);
        }

        @Override
        public void onRead(final HttpConnection connection) {
            if (connection.getState() == HttpConnection.State.READING_INPUT_STREAM) {
                connection.getInputStream().wakeUp();
                return;
            }

            if (connection.processListener())
                return;

            ByteBuffer b;
            try {
                while ((b = read(connection, connection.getBufferSize())).limit() > 0) {
                    if (connection.check(b))
                        break;
                }
                if (!connection.isRequestReady())
                    return;

            } catch (IOException e) {
                e.printStackTrace();
                try {
                    connection.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                return;
            }

            if (workersCount > 0)
                queue.add(connection);
            else
                handle(connection);
        }

        @Override
        public void onWrite(HttpConnection connection) {
            if (connection.hasDataToWrite())
                connection.write();
            else if (connection.getState() == HttpConnection.State.WRITING_OUTPUT_STREAM)
                connection.getOutputStream().wakeUp();
        }
    }

    protected void handle(HttpConnection connection) {
        try {
            Request request = connection.getRequest();
            Response response = connection.getResponse();

            if (!filtersMapping.before(request, response)) {
                finishHandling(connection);
                return;
            }

            response = handler.handle(request, response);

            filtersMapping.after(request, response);

            finishHandling(connection);
        } catch (Exception t) {
            t.printStackTrace();
            //TODO render error page
            try {
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void finishHandling(HttpConnection connection) throws IOException {
        if (connection.getState() == HttpConnection.State.WRITING_OUTPUT_STREAM)
            connection.getOutputStream().flush();

        if (connection.getResponse().isProcessed())
            return;

        connection.write(connection.getResponse().toReadableBytes());
        connection.onFinishingHandling();
    }

    public FiltersMapping getFiltersMapping() {
        return filtersMapping;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public void setSessionTimeout(int sec) {
        this.sessionTimeoutSec = sec;
    }

    public static void main(String[] args) {
        HttpServer server = new HttpServer(null, 8084, args.length > 0 ? Integer.parseInt(args[0]) : 0);
        server.setIoThreadsCount(args.length > 1 ? Integer.parseInt(args[1]) : 4);
//        server.setHandler(new UrlHandler()
//                        .append("/static/*", new FileTreeHandler("/home/wizzardo/", "/static"))
//                        .append("/echo", new WebSocketHandler() {
//                            @Override
//                            public void onMessage(WebSocketListener listener, Message message) {
//                                System.out.println(message.asString());
//                                listener.sendMessage(message);
//                            }
//                        }).append("/time", new WebSocketHandler() {
//                            {
//                                final Thread thread = new Thread(() -> {
//                                    while (true) {
//                                        try {
//                                            Thread.sleep(1000);
//                                        } catch (InterruptedException ignored) {
//                                        }
//
//                                        broadcast(new Date().toString());
//                                    }
//                                });
//                                thread.setDaemon(true);
//                                thread.start();
//                            }
//
//                            ConcurrentLinkedQueue<WebSocketListener> listeners = new ConcurrentLinkedQueue<>();
//
//                            void broadcast(String message) {
//                                Message m = new Message().append(message);
//
//                                Iterator<WebSocketListener> iter = listeners.iterator();
//                                while (iter.hasNext()) {
//                                    WebSocketListener listener = iter.next();
//                                    listener.sendMessage(m);
//                                }
//                            }
//
//                            @Override
//                            public void onConnect(WebSocketListener listener) {
//                                listeners.add(listener);
//                            }
//                        })
//        );
        server.start();
    }
}
