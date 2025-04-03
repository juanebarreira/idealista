import java.io.*;
import java.nio.file.*;
import java.util.Set;
import java.util.HashSet;

public class AdPersistence {

    private static final String FILE_PATH = "seen-ads.txt";

    public static Set<String> loadSeenAds() {
        Set<String> seen = new HashSet<>();
        try {
            if (Files.exists(Paths.get(FILE_PATH))) {
                seen.addAll(Files.readAllLines(Paths.get(FILE_PATH)));
            }
        } catch (IOException e) {
            System.err.println("⚠️ Error leyendo archivo de vistos: " + e.getMessage());
        }
        return seen;
    }

    public static void appendSeenAd(String url) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            writer.write(url);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("⚠️ Error escribiendo archivo de vistos: " + e.getMessage());
        }
    }
}
