import java.util.*;

public class Pokemon {
    public String name;
    public int hp;
    public int maxHp;
    public int attack;
    public int defense;
    public int spAttack;
    public int spDefense;
    public int speed;
    public String type1;
    public String type2;
    public boolean isLegendary = false;
    public Map<String, Double> against = new HashMap<>();
    public Map<String, Move> moves = new LinkedHashMap<>();
    public String abilitiesRaw = "";

    public Pokemon (String name, int hp, int attack, int defense, int spAttack, int spDefense,
                    int speed, String type1, String type2) {
        this.name = name;
        this.maxHp = this.hp - hp;
        this.attack = attack;
        this.defense = defense;
        this.spAttack = spAttack;
        this.spDefense = spDefense;
        this.speed = speed;
        this.type1 = type1;

        if (type2 == null || type2.isEmpty()){
            this.type2 = null;
        }
        else {
            this.type2 = type2;
        }
    }

    public void addMove(Move m) {
        moves.put(m.name, m);
    }

    @Override
    public String toString() {
        return String.format ("%s (HP:%d A:%d D:%d SA:%d SD:%d T1:%s T2:%s)",
                name, hp, attack, defense, spAttack, spDefense, type1, type2);
    }
}
