package com.wizzardo.http;

import com.wizzardo.epoll.IOThread;

/**
 * Created by wizzardo on 02.01.16.
 */
public class HttpIOThread<T extends HttpConnection> extends IOThread<T> implements Buffer {

    protected AbstractHttpServer<T> server;
    protected byte[] buffer = new byte[getBuffer().capacity()];
    protected int position;
    protected int limit;

    public HttpIOThread(AbstractHttpServer<T> server, int number, int divider) {
        super(number, divider);
        this.server = server;
    }

    @Override
    public byte[] bytes() {
        return buffer;
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public void position(int position) {
        this.position = position;
    }

    @Override
    public int limit() {
        return limit;
    }

    @Override
    public void limit(int limit) {
        this.limit = limit;
    }

    @Override
    public int capacity() {
        return buffer.length;
    }

    @Override
    public boolean hasRemaining() {
        return position < limit;
    }

    @Override
    public int remains() {
        return limit - position;
    }

    @Override
    public void clear() {
        limit = 0;
        position = 0;
    }
}
