package com.conote.server;

import com.conote.server.network.SocketServerBootstrap;

public class ServerApplication {
    public static void main(String[] args) throws Exception {
        SocketServerBootstrap server = new SocketServerBootstrap();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "conote-shutdown"));
        server.start();
    }
}
