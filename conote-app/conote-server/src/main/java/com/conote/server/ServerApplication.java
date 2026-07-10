package com.conote.server;

import com.conote.server.network.HttpServerBootstrap;

public class ServerApplication {
    public static void main(String[] args) {
        try {
            HttpServerBootstrap server = new HttpServerBootstrap();
            server.start();
        } catch (Exception exception) {
            System.err.println("Failed to start CoNote server");
            exception.printStackTrace();
        }
    }
}