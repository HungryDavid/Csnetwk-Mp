import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class CSVLoader {
    public static Map<String, Pokemon> load(File csvFile) throws Exception {
        Map<String, Pokemon> out = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String header = br.readLine();
            if (header == null) {
                return out;
            }

            String[] cols = header.split(",", -1);
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < cols.length; i++){
                idx.put(cols[i].trim(), i);
            }

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] row = splitCsvLine(line);

                String name = safeGet(row, idx, "name");
                if (name == null || name.isBlank()) continue;

                int hp = parseIntSafe(safeGet(row, idx, "hp"), 100);
                int attack = parseIntSafe(safeGet(row, idx, "attack"), 50);
                int defense = parseIntSafe(safeGet(row, idx, "defense"), 50);
                int spAttack = parseIntSafe(safeGet(row, idx, "sp_attack"), 50);
                int spDefense = parseIntSafe(safeGet(row, idx, "sp_defense"), 50);
                int speed = parseIntSafe(safeGet(row, idx, "speed"), 50);

                String type1 = safeGet(row, idx, "type1");
                String type2 = safeGet(row, idx, "type2");

                Pokemon p = new Pokemon(name, hp, attack, defense, spAttack, spDefense, speed,
                        type1, type2);

                for (int c = 0; c < row.length; c++){
                    String colName = cols[c].trim();
                    if (colName.startsWith("against_")){
                        String t = colName.substring("against_".length());
                        String val = row[c].trim();
                        double d = 1.0;
                        try {
                            d = Double.parseDouble(val);
                        } catch (Exception ignored){}
                        p.against.put(t, d);
                    }
                }

                p.addMove(new Move("Tackle", 40, "physical", "Normal"));

                String elementalType;
                if (type1 == null || type1.isEmpty()){
                    elementalType = "Normal";
                } else{
                    elementalType = type1;
                }

                p.addMove(new Move("Elemental Beam", 60, "special", elementalType));
                p.addMove(new Move("Power Hit", 75, "physical", "Normal"));
                p.addMove(new Move("Neutral Burst", 50, "special", "Normal"));

                out.put(name, p);
            }
        }
        return out;
    }

    private static String[] splitCsvLine(String line) {
        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (ch == ',' && !inQuotes) {
                pieces.add(cur.toString());
                cur.setLength(0);
            }else {
                cur.append(ch);
            }
        }
        pieces.add(cur.toString());
        return pieces.toArray(new String[0]);
    }

    private static String safeGet(String[] row, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null)
            return "";
        if (i < 0 || i >= row.length)
            return "";
        return row[i].trim();
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null || s.isEmpty())
            return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
