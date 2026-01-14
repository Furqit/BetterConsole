package dev.furq.betterconsole.mixin;

import com.hypixel.hytale.logger.backend.HytaleConsole;
import com.hypixel.hytale.logger.backend.HytaleLoggerBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(HytaleConsole.class)
public abstract class HytaleConsoleMixin extends Thread {

    @Unique
    private static final DateTimeFormatter BETTER_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    @Unique
    private static final Pattern RAW_DATE_PATTERN = Pattern.compile("^\\[\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\\s+(\\w+)]\\s+(.*)", Pattern.DOTALL);

    @Unique private static final String COLOR_GRAY = "\u001b[90m";
    @Unique private static final String COLOR_RESET = "\u001b[m";
    @Unique private static final String COLOR_CYAN = "\u001b[36m";
    @Unique private static final String COLOR_GREEN = "\u001b[32m";
    @Unique private static final String COLOR_MAGENTA = "\u001b[35m";
    @Unique private static final String COLOR_BLUE = "\u001b[34m";
    @Unique private static final String COLOR_WHITE = "\u001b[97m";
    @Unique private static final String COLOR_BOLD = "\u001b[1m";

    @Unique private static boolean betterConsole$useColors = true;
    @Unique private static boolean betterConsole$useSuppressions = true;
    @Unique private static boolean betterConsole$compactTimestamps = true;
    @Unique private static boolean betterConsole$prettifyPartyInfo = true;
    @Unique private static boolean betterConsole$inlineValidationLogs = true;
    @Unique private static final List<String> betterConsole$suppressions = new ArrayList<>();
    @Unique private static long betterConsole$lastLoaded = 0;

    @Shadow @Nullable private OutputStreamWriter soutwriter;
    @Shadow @Nullable private OutputStreamWriter serrwriter;

    /**
     * @author Furq
     * @reason Overwrite publish0 to enforce compact formatting and silence noise.
     */
    @Overwrite
    private void publish0(@Nonnull LogRecord record) {
        betterConsole$reloadConfigIfNeeded();

        if (betterConsole$useSuppressions && betterConsole$shouldSuppress(record))
            return;

        String msg;
        try {
            msg = betterConsole$format(record);
        } catch (Exception ex) {
            if (this.serrwriter != null) {
                ex.printStackTrace(new PrintWriter(this.serrwriter));
            } else {
                ex.printStackTrace(System.err);
            }
            return;
        }

        try {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                if (this.serrwriter != null) {
                    this.serrwriter.write(msg);
                    try {
                        this.serrwriter.flush();
                    } catch (Exception _) {
                    }
                } else {
                    HytaleLoggerBackend.REAL_SERR.print(msg);
                }
            } else if (this.soutwriter != null) {
                this.soutwriter.write(msg);
                try {
                    this.soutwriter.flush();
                } catch (Exception _) {
                }
            } else {
                HytaleLoggerBackend.REAL_SOUT.print(msg);
            }
        } catch (Exception _) {
        }
    }

    @Unique
    private boolean betterConsole$shouldSuppress(LogRecord record) {
        String msg = record.getMessage();
        if (msg == null) return false;

        for (String pattern : betterConsole$suppressions) {
            if (msg.contains(pattern) || msg.matches(".*" + pattern + ".*")) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private String betterConsole$format(LogRecord record) {
        String message = record.getMessage();
        if (message == null) message = "null";

        boolean ansiHardware = shouldPrintAnsi();
        String levelName = record.getLevel().equals(Level.WARNING) ? "WARN" : record.getLevel().getName();
        String loggerName = record.getLoggerName();
        if (loggerName == null) loggerName = "unknown";

        if (record.getClass().getName().contains("RawLogRecord")) {
            Matcher matcher = RAW_DATE_PATTERN.matcher(message);
            if (matcher.find()) {
                levelName = matcher.group(1);
                message = matcher.group(2);
                if (levelName.length() > 6)
                    levelName = levelName.substring(0, 4);
            } else if (record.getLoggerName() == null && betterConsole$compactTimestamps) {
                return (ansiHardware ? message : betterConsole$stripAnsi(message)) + "\n";
            }
        }

        if (betterConsole$prettifyPartyInfo && message.contains("PartyInfo{")) {
            message = message.replace("PartyInfo{", "PartyInfo{\n    ").replace(", ", ",\n    ").replace("}", "\n}");
                }
        if (betterConsole$inlineValidationLogs && (message.contains("Failed to validate asset!") || message.contains("Validation Results:"))) {
            message = message.replaceAll("\n\\s*", " ");
        }

        if (record.getParameters() != null && record.getParameters().length > 0) {
            try { message = String.format(message, record.getParameters()); } catch (RuntimeException ignored) {}
        }
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(sw));
            message = message + "\n" + sw;
        }

        StringBuilder sb = new StringBuilder();
        if (ansiHardware) {
            String levelColor = betterConsole$getLevelColor(record.getLevel().intValue());

            if (betterConsole$useColors) {
                String categoryColor = betterConsole$getCategoryColor(loggerName);
                String metaColor = COLOR_GRAY;

                sb.append(metaColor).append('[');
                betterConsole$appendTime(record, sb);
                sb.append(" | ").append(COLOR_BOLD).append(levelColor).append(levelName).append(COLOR_RESET).append(metaColor).append("] ");

                sb.append(metaColor).append('[').append(COLOR_RESET).append(categoryColor).append(loggerName).append(COLOR_RESET).append(metaColor).append("] ");

                sb.append(COLOR_WHITE).append(message).append(COLOR_RESET).append('\n');
            } else {
                sb.append(levelColor);
                sb.append('[');
                betterConsole$appendTime(record, sb);
                sb.append(" | ").append(levelName).append("] ");
                sb.append('[').append(loggerName).append("] ");
                sb.append(message).append(COLOR_RESET).append('\n');
            }
        } else {
            sb.append('[');
            betterConsole$appendTime(record, sb);
            sb.append(" | ").append(levelName).append("] ");
            sb.append('[').append(loggerName).append("] ");
            sb.append(betterConsole$stripAnsi(message)).append('\n');
        }

        return sb.toString();
    }

    @Unique
    private void betterConsole$appendTime(LogRecord record, StringBuilder sb) {
        if (betterConsole$compactTimestamps) {
            BETTER_TIME_FORMATTER.formatTo(LocalDateTime.ofInstant(record.getInstant(), ZoneOffset.UTC), sb);
        } else {
            sb.append(record.getInstant());
        }
    }

    @Unique
    private String betterConsole$getLevelColor(int level) {
        if (level <= Level.ALL.intValue()) return COLOR_WHITE;
        if (level <= Level.FINEST.intValue()) return "\u001b[36m";
        if (level <= Level.FINER.intValue()) return "\u001b[34m";
        if (level <= Level.FINE.intValue()) return "\u001b[35m";
        if (level <= Level.CONFIG.intValue()) return "\u001b[32m";
        if (level <= Level.INFO.intValue()) return COLOR_WHITE;
        if (level <= Level.WARNING.intValue()) return "\u001b[33m";
        if (level >= Level.SEVERE.intValue()) return "\u001b[31m";
        return COLOR_WHITE;
    }

    @Unique
    private String betterConsole$getCategoryColor(String loggerName) {
        if (loggerName.contains("Server") || loggerName.contains("Hytale") || loggerName.contains("Universe"))
            return COLOR_CYAN;
        if (loggerName.contains("Plugin") || loggerName.contains("Mod") || loggerName.contains("|P"))
            return COLOR_GREEN;
        if (loggerName.contains("Asset") || loggerName.contains("Generator") || loggerName.contains("Registry"))
            return COLOR_MAGENTA;
        if (loggerName.contains("Auth") || loggerName.contains("JWT") || loggerName.contains("Session"))
            return COLOR_BLUE;
        return COLOR_WHITE;
    }

    @Unique
    private String betterConsole$stripAnsi(String message) {
        return message == null ? null : message.replaceAll("\u001b\\[[;\\d]*m", "");
    }

    @Shadow private boolean shouldPrintAnsi() { return false; }

    @Unique
    private void betterConsole$reloadConfigIfNeeded() {
        if (System.currentTimeMillis() - betterConsole$lastLoaded > 15000) {
            betterConsole$loadConfig();
        }
    }

    @Unique
    private static synchronized void betterConsole$loadConfig() {
        try {
            Path configDir = Paths.get("config", "betterconsole");
            if (!Files.exists(configDir)) Files.createDirectories(configDir);

            Path settingsFile = configDir.resolve("settings.properties");
            Properties props = new Properties();
            if (Files.exists(settingsFile)) {
                try (InputStream in = Files.newInputStream(settingsFile)) {
                    props.load(in);
                }
            } else {
                props.setProperty("useColors", "true");
                props.setProperty("useSuppressions", "true");
                props.setProperty("compactTimestamps", "true");
                props.setProperty("prettifyPartyInfo", "true");
                props.setProperty("inlineValidationLogs", "true");
                try (OutputStream out = Files.newOutputStream(settingsFile)) {
                    props.store(out, "BetterConsole Settings");
                }
            }

            betterConsole$useColors = Boolean.parseBoolean(props.getProperty("useColors", "true"));
            betterConsole$useSuppressions = Boolean.parseBoolean(props.getProperty("useSuppressions", "true"));
            betterConsole$compactTimestamps = Boolean.parseBoolean(props.getProperty("compactTimestamps", "true"));
            betterConsole$prettifyPartyInfo = Boolean.parseBoolean(props.getProperty("prettifyPartyInfo", "true"));
            betterConsole$inlineValidationLogs = Boolean.parseBoolean(props.getProperty("inlineValidationLogs", "true"));

            Path suppressionsFile = configDir.resolve("suppressions.txt");
            betterConsole$suppressions.clear();
            if (Files.exists(suppressionsFile)) {
                try (BufferedReader reader = Files.newBufferedReader(suppressionsFile)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) betterConsole$suppressions.add(line);
                    }
                }
            } else {
                String[] defaults = {"Asset key.*has incorrect format", "Unused key", "Missing interaction",
                        "Duplicate export name", "Exported Scanner asset", "does not exist for model"};
                try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(suppressionsFile))) {
                    writer.println("# Add patterns to suppress here (one per line)");
                    for (String s : defaults) {
                        writer.println(s);
                        betterConsole$suppressions.add(s);
                    }
                }
            }
            betterConsole$lastLoaded = System.currentTimeMillis();
        } catch (Exception ignored) {}
    }
}
