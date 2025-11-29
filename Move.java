public class Move {
    public String name;
    public int power;
    public String category;
    public String type;

    public Move(String name, int power, String category, String type) {
        this.name = name;
        this.power = power;
        this.category = category;
        this.type = type;
    }

    @Override
    public String toString() {
        return name + " (" + type + " " + " pow: " + power + ")";
    }
}
