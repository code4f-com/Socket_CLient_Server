/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tuanpla.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author TUANPLA
 */
public class AppStart extends Thread {

    private static final int severPort = 8888;
    public static boolean isRuning = true;
    private final ServerSocket serverSocket;
    public static final ExecutorService waitBindExecService = Executors.newFixedThreadPool(10);

    public AppStart(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(10000);
    }

    @Override
    public void run() {
        while (isRuning) {
            try {
//                System.out.println("Sever available on port " + serverSocket.getLocalPort() + "..." + DateProc.createTimestamp());
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                System.out.println("Just connected to " + clientSocket.getRemoteSocketAddress());
                waitBindExecService.execute(new ClientSession(clientSocket));
            } catch (SocketTimeoutException s) {
                // System.out.println("Socket timed out!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        isRuning = false;
    }

    public static void main(String[] args) {
        try {
            Thread sv = new AppStart(severPort);
            sv.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
