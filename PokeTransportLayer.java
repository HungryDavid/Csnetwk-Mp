import java.net.*;
import java.util.*;

public class PokeTransportLayer {
  private DatagramSocket socket;
  private int currentSequence = 0;
  private Map<Integer, String> awaitingAck = new HashMap<>(); // waits for ack number
  private Map<Integer, Long> sendTime = new HashMap<>();
  private Map<Integer, Integer> retryCount = new HashMap<>();
  private static final int TIMEOUT_MS = 500;
  private static final int MAX_RETRIES = 3;
  private MessageListener listener;
  private Map<Integer, InetAddress> destIp = new HashMap<>();
  private Map<Integer, Integer> destPort = new HashMap<>();

  public PokeTransportLayer(int port) throws Exception {
    socket = new DatagramSocket(port);
  }
  
  public interface MessageListener {
    void onMessageReceived(String rawMessage, int seq, InetAddress ip, int port);
  }

  public void setListener(MessageListener l) {
    this.listener = l;
  }

  public void sendReliableMessage(String message, InetAddress ip, int port) throws Exception {
    int seq = currentSequence++;

    String msgWithSeq = message + "\nsequence_number: " + seq;

    byte[] data = msgWithSeq.getBytes();
    DatagramPacket packet = new DatagramPacket(data, data.length, ip, port);
    socket.send(packet);

    awaitingAck.put(seq, msgWithSeq);
    sendTime.put(seq, System.currentTimeMillis());
    retryCount.put(seq, 0);

    destIp.put(seq, ip); // store the destination
    destPort.put(seq, port);
  }

  public void sendAck(int ackNum, InetAddress ip, int port) throws Exception {
    String ack = "message_type: ACK\nack_number: " + ackNum;
    byte[] data = ack.getBytes();
    DatagramPacket packet = new DatagramPacket(data, data.length, ip, port);
    socket.send(packet);
  }

  public void listen() throws Exception {
    byte[] buffer = new byte[4096];

    while (true) {
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      socket.receive(packet);

      String received = new String(packet.getData(), 0, packet.getLength());
      handleIncomingPacket(received, packet.getAddress(), packet.getPort());
    }
  }

  private void handleIncomingPacket(String msg, InetAddress ip, int port) throws Exception {
    if (msg.startsWith("message_type: ACK")) {// if ack is recieved
      int ackNum = extractAckNumber(msg);
      awaitingAck.remove(ackNum);
      sendTime.remove(ackNum);
      retryCount.remove(ackNum);
      return;
    }
   
    int seqNum = extractSequenceNumber(msg); // extract sequence number

    sendAck(seqNum, ip, port); // send ack back

    if (listener != null) {//if not null then pass to protocol layer
      listener.onMessageReceived(msg, seqNum, ip, port);
    }
  }

  private int extractAckNumber(String msg) {
    for (String line : msg.split("\n")) {
      if (line.startsWith("ack_number:")) {
        return Integer.parseInt(line.split(":")[1].trim());
      }
    }
    return -1;
  }

  private int extractSequenceNumber(String msg) {
    for (String line : msg.split("\n")) {
      if (line.startsWith("sequence_number:")) {
        return Integer.parseInt(line.split(":")[1].trim());
      }
    }
    return -1;
  }

  public void retransmissionLoop() throws Exception { // run function on a exclusive thread
    while (true) {
      long now = System.currentTimeMillis();
      for (int seq : new ArrayList<>(awaitingAck.keySet())) {
        long lastSent = sendTime.get(seq);
        int retries = retryCount.get(seq);
        if (now - lastSent >= TIMEOUT_MS) {
          if (retries >= MAX_RETRIES) {
            System.out.println("FAILED: seq " + seq + " dropped after max retries.");
            awaitingAck.remove(seq);
            retryCount.remove(seq);
            sendTime.remove(seq);
            continue;
          }

          // resend the message
          String msg = awaitingAck.get(seq);
          InetAddress ip = destIp.get(seq);
          int port = destPort.get(seq);

          byte[] data = msg.getBytes();
          DatagramPacket packet = new DatagramPacket(data, data.length, ip, port);
          socket.send(packet);

          System.out.println("Retransmitting seq " + seq);

          if (retries >= MAX_RETRIES) { // remove message if max entry is reached
            System.out.println("FAILED: seq " + seq + " dropped after max retries.");
            awaitingAck.remove(seq);
            retryCount.remove(seq);
            sendTime.remove(seq);
            destIp.remove(seq);
            destPort.remove(seq);
            continue;
          }

          sendTime.put(seq, now);
          retryCount.put(seq, retries + 1);
        }
      }

      Thread.sleep(50);
    }
  }

}

