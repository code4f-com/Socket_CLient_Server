/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tuanpla.server;

import com.tuanpla.common.DateProc;
import com.tuanpla.common.Tool;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author TUANPLA
 */
public class ClientSession implements Runnable {

    public static final Map<String, ClientSession> CLIENT_REIGS = new HashMap<>();
    private final Socket clientSocket;
    BufferedReader inpst;
    DataOutputStream outpst;
    String clientName;
    String sign;
    //--
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private long timeout;

    public static enum COMMAND {
        REG("REG"),
        ENQ("ENQ"), // Nhan ket qua tu CMC OK
        MESS("MES"), // Da gui toi box chua biet the nao
        CLOSE("CLO"), // Box gui thanh cong toi User
        ERROR("ERR"), // 
        ;
        public String val;

        private COMMAND(String val) {
            this.val = val;
        }
    }

    public static enum CODE {
        EXIST_SESSION(1, "Session Exit"),
        INVALID_SESSION_CLODE(2, "Invalid Session Clode"),
        INVALID_COMMAND(3, "Invalid Command"), //--
        ;
        public int val;
        public String desc;

        private CODE(int val, String desc) {
            this.val = val;
            this.desc = desc;
        }

        public String getMessage() {
            return this.val + "." + this.desc;
        }
    }

    //--
    public ClientSession(Socket clientSocket) {
        this.clientSocket = clientSocket;
        try {
//            this.clientSocket.setSoTimeout(5000);
            this.setTimeout(500); // 2 s
//            this.clientSocket.setKeepAlive(true);
            inpst = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            outpst = new DataOutputStream(clientSocket.getOutputStream());
            outpst.writeBytes("Accepted!" + System.lineSeparator());
            new EnquireLinkSender().start();
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    @Override
    public void run() {
        while (!clientSocket.isClosed()) {
            try {
                String clMessage = readMess(inpst);
                processMessage(clMessage);
            } catch (SocketTimeoutException s) {
                System.out.println("Socket timed out pst....!");
            } catch (IOException e) {
                try {
                    if (clientName != null) {
                        removeClient(clientName);
                    }
                    System.out.println("Socket is Close :" + clientSocket.hashCode());
                    clientSocket.close();
                } catch (Exception ex) {
                }
            }
        }
    }

    private void processMessage(String message) throws IOException {
        try {
            if (message != null && !message.equals("")) {
                if (message.startsWith(COMMAND.CLOSE.val)) {
                    doClose(message);
                } else if (message.startsWith(COMMAND.ENQ.val)) {
                    doEQR(message);
                } else if (message.startsWith(COMMAND.MESS.val)) {
                    Tool.debug(DateProc.createTimestamp() + ": Client send Message: " + message);
                } else if (message.startsWith(COMMAND.REG.val)) {
                    doRegis(message);
                } else {
                    sendMessage(COMMAND.ERROR.val + ":" + CODE.EXIST_SESSION.getMessage());
                }

            } else {
                System.out.println("Mesage is null or Empty");
            }
        } catch (InterruptedException e) {
            System.out.println("Process message error:" + e.getMessage());
            // TODO: handle exception
        }
    }

    private void doEQR(String message) throws InterruptedException, IOException {
        String[] arr = message.split(":");
        if (arr != null && arr.length >= 2) {
            sendMessage(COMMAND.ENQ.val + ":" + arr[1]);
        } else {
            sendMessage(COMMAND.ERROR.val + ":" + CODE.EXIST_SESSION.getMessage());
        }
    }

    private void doClose(String message) throws InterruptedException, IOException {
        String[] arr = message.split(":");
        if (arr != null && arr.length >= 2) {
            removeClient(arr[1]);
            closeSK("user:" + arr[1] + " is bind Close");
        } else {
            sendMessage(COMMAND.MESS.val + ":" + CODE.EXIST_SESSION.getMessage());
        }
    }

    private void doRegis(String message) throws InterruptedException, IOException {
        String[] arr = message.split(":");
        if (arr != null && arr.length >= 3) {
            clientName = arr[1];
            ClientSession receiver = CLIENT_REIGS.get(arr[1]);
            if (receiver != null) {
                receiver.sendMessage(COMMAND.ERROR.val + ":" + CODE.EXIST_SESSION.getMessage());
            } else {
                this.setSign(arr[2]);
                regis(clientName, this);
                System.out.println("Not find client Socket in Regismap:" + arr[1]);
            }

            sendMessage("Registed");
        } else {

        }
    }

    private void regis(String username, ClientSession client) {
        System.out.println("Register Client:" + clientName + ":" + this.hashCode());
        CLIENT_REIGS.put(username, client);
    }

    private void removeClient(String username) {
        System.out.println("Remove Client From Map:" + clientName + ":" + this.hashCode() + ":" + DateProc.createTimestamp());
        CLIENT_REIGS.remove(username);
    }

    private int sendMessage(String message) throws InterruptedException, IOException {
        int result = 0;
        lock.lock();
        try {
            //write to toClient
            System.out.println("Send Message to:" + clientSocket.hashCode());
            PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outpst)), true);
            writer.println(message);
            try {
                condition.await(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw e;
            }
//                Tool.debug("Sending request to Socket Server");
//                    String response = (String) _read.readLine();
//                    if (response != null && response.startsWith("ASK:")) {
//                        // Phan hoi cho thang Gui
//                        writeMess(response, outpst);
//                    }
//                    System.outpst.println("Response from client:" + response);
        } catch (Exception e) {
            throw e;
//            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        return result;
    }

    private int sendEQR() throws IOException {
        int result = 0;
        try {
            //write to toClient
            outpst.write((byte) 1);
        } catch (IOException e) {
            throw e;
        }
        return result;
    }

    private String readMess(BufferedReader read) throws IOException {
        String content = read.readLine();
        if (content == null) {
            // Throw io exception since possibly connection has been lost
            throw new IOException();
        }
        System.out.println("Client data Send:[" + content + "]" + DateProc.createTimestamp());
        return content;
    }

    public void closeSK(String funCall) {
        try {
            if (clientSocket != null) {
                Tool.debug("Socket Session:" + this.hashCode() + " is Close [issue:" + funCall + "]");
                clientSocket.close();
                inpst = null;
                outpst = null;
            } else {
                Tool.debug("Socket not exist when you call Close it...");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Tool.debug("===>>Cho nay thu xem co loi khong nao:" + ex.getMessage());
        }
    }

    private class EnquireLinkSender extends Thread {

        @Override
        public void run() {
            while (clientSocket != null && AppStart.isRuning && !clientSocket.isClosed()) {
                try {
                    sendEQR();
                    Thread.sleep(500);
                } catch (IOException | InterruptedException e) {
                    closeSK("EnquireLinkSender stop [" + clientName + "] by Error:" + e.getMessage());
                    Tool.debug(Tool.getLogMessage(e));
                }
            }
            Tool.debug("EnquireLinkSender [" + clientName + "] stop");
        }
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public BufferedReader getInpst() {
        return inpst;
    }

    public void setInpst(BufferedReader inpst) {
        this.inpst = inpst;
    }

    public DataOutputStream getOutpst() {
        return outpst;
    }

    public void setOutpst(DataOutputStream outpst) {
        this.outpst = outpst;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

}
