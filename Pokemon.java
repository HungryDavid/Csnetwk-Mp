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
    public int spAttackBoostsRemaining = 5;
    public int spDefenseBoostsRemaining = 5;

    public Pokemon (String name, int hp, int attack, int defense, int spAttack, int spDefense,
                    int speed, String type1, String type2) {
        this.name = name;
        this.hp = hp;
        this.maxHp = hp; 
        
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
        moves.put(m.name.toUpperCase(), m); 
    }
    public String getName() { 
        return name; 
    }
    public int getHp() { 
        return hp; 
    }
    public void setHp(int hp) { 
        this.hp = hp; 
    }
    public int getAttack() { return attack; }
    public int getDefense() { return defense; }
    public int getSpAttack() { return spAttack; }
    public int getSpDefense() { return spDefense; }
    public String getType1() { return type1; }
    public String getType2() { return type2; }
    public Move getMove(String moveName) { 
        return moves.get(moveName.toUpperCase()); 
    } 

    public String getStatsString() {
        return String.format("%d,%d,%d,%d,%d,%d", maxHp, attack, defense, spAttack, spDefense, speed);
    }
    
    public double getEffectiveMultiplier(String moveType) {
        return against.getOrDefault(moveType.toLowerCase(), 1.0);
    }

    @Override
    public String toString() {
        return String.format ("%s (HP:%d A:%d D:%d SA:%d SD:%d T1:%s T2:%s)",
                name, hp, attack, defense, spAttack, spDefense, type1, type2);
    }
}