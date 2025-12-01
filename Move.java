public class Move {
    public String name;
    public int basePower; 
    public String category;
    public String type;

    public Move(String name, int basePower, String category, String type) {
        this.name = name;
        this.basePower = basePower;
        this.category = category;
        this.type = type;
    }
    
    public String getName() { 
        return name; 
    }

    public int getBasePower() { 
        return basePower; 
    }

    public String getCategory() { 
        return category; 
    }

    public String getType() { 
        return type; 
    }

    @Override
    public String toString() {
        return name + " (" + type + " " + " pow: " + basePower + ")";
    }
}