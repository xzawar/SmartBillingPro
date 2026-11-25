package com.smartbillingpro.app;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Keeps an installed copy of Smart Billing Pro on the computer it was installed on.
 *
 * <p>The Windows installer runs {@code SmartBillingPro.exe --bind-device <token>} once, after
 * copying the files. The app then writes {@value #FILE_NAME} into its installation folder: hashes of
 * this computer's Windows machine GUID and hardware UUID, with a signature. On every start they are
 * read again; if the installed files were copied to another computer the app explains that and does
 * not start.</p>
 *
 * <p>Only installer builds are locked. They carry {@value #TOKEN_HASH_RESOURCE}, the SHA-256 of a
 * random token that exists only in that build's installer, so the files of an installed copy are not
 * enough to register another computer. Development runs and the test suite are never locked.</p>
 */
public final class DeviceLock {

    /** Result of checking this copy against this computer. */
    public enum State {
        /** A development or test run: no lock applies. */
        NOT_LOCKED,
        /** Installed on this computer. */
        OK,
        /** Installed files without a registration (e.g. copied without the installer). */
        NOT_REGISTERED,
        /** Registered to a different computer. */
        OTHER_DEVICE,
        /** The registration file was changed or is unreadable. */
        DAMAGED
    }

    /**
     * What identifies this computer: hashes of the Windows machine GUID and of the hardware UUID.
     * An empty value means it could not be read; the hardware part is then not compared.
     */
    record Device(String machine, String hardware) {

        static Device of(String machineId, String hardwareId) {
            return new Device(hash("SBP-MACHINE|", machineId), hash("SBP-HARDWARE|", hardwareId));
        }

        private static String hash(String prefix, String raw) {
            String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            return v.isEmpty() ? "" : sha256Hex(prefix + v);
        }

        /** Same computer: the machine GUID matches, and the hardware too whenever both were readable. */
        boolean sameComputerAs(Device other) {
            if (machine.isEmpty() || !equalsConstantTime(machine, other.machine)) {
                return false;
            }
            return hardware.isEmpty() || other.hardware.isEmpty() || equalsConstantTime(hardware, other.hardware);
        }
    }

    public static final String FILE_NAME = "device.lic";
    static final String TOKEN_HASH_RESOURCE = "/license/bind.sha256";
    private static final String SIGNING_PREFIX = "SmartBillingPro|KeyKraft|";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DeviceLock() {
    }

    // ------------------------------------------------------------ used by the app and the installer

    /** Is this copy allowed to run on this computer? */
    public static State check() {
        return check(tokenHash(), installDir(), DeviceLock::device);
    }

    /**
     * Registers this computer. Run by the installer with the token of its build.
     *
     * @return a process exit code: 0 registered (or nothing to do), 3 wrong token, 4 no installation
     *         folder, 5 the file could not be written
     */
    public static int bind(String token) {
        String hash = tokenHash();
        if (hash == null) {
            return 0;
        }
        if (!tokenMatches(token, hash)) {
            return 3;
        }
        Path dir = installDir();
        if (dir == null) {
            return 4;
        }
        try {
            write(dir, hash, device(), LocalDateTime.now().format(STAMP));
            return 0;
        } catch (IOException | RuntimeException e) {
            return 5;
        }
    }

    /** A short explanation for the owner when the app cannot start. */
    public static String explain(State state) {
        return switch (state) {
            case OTHER_DEVICE -> "This copy of Smart Billing Pro is registered to another computer, so it cannot "
                    + "run here. Smart Billing Pro only works on the computer it was installed on.\n\n"
                    + "If this is the same computer (for example after a repair), run the Smart Billing Pro "
                    + "installer again – your shop's data is kept. Otherwise please contact KeyKraft.";
            case NOT_REGISTERED -> "Smart Billing Pro has not been set up on this computer.\n\n"
                    + "Please install it with the Smart Billing Pro installer from KeyKraft.";
            case DAMAGED -> "This computer's registration for Smart Billing Pro has been changed or damaged.\n\n"
                    + "Please run the Smart Billing Pro installer again – your shop's data is kept.";
            default -> "";
        };
    }

    // ------------------------------------------------------------ testable core

    static State check(String tokenHash, Path dir, Supplier<Device> here) {
        if (tokenHash == null) {
            return State.NOT_LOCKED;
        }
        if (dir == null) {
            return State.NOT_REGISTERED;
        }
        Path file = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return State.NOT_REGISTERED;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            return State.DAMAGED;
        }
        Device registered = new Device(p.getProperty("machine", "").trim(), p.getProperty("hardware", "").trim());
        String installed = p.getProperty("installed", "").trim();
        String signature = p.getProperty("check", "").trim().toLowerCase(Locale.ROOT);
        if (registered.machine().isEmpty() || signature.isEmpty()
                || !equalsConstantTime(sign(tokenHash, registered, installed), signature)) {
            return State.DAMAGED;
        }
        return registered.sameComputerAs(here.get()) ? State.OK : State.OTHER_DEVICE;
    }

    static void write(Path dir, String tokenHash, Device device, String installed) throws IOException {
        if (device.machine().isEmpty()) {
            throw new IOException("This computer's machine ID could not be read");
        }
        Files.createDirectories(dir);
        Path tmp = dir.resolve(FILE_NAME + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            w.write("# Smart Billing Pro - registration of this computer (KeyKraft). Do not edit or copy.\n");
            w.write("machine=" + device.machine() + "\n");
            w.write("hardware=" + device.hardware() + "\n");
            w.write("installed=" + installed + "\n");
            w.write("check=" + sign(tokenHash, device, installed) + "\n");
        }
        Files.move(tmp, dir.resolve(FILE_NAME), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    static boolean tokenMatches(String token, String tokenHash) {
        if (token == null || token.isBlank() || tokenHash == null) {
            return false;
        }
        return equalsConstantTime(sha256Hex(token.trim()), tokenHash.trim().toLowerCase(Locale.ROOT));
    }

    static boolean equalsConstantTime(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    static String sign(String tokenHash, Device device, String installed) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((SIGNING_PREFIX + tokenHash.trim().toLowerCase(Locale.ROOT))
                    .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String message = device.machine() + "|" + device.hardware() + "|" + installed;
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** The SHA-256 of this build's installer token, or null for development builds (never locked). */
    static String tokenHash() {
        try (InputStream in = DeviceLock.class.getResourceAsStream(TOKEN_HASH_RESOURCE)) {
            if (in == null) {
                return null;
            }
            String h = new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim().toLowerCase(Locale.ROOT);
            return h.matches("[0-9a-f]{64}") ? h : null;
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ where and what

    /** The installation folder: the folder above {@code app\} that holds the application jar. */
    static Path installDir() {
        try {
            java.security.CodeSource src = DeviceLock.class.getProtectionDomain().getCodeSource();
            return src == null ? null : installDirFor(Path.of(src.getLocation().toURI()));
        } catch (Exception e) {
            return null;
        }
    }

    static Path installDirFor(Path codeLocation) {
        if (codeLocation == null || !Files.isRegularFile(codeLocation)) {
            return null;
        }
        Path dir = codeLocation.toAbsolutePath().getParent();
        if (dir != null && dir.getFileName() != null && dir.getFileName().toString().equalsIgnoreCase("app")
                && dir.getParent() != null) {
            return dir.getParent();
        }
        return dir;
    }

    /** Identifies this computer. It survives renaming the computer, but not moving the files to another one. */
    static Device device() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Device.of(registryValue("HKLM\\SOFTWARE\\Microsoft\\Cryptography", "MachineGuid"),
                    registryValue("HKLM\\SYSTEM\\HardwareConfig", "LastConfig"));
        }
        String machine = firstLine(Path.of("/etc/machine-id"));
        if (machine.isEmpty()) {
            machine = firstLine(Path.of("/var/lib/dbus/machine-id"));
        }
        return Device.of(machine, "");
    }

    private static String registryValue(String key, String name) {
        try {
            String root = System.getenv("SystemRoot");
            Path reg = root == null ? null : Path.of(root, "System32", "reg.exe");
            String exe = reg != null && Files.isRegularFile(reg) ? reg.toString() : "reg";
            Process p = new ProcessBuilder(exe, "query", key, "/v", name, "/reg:64").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), Charset.defaultCharset());
            p.waitFor(10, TimeUnit.SECONDS);
            return parseRegistryValue(out, name);
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    /** Reads {@code    Name    REG_SZ    value} from the output of {@code reg query}. */
    static String parseRegistryValue(String output, String name) {
        if (output == null) {
            return "";
        }
        for (String line : output.split("\\R")) {
            String[] parts = line.trim().split("\\s+", 3);
            if (parts.length == 3 && parts[0].equalsIgnoreCase(name) && parts[1].startsWith("REG_")) {
                return parts[2].trim();
            }
        }
        return "";
    }

    private static String firstLine(Path p) {
        try {
            return Files.isRegularFile(p) ? Files.readAllLines(p).stream().findFirst().orElse("").trim() : "";
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
