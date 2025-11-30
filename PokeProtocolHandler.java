import java.net.*;
import java.util.*;

public class PokeProtocolHandler implements PokeTransportLayer.MessageListener {
    private final PokeTransportLayer transport;
    private final Map<String, Pokemon> pokemonDB;

    private boolean isServer = false;

    private InetAddress peerIP;
    private int peerPort;

    private Pokemon myPokemon;
    private Pokemon enemyPokemon;

    private int nextSequence = 1;

    private enum State {
        WAIT_HELLO,
        WAIT_WELCOME,
        WAIT_SETUP,
        WAIT_OPP_SETUP,
        WAIT_ATTACK_ANNOUNCE,
        WAIT_DEFENSE_ANNOUNCE,
        WAIT_CALCULATION_REPORT,
        WAIT_CALCULATION_CONFIRM,
        READY_TO_ATTACK,
        READY_TO_DEFEND
    }

    private State state = State.WAIT_HELLO;

    public PokeProtocolHandler(PokeTransportLayer transport, Map<String, Pokemon> db, boolean isServer) {
        this.transport = transport;
        this.pokemonDB = db;
        this.isServer = isServer;
        this.transport.setListener(this);
    }

    private void send(String raw){
        try {
            transport.sendReliableMessage(raw, peerIP, peerPort);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String nextSeq () {
        return "sequence_number: " + (nextSequence++);
    }

    public void startHandshake (String myPokemonName) {
        if (!pokemonDB.containsKey(myPokemonName)) {
            throw new RuntimeException("Pokemon not found!: " + myPokemonName);
        }
        this.myPokemon = pokemonDB.get(myPokemonName);

        if (!isServer) {
            state = State.WAIT_WELCOME;
            String msg = "message_type: HELLO";
            send(msg);
        } else {
            state = State.WAIT_HELLO;
        }
    }

    @Override
    public void onMessageReceived(String rawMessage, int seq, InetAddress ip, int port) {
        this.peerIP = ip;
        this.peerPort = port;

        Map<String, String> fields = parse(rawMessage);

        String type = fields.get("message_type");
        if (type == null)
            return;

        switch(type) {
            case "HELLO": break;
            case "WELCOME": break;
            case "BATTLE_SETUP": break;
            case "ATTACK_ANNOUNCE": break;
            case "DEFENSE_ANNOUNCE": break;
            case "CALCULATION_REPORT": break;
            case "CALCULATION_CONFIRM": break;
            case "RESOLUTION_REQUEST": break;
            default: break;
        }
    }

    private void handleHELLO () {
        if (!isServer)
            return;
        state = State.WAIT_SETUP;
        String msg = "message_type: WELCOME";
        send(msg);
    }

    private void handleWELCOME () {
        if (!isServer)
            return;
        state = State.WAIT_OPP_SETUP;
        sendBattleSetup();
    }

    private void handleBattleSetup (Map<String, String> f) {
        String enemyName = f.get("pokemon_name");
        if (enemyName  == null || !pokemonDB.containsKey(enemyName))
            return;
        enemyPokemon = deepCopy(pokemonDB.get(enemyName));

        if(state == State.WAIT_OPP_SETUP) {
            sendBattleSetup();
            state = State.READY_TO_ATTACK;
        } else if (state == State.WAIT_SETUP) {
            state = State.READY_TO_ATTACK;
        }
    }

    private void handleATTACK_ANNOUNCE(Map<String, String> f) {
        String moveName = f.get("move_name");
        if (moveName  == null || !pokemonDB.containsKey(moveName))
            return;
        state = State.WAIT_DEFENSE_ANNOUNCE;

        String msg = "message_type: DEFENSE_ANNOUNCE\n" + nextSeq();
        send(msg);
    }

    private void handleDEFENSE_ANNOUNCE(Map<String, String> f) {
        state = State.WAIT_CALCULATION_REPORT;
        sendCalcReport();
    }

    private int lastDamageThisTurn = 0;
    private int lastEnemyHPRemaining = 0;

    private void handleCALC_REPORT(Map<String, String> f) {
        int enemyDamage = Integer.parseInt(f.get("damage_dealt"));
        int enemyHP = Integer.parseInt(f.get("defender_hp_remaining"));

        if (enemyDamage == lastDamageThisTurn && enemyHP == lastEnemyHPRemaining) {
            String msg = "message_type: CALCULATION CONFIRM\n" + nextSeq();
            send(msg);
            state = State.READY_TO_ATTACK;
        } else {
            String msg = "message_type: RESOLUTION_REQUEST\n" +
                    "attacker: " + myPokemon.name + "\n" +
                    "move_used: TBD\n" +
                    "damage_dealt: " + lastDamageThisTurn + "\n" +
                    "defender_hp_remaining: " + lastEnemyHPRemaining + "\n" +
                    nextSeq();
            send(msg);
        }
    }

    private void handleCALC_CONFIRM(Map<String, String> f) {
        state = State.READY_TO_ATTACK;
    }

    private void handleRESOLUTION_REQUEST(Map<String, String> f) {
        int correctedHP = Integer.parseInt(f.get("defender_hp_remaining"));
        enemyPokemon.hp = correctedHP;
        state = State.READY_TO_ATTACK;
    }

    private void sendBattleSetup() {
        String msg = "message_type: BATTLE_SETUP\n" +
                "communication_mode: P2P\n" +
                "pokemon_name: " + myPokemon.name + "\n" +
                "stat_boosts: {\"special_attack_uses\":5, \"special_defense_uses\":5}\n";
        send(msg);
    }

    public void attack (String moveName) {
        if(state != State.READY_TO_ATTACK)
            return;
        if(!myPokemon.moves.containsKey(moveName))
            return;
        String msg = "message_type: ATTACK_ANNOUNCE\n" +
                "move_name: " + moveName + "\n" +
                nextSeq();
        send(msg);

        state = State.WAIT_DEFENSE_ANNOUNCE;
    }

    private void sendCalcReport() {
        Move mv = myPokemon.moves.values().iterator().next();
        int dmg = computeDamage(myPokemon, enemyPokemon, mv);

        lastDamageThisTurn = dmg;
        enemyPokemon.hp = Math.max(0, enemyPokemon.hp - dmg);
        lastEnemyHPRemaining = enemyPokemon.hp;

        String msg = "message_type: CALCULATION_REPORT\n" +
                "attacker: " + myPokemon.name + "\n" +
                "move_used: " + mv.name +  "\n" +
                "remaining_health: " + myPokemon.hp + "\n" +
                "damage_dealt: " + dmg + "\n" +
                "defender_hp_remaining: " + enemyPokemon.hp + "\n" +
                "status_message: " + myPokemon.name + " used " + mv.name + "!\n" +
                nextSeq();
        send(msg);
    }

    private int computeDamage(Pokemon atk, Pokemon def, Move mv) {
        double typeMult = 1.0;

        String moveTypeKey = mv.type.toLowerCase();
        if (def.against.containsKey(moveTypeKey)) {
            typeMult = def.against.get(moveTypeKey);
        }

        int atkStat;
        int defStat;

        if (mv.category.equalsIgnoreCase("physical")){
            atkStat = atk.attack;
            defStat = def.defense;
        } else {
            atkStat = atk.spAttack;
            defStat = def.spDefense ;
        }

        double raw = (mv.power * atkStat/(double) defStat) * typeMult;
        int dmg = (int) Math.max(1, Math.round(raw));
        return dmg;
    }

    private Map<String, String> parse(String raw) {
        Map<String, String> map = new HashMap<>();
        for(String line : raw.split("\n")) {
            int k = line.indexOf(":");
            if (k == -1)
                continue;
            String key = line.substring(0, k).trim();
            String value = line.substring(k + 1).trim();
            map.put(key, value);
        }
        return map;
    }

    private Pokemon deepCopy(Pokemon p) {
        Pokemon c = new Pokemon(p.name, p.maxHp, p.attack, p.defense, p.spAttack,
                p.spDefense, p.speed, p.type1, p.type2);
        c.hp = p.hp;
        c.isLegendary = p.isLegendary;
        c.against.putAll(p.against);

        for (Move m : p.moves.values()) {
            c.addMove(new Move(m.name, m.power, m.category, m.type));
        }

        return c;
    }
}
