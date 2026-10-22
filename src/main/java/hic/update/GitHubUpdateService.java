package hic.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

public class GitHubUpdateService {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String version;
    private final String repository;

    public GitHubUpdateService() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/hic-update.properties")) {
            if (input == null) {
                throw new IOException("Update configuration is missing.");
            }
            properties.load(input);
        }
        version = properties.getProperty("version", "");
        repository = properties.getProperty("repository", "");
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException("Invalid update repository.");
        }
    }

    public String version() {
        return version;
    }

    public Release check() throws IOException, InterruptedException {
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            return null;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/"
                        + repository + "/releases/latest"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "HIC-Studio/" + version)
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new IOException("No public release is available. Check the release repository and its visibility.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub update check returned HTTP " + response.statusCode() + ".");
        }
        return parseRelease(response.body(), version, repository,
                System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    static Release parseRelease(String json, String currentVersion, String repository,
                                String operatingSystem, String architecture) throws IOException {
        try {
            JsonObject release = JsonParser.parseString(json).getAsJsonObject();
            if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) {
                return null;
            }
            String latest = release.get("tag_name").getAsString().replaceFirst("^v", "");
            if (!isNewer(latest, currentVersion)) {
                return null;
            }
            String platform = platform(operatingSystem, architecture);
            if (platform == null) {
                throw new IOException("No automatic installer is available for this operating system or processor.");
            }
            String expected = "HIC-Studio-" + latest + "-" + platform;
            for (var element : release.getAsJsonArray("assets")) {
                JsonObject asset = element.getAsJsonObject();
                if (!expected.equals(asset.get("name").getAsString())) {
                    continue;
                }
                URI download = URI.create(asset.get("browser_download_url").getAsString());
                if (!"https".equals(download.getScheme()) || !"github.com".equals(download.getHost())
                        || !download.getPath().startsWith("/" + repository + "/releases/download/")) {
                    throw new IOException("Unexpected installer download address.");
                }
                String digest = asset.has("digest") && !asset.get("digest").isJsonNull()
                        ? asset.get("digest").getAsString() : "";
                if (!digest.matches("sha256:[a-fA-F0-9]{64}")) {
                    throw new IOException("The release installer has no SHA-256 digest. Re-upload it to GitHub Releases.");
                }
                String notes = release.has("body") && !release.get("body").isJsonNull()
                        ? release.get("body").getAsString() : "";
                return new Release(latest, notes, expected, download, digest.substring(7));
            }
            throw new IOException("The latest release has no installer named " + expected + ".");
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            throw new IOException("GitHub returned invalid release information.", exception);
        }
    }

    static boolean isNewer(String candidate, String current) {
        if (!candidate.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || !current.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            return false;
        }
        String[] candidateParts = candidate.split("\\.");
        String[] currentParts = current.split("\\.");
        for (int index = 0; index < candidateParts.length; index++) {
            int comparison = new java.math.BigInteger(candidateParts[index])
                    .compareTo(new java.math.BigInteger(currentParts[index]));
            if (comparison != 0) {
                return comparison > 0;
            }
        }
        return false;
    }

    static String platform(String operatingSystem, String architecture) {
        String normalized = operatingSystem.toLowerCase(Locale.ROOT);
        boolean arm = architecture.equalsIgnoreCase("aarch64") || architecture.equalsIgnoreCase("arm64");
        boolean intel = architecture.equalsIgnoreCase("amd64") || architecture.equalsIgnoreCase("x86_64");
        if (normalized.startsWith("windows") && intel) {
            return "windows-x64.msi";
        }
        if (normalized.startsWith("mac") && (arm || intel)) {
            return arm ? "macos-arm64.dmg" : "macos-x64.dmg";
        }
        return null;
    }

    public Path download(Release release) throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("hic-update-");
        Path installer = directory.resolve(release.fileName());
        boolean complete = false;
        try {
            HttpRequest request = HttpRequest.newBuilder(release.download())
                    .timeout(Duration.ofMinutes(15)).GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(installer));
            if (response.statusCode() != 200) {
                throw new IOException("Installer download returned HTTP " + response.statusCode() + ".");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(installer)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(release.sha256())) {
                throw new IOException("Installer verification failed. Please try the download again.");
            }
            complete = true;
            return installer;
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable.", exception);
        } finally {
            if (!complete) {
                Files.deleteIfExists(installer);
                Files.deleteIfExists(directory);
            }
        }
    }

    public record Release(String version, String notes, String fileName, URI download, String sha256) {
    }
}
