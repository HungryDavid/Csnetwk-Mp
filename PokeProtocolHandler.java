import java.net.*;
import java.math.BigInteger;
import java.util.*;

public class PokeProtocolHandler {

    private enum State {
        INIT,
        READY_TO_ATTACK,
        READY_TO_DEFEND,
        AWAITING_RESOLUTION,
        AWAITING_CONFIRMATION,
        GAME_OVER
    }

    private final PokeTransportLayer transport;
    private final Map<String, Pokemon> pokemonDB;
    private final boolean isServer;

    private State currentState = State.INIT;
    private Pokemon myPokemon;
    private Pokemon opponentPokemon;
    private BigInteger battleSeed;

    private InetAddress peerIP;
    private int peerPort;
    
    private int mySpecialAttackBoosts = 1;
    private int mySpecialDefenseBoosts = 1;
    private boolean isSpecialAttackActive = false;
    private boolean isSpecialDefenseActive = false;

    private String announcedMoveName;
    private int announcedDamage;

    public PokeProtocolHandler(PokeTransportLayer transport, Map<String, Pokemon> pokemonDB, boolean isServer) {
        this.transport = transport;
        this.pokemonDB = pokemonDB;
        this.isServer = isServer;
        transport.setHandler(this);

        if (isServer) {
            currentState = State.READY_TO_DEFEND;
        }
    }

    public void setPeerAddress(String ipAddress, int port) throws UnknownHostException {
        this.peerIP = InetAddress.getByName(ipAddress);
        this.peerPort = port;
    }

    private void send(String message) {
        if (peerIP == null || peerPort == 0) {
            System.err.println("Cannot send: Peer address not set.");
            return;
        }
        try {
            transport.sendReliableMessage(message, peerIP, peerPort);
        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    private String buildMessage(String command, String... args) {
        StringBuilder sb = new StringBuilder(command);
        for (String arg : args) {
            sb.append("|").append(arg);
        }
        return sb.toString();
    }

    private Map<String, String> parseMessage(String rawMessage) {
        Map<String, String> parts = new HashMap<>();
        String[] tokens = rawMessage.split("\\|", -1);
        if (tokens.length > 0) {
            parts.put("command", tokens[0]);
        }
        for (int i = 1; i < tokens.length; i++) {
            parts.put("arg" + i, tokens[i]);
        }
        return parts;
    }

    public void startHandshake(String myPokemonName) {
        this.myPokemon = pokemonDB.get(myPokemonName);
        if (myPokemon == null) {
            throw new RuntimeException("Pokemon not found!: " + myPokemonName);
        }

        String setup = buildMessage("HELLO", myPokemonName);
        send(setup);
    }

    public void onMessageReceived(String rawMessage, int seq, InetAddress ip, int port) {
        this.peerIP = ip;
        this.peerPort = port;

        Map<String, String> message = parseMessage(rawMessage);
        String command = message.get("command");
        
        System.out.println("\n[Protocol] Received: " + command);

        switch (command) {
            case "HELLO":
                handleHello(message.get("arg1"));
                break;
            case "WELCOME":
                handleWelcome(message.get("arg1"));
                break;
            case "BATTLE_SETUP":
                handleBattleSetup(message.get("arg1"), message.get("arg2"), message.get("arg3"), message.get("arg4"));
                break;
            case "ATTACK_ANNOUNCE":
                handleAttackAnnounce(message.get("arg1"), message.get("arg2"));
                break;
            case "RESOLUTION_REQUEST":
                handleResolutionRequest(message.get("arg1"), message.get("arg2"));
                break;
            case "CALCULATION_REPORT":
                handleCalculationReport(message.get("arg1"), message.get("arg2"));
                break;
            case "CALCULATION_CONFIRM":
                handleCalculationConfirm();
                break;
            case "CHAT":
                handleChat(message.get("arg1"));
                break;
            case "BOOST_REQUEST":
                handleBoostRequest(message.get("arg1"));
                break;
            default:
                System.out.println("[Error] Unknown command: " + command);
        }
        printStatus();
    }
    
    private void handleHello(String opponentName) {
        this.myPokemon = pokemonDB.get(isServer ? opponentName : myPokemon.getName().toUpperCase());
        if (myPokemon == null) {
            throw new RuntimeException("Pokemon not found!: " + myPokemon.getName());
        }
        this.opponentPokemon = pokemonDB.get(opponentName);
        
        battleSeed = new BigInteger(256, new Random());
        System.out.println("[System] Received HELLO from " + opponentName + ". Generating battle seed.");
        
        String welcome = buildMessage("WELCOME", battleSeed.toString());
        send(welcome);
        
        String setup = buildMessage("BATTLE_SETUP", myPokemon.getName().toUpperCase(), myPokemon.getStatsString(), String.valueOf(mySpecialAttackBoosts), String.valueOf(mySpecialDefenseBoosts));
        send(setup);
        
        currentState = State.AWAITING_RESOLUTION;
    }
    
    private void handleWelcome(String seed) {
        battleSeed = new BigInteger(seed);
        System.out.println("[System] Received WELCOME. Seed stored: " + battleSeed.toString());
        
        String setup = buildMessage("BATTLE_SETUP", myPokemon.getName().toUpperCase(), myPokemon.getStatsString(), String.valueOf(mySpecialAttackBoosts), String.valueOf(mySpecialDefenseBoosts));
        send(setup);
        
        currentState = State.AWAITING_RESOLUTION;
    }
    
    // In PokeProtocolHandler.java

private void handleBattleSetup(String opponentName, String opponentStats, String spAttackBoosts, String spDefenseBoosts) {
    this.opponentPokemon = pokemonDB.get(opponentName);
    if (opponentPokemon == null) {
         throw new RuntimeException("Opponent Pokemon not found!: " + opponentName);
    }
    
    // --- FIX START: Parse and Apply Opponent Stats ---
    String[] stats = opponentStats.split(",");
    
    // We expect 6 stats: maxHp, attack, defense, spAttack, spDefense, speed
    if (stats.length == 6) {
        // Since the opponent is sending its CURRENT stats, apply them
        // Note: The stats string should ideally contain the MAX HP, not current HP,
        // but based on your protocol, we'll parse and use it here.
        
        int maxHp = Integer.parseInt(stats[0]);
        int attack = Integer.parseInt(stats[1]);
        int defense = Integer.parseInt(stats[2]);
        int spAttack = Integer.parseInt(stats[3]);
        int spDefense = Integer.parseInt(stats[4]);
        int speed = Integer.parseInt(stats[5]);
        
        // Update opponentPokemon object with the received stats
        this.opponentPokemon.maxHp = maxHp;
        this.opponentPokemon.hp = maxHp; // Assume opponent starts at full HP
        this.opponentPokemon.attack = attack;
        this.opponentPokemon.defense = defense;
        this.opponentPokemon.spAttack = spAttack;
        this.opponentPokemon.spDefense = spDefense;
        this.opponentPokemon.speed = speed;
        
    } else {
        System.err.println("[Error] Received invalid number of stats for opponent: " + opponentStats);
    }
    // --- FIX END ---
    
    if (isServer && currentState == State.AWAITING_RESOLUTION) {
        currentState = State.READY_TO_ATTACK;
        System.out.println("[System] Setup complete. Ready to ATTACK.");
    } else if (!isServer && currentState == State.AWAITING_RESOLUTION) {
        currentState = State.READY_TO_DEFEND;
        System.out.println("[System] Setup complete. Ready to DEFEND.");
    }
}

    public void attack(String moveName) {
        if (currentState != State.READY_TO_ATTACK) {
            System.out.println("[Error] Cannot attack: Not your turn.");
            return;
        }
        
        // FIX: Using top-level Move class
        Move move = myPokemon.getMove(moveName);
        if (move == null) {
             System.out.println("[Error] Move not found: " + moveName);
             return;
        }

        String boostStatus = isSpecialAttackActive ? "BOOSTED" : "NONE";
        String announce = buildMessage("ATTACK_ANNOUNCE", moveName, boostStatus);
        send(announce);

        isSpecialAttackActive = false;
        currentState = State.AWAITING_RESOLUTION;
    }

    public void useSpecialAttackBoost() {
        if (currentState != State.READY_TO_ATTACK) {
            System.out.println("[Error] Can only BOOST on your turn to ATTACK.");
            return;
        }
        if (mySpecialAttackBoosts > 0) {
            mySpecialAttackBoosts--;
            isSpecialAttackActive = true;
            System.out.println("[System] Special Attack boost activated for the next attack.");
        } else {
            System.out.println("[Error] No Special Attack boosts remaining.");
        }
    }

    public void sendChatMessage(String message) {
        String chat = buildMessage("CHAT", message);
        send(chat);
        System.out.println("[You] " + message);
    }
    
    private void handleChat(String message) {
        System.out.println("[Opponent] " + message);
    }

    private void handleBoostRequest(String boostType) {
        if (boostType.equals("SP_DEFENSE")) {
            isSpecialDefenseActive = true;
            mySpecialDefenseBoosts--;
            System.out.println("[Opponent] Used Special Defense boost for this turn!");
        }
    }

    private void handleAttackAnnounce(String moveName, String boostStatus) {
        if (currentState != State.READY_TO_DEFEND) {
            System.out.println("[Error] Unexpected ATTACK_ANNOUNCE.");
            return;
        }
        
        // FIX: Using top-level Move class
        Move move = opponentPokemon.getMove(moveName);
        if (move == null) {
            System.out.println("[Error] Opponent's move not found: " + moveName);
            return;
        }
        
        if (boostStatus.equals("BOOSTED")) {
            System.out.println("[Opponent] Announced a Special Attack boost!");
        }

        if (mySpecialDefenseBoosts > 0) {
            mySpecialDefenseBoosts--;
            isSpecialDefenseActive = true;
            String boostRequest = buildMessage("BOOST_REQUEST", "SP_DEFENSE");
            send(boostRequest);
            System.out.println("[System] Used Special Defense boost in response to attack!");
        }

        int damage = calculateDamage(move, opponentPokemon, myPokemon, isSpecialAttackActive, isSpecialDefenseActive);
        
        isSpecialDefenseActive = false;

        this.announcedMoveName = moveName;
        this.announcedDamage = damage;
        
        String request = buildMessage("RESOLUTION_REQUEST", moveName, String.valueOf(damage));
        send(request);
        
        currentState = State.AWAITING_RESOLUTION;
    }

    private void handleResolutionRequest(String moveName, String damageStr) {
        if (currentState != State.AWAITING_RESOLUTION) {
            System.out.println("[Error] Unexpected RESOLUTION_REQUEST.");
            return;
        }
        
        int announcedDamage = Integer.parseInt(damageStr);
        
        // FIX: Using top-level Move class
        Move move = opponentPokemon.getMove(moveName);
        
        int myCalculatedDamage = calculateDamage(move, opponentPokemon, myPokemon, isSpecialAttackActive, isSpecialDefenseActive);
        isSpecialDefenseActive = false;

        if (myCalculatedDamage == announcedDamage) {
            System.out.println("[System] Local damage calculation verified: " + myCalculatedDamage);
            
            myPokemon.setHp(Math.max(0, myPokemon.getHp() - myCalculatedDamage));
            
            String report = buildMessage("CALCULATION_REPORT", moveName, damageStr);
            send(report);

            currentState = State.AWAITING_CONFIRMATION;
        } else {
            System.err.println("[Error] Damage mismatch! Local: " + myCalculatedDamage + ", Peer: " + announcedDamage);
        }
    }
    
    private void handleCalculationReport(String moveName, String damageStr) {
        int damage = Integer.parseInt(damageStr);
        
        opponentPokemon.setHp(Math.max(0, opponentPokemon.getHp() - damage));
        
        System.out.println("[System] Damage Report received and applied. Opponent's HP: " + opponentPokemon.getHp());

        String confirm = buildMessage("CALCULATION_CONFIRM", "OK");
        send(confirm);
        
        currentState = State.AWAITING_CONFIRMATION;
    }

    private void handleCalculationConfirm() {
        if (myPokemon.getHp() <= 0 || opponentPokemon.getHp() <= 0) {
            currentState = State.GAME_OVER;
            System.out.println("[System] Battle Over!");
            if (myPokemon.getHp() > opponentPokemon.getHp()) {
                System.out.println("[System] YOU WIN!");
            } else if (opponentPokemon.getHp() > myPokemon.getHp()) {
                System.out.println("[System] YOU LOSE!");
            } else {
                System.out.println("[System] It's a DRAW!");
            }
            return;
        }

        if (currentState == State.AWAITING_CONFIRMATION) {
            if (isServer) {
                currentState = State.READY_TO_DEFEND;
                System.out.println("[System] Turn finished. Ready to DEFEND.");
            } else {
                currentState = State.READY_TO_ATTACK;
                System.out.println("[System] Turn finished. Ready to ATTACK.");
            }
        }
    }

    // FIX: Changed Pokemon.Move to Move
    private int calculateDamage(Move move, Pokemon attacker, Pokemon defender, boolean spAttackBoost, boolean spDefenseBoost) {
        double basePower = move.getBasePower();
        double attackStat;
        double defenseStat;
        double typeEffectiveness;

        if ("physical".equalsIgnoreCase(move.getCategory())) {
            attackStat = attacker.getAttack();
            defenseStat = defender.getDefense();
        } else { // "special"
            attackStat = attacker.getSpAttack();
            defenseStat = defender.getSpDefense();
            
            if (spAttackBoost) {
                attackStat *= 1.5;
            }
            if (spDefenseBoost) {
                defenseStat *= 1.5;
            }
        }
        
        typeEffectiveness = defender.getEffectiveMultiplier(move.getType());

        double damageValue = basePower * (attackStat / defenseStat) * typeEffectiveness;
        
        long seed = battleSeed.longValue() + attacker.hashCode() + defender.hashCode();
        Random rng = new Random(seed);
        double randomFactor = 0.85 + (rng.nextDouble() * 0.15);
        
        damageValue *= randomFactor;

        if (move.getType().equalsIgnoreCase(attacker.getType1()) || move.getType().equalsIgnoreCase(attacker.getType2())) {
            damageValue *= 1.5;
        }
        
        return Math.max(1, (int) Math.round(damageValue));
    }
    
    private void printStatus() {
        if (myPokemon != null) {
            System.out.print(myPokemon.getName() + " HP: " + myPokemon.getHp());
            System.out.print(" | Opponent " + (opponentPokemon != null ? opponentPokemon.getName() : "???") + " HP: " + (opponentPokemon != null ? opponentPokemon.getHp() : "???"));
            System.out.println(" | State: " + currentState);
        }
        System.out.print("> ");
    }
}