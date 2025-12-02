import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PokeTransportLayer {
    private static final int RETRANSMISSION_TIMEOUT_MS = 500;
    private static final int MAX_PACKET_SIZE = 1024;
    private static final int LISTEN_TIMEOUT_MS = 100;

    private DatagramSocket socket;
    private PokeProtocolHandler handler;
    private final Map<Integer, PacketInfo> unackedMessages = new ConcurrentHashMap<>();
    private int mySeq = 0;
    private int peerSeq = -1;

    private class PacketInfo {
        final byte[] data;
        final InetAddress address;
        final int port;
        long timestamp;
        int sequence;

        PacketInfo(byte[] data, InetAddress address, int port, int sequence) {
            this.data = data;
            this.address = address;
            this.port = port;
            this.sequence = sequence;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public PokeTransportLayer(int port) throws SocketException {
        this.socket = new DatagramSocket(port);
        this.socket.setSoTimeout(LISTEN_TIMEOUT_MS);
        System.out.println("[Transport] Listening on port: " + this.socket.getLocalPort());
    }

    public void setHandler(PokeProtocolHandler handler) {
        this.handler = handler;
    }

    private void send(byte[] data, InetAddress address, int port) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    private void sendAck(int seq, InetAddress address, int port) throws IOException {
        String ackMessage = "ACK|" + seq;
        send(ackMessage.getBytes(), address, port);
    }

    public void sendReliableMessage(String messageBody, InetAddress address, int port) throws IOException {
        int currentSeq = mySeq++;
        String fullMessage = "DATA|" + currentSeq + "|" + messageBody;
        byte[] data = fullMessage.getBytes();

        PacketInfo info = new PacketInfo(data, address, port, currentSeq);
        unackedMessages.put(currentSeq, info);

        send(data, address, port);
    }

    public void retransmissionLoop() {
        while (true) {
            try {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<Integer, PacketInfo>> iterator = unackedMessages.entrySet().iterator();

                while (iterator.hasNext()) {
                    PacketInfo info = iterator.next().getValue();
                    if (now - info.timestamp > RETRANSMISSION_TIMEOUT_MS) {
                        System.out.println("[Transport] Retransmitting sequence: " + info.sequence);
                        // Update timestamp and resend
                        info.timestamp = now;
                        send(info.data, info.address, info.port);
                    }
                }
                Thread.sleep(RETRANSMISSION_TIMEOUT_MS / 4); // Check frequently
            } catch (SocketException e) {
                // Socket closed, exit loop
                break;
            } catch (Exception e) {
                System.err.println("[Transport] Retransmission error: " + e.getMessage());
            }
        }
    }

// --- Listening Method ---

    public void listen() throws Exception {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            try {
                socket.receive(packet);
                String rawMessage = new String(packet.getData(), 0, packet.getLength());
                InetAddress peerIP = packet.getAddress();
                int peerPort = packet.getPort();

                handleIncomingMessage(rawMessage, peerIP, peerPort);
            } catch (SocketTimeoutException ignored) {
            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                System.err.println("[Transport] Listen error: " + e.getMessage());
            }
        }
    }

    private void handleIncomingMessage(String rawMessage, InetAddress ip, int port) throws IOException {
        String[] parts = rawMessage.split("\\|", 3);
        String type = parts[0];
        if (handler == null) return; 

        if ("ACK".equals(type) && parts.length >= 2) {
            int seq = Integer.parseInt(parts[1]);
            if (unackedMessages.remove(seq) != null) {
                System.out.println("[Transport] ACK received for sequence: " + seq);
            }
        } else if ("DATA".equals(type) && parts.length >= 3) {
            int seq = Integer.parseInt(parts[1]);
            String messageBody = parts[2];
            sendAck(seq, ip, port);
            
            if (seq > peerSeq) {
                handler.onMessageReceived(messageBody, seq, ip, port);
                peerSeq = seq; 
            } else if (seq == peerSeq) {
                 System.out.println("[Transport] Duplicate data packet received (Seq: " + seq + "). Dropped message body.");
            } else {
                 System.out.println("[Transport] Out-of-order data packet received (Seq: " + seq + "). Dropped message body.");
            }
        }
    }

    public void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("[Transport] Socket closed.");
        }
    }
}
