import java.net.*;
import java.io.File;
import java.util.*;

public class PokeBattleApp {
  
  private static final int DEFAULT_PORT = 5000;
  private static final String DEFAULT_IP = "127.0.0.1";
  private static final String DEFAULT_CSV_PATH = "pokemon.csv"; 
  
  private static Map<String, Pokemon> loadPokemonData(String filePath) throws Exception {
    File csvFile = new File(filePath);
    return CSVLoader.load(csvFile);
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.out.println("Usage: java PokeBattleApp <server|client> <PokemonName> [opponentIP] [csv_file_path]");
      return;
    }
    
    boolean isServer = args[0].equalsIgnoreCase("server");
    String myPokemonName = args[1].toUpperCase();
    String opponentIP = isServer ? null : (args.length > 2 ? args[2] : DEFAULT_IP);
    String csvFilePath = args.length > 3 ? args[3] : DEFAULT_CSV_PATH;

    Map<String, Pokemon> pokemonDB = loadPokemonData(csvFilePath);
    
    System.out.println("Starting PokeBattleApp...");
    int listeningPort = isServer ? DEFAULT_PORT : 0;
    PokeTransportLayer transport = new PokeTransportLayer(listeningPort);
    
    PokeProtocolHandler handler = new PokeProtocolHandler(transport, pokemonDB, isServer);
    
    Thread listenThread = new Thread(() -> {
      try {
        transport.listen();
      } catch (Exception e) {
        System.err.println("Listen Thread Error: " + e.getMessage());
      }
    });
    listenThread.start();
    
    Thread retransmitThread = new Thread(() -> {
      try {
        transport.retransmissionLoop();
      } catch (Exception e) {
        System.err.println("Retransmission Thread Error: " + e.getMessage());
      }
    });
    retransmitThread.start();
    
    if (isServer) {
      System.out.println("SERVER mode. Waiting for HELLO message on port " + DEFAULT_PORT);
    } else {
      System.out.println("CLIENT mode. Connecting to " + opponentIP + ":" + DEFAULT_PORT);
      // FIX: Set the destination address for the client's first message
      handler.setPeerAddress(opponentIP, DEFAULT_PORT);
      handler.startHandshake(myPokemonName);
    }

    Scanner scanner = new Scanner(System.in);
    String line;
    
    while (true) {
      System.out.print("> ");
      if (scanner.hasNextLine()) {
        line = scanner.nextLine().trim();
      } else {
        break;
      }
      
      String[] parts = line.split(" ", 2);
      String command = parts[0].toLowerCase();
      String argument = parts.length > 1 ? parts[1].trim() : "";

      try {
        switch (command) {
          case "attack":
            handler.attack(argument); 
            break;
          case "chat":
            handler.sendChatMessage(argument);
            break;
          case "boost":
            handler.useSpecialAttackBoost();
            break;
          case "quit":
          case "exit":
            System.out.println("Shutting down...");
            System.exit(0);
            break;
          default:
            System.out.println("Unknown command. Use: attack <moveName>, boost, chat <message>, or quit.");
            break;
        }
      } catch (Exception e) {
        System.err.println("Command processing error: " + e.getMessage());
      }
    }
  }
}