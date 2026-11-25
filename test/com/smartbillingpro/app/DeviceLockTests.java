package com.smartbillingpro.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The device lock: an installed copy runs on its own computer only, and its registration can't be edited. */
public final class DeviceLockTests {

    private DeviceLockTests() {
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void testDevelopmentRunsAreNeverLocked() throws Exception {
        Path dir = Files.createTempDirectory("sbp-lock");
        eq(DeviceLock.State.NOT_LOCKED, DeviceLock.check(null, dir, () -> DeviceLock.Device.of("x", "y")),
                "no token in the build");
        eq(DeviceLock.State.NOT_LOCKED, DeviceLock.check(), "the test run itself is not locked");
    }

    static void testRegisteredComputerRunsAndOthersDoNot() throws Exception {
        String token = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
        String hash = DeviceLock.sha256Hex(token);
        Path dir = Files.createTempDirectory("sbp-lock");
        DeviceLock.Device laptop = DeviceLock.Device.of("4c4c4544-0042-3510-8051-b4c04f4a4c32",
                "{4C4C4544-0042-3510-8051-B4C04F4A4C32}");
        DeviceLock.Device other = DeviceLock.Device.of("9f2d1c77-1111-2222-3333-444455556666",
                "{00000000-1111-2222-3333-444455556666}");
        eq(laptop, DeviceLock.Device.of(" 4C4C4544-0042-3510-8051-B4C04F4A4C32 ", "{4c4c4544-0042-3510-8051-b4c04f4a4c32}"),
                "case and spaces do not matter");

        eq(DeviceLock.State.NOT_REGISTERED, DeviceLock.check(hash, dir, () -> laptop), "files copied without the installer");
        DeviceLock.write(dir, hash, laptop, "2026-11-25 20:30:00");
        eq(DeviceLock.State.OK, DeviceLock.check(hash, dir, () -> laptop), "runs on the computer it was installed on");
        eq(DeviceLock.State.OTHER_DEVICE, DeviceLock.check(hash, dir, () -> other), "copied to another computer");
        eq(DeviceLock.State.OTHER_DEVICE, DeviceLock.check(hash, dir,
                () -> DeviceLock.Device.of("9f2d1c77-1111-2222-3333-444455556666", "")), "another computer, hardware unreadable");
        eq(DeviceLock.State.OTHER_DEVICE, DeviceLock.check(hash, dir,
                () -> DeviceLock.Device.of("4c4c4544-0042-3510-8051-b4c04f4a4c32", "{00000000-1111-2222-3333-444455556666}")),
                "same Windows copied onto other hardware");
        eq(DeviceLock.State.OK, DeviceLock.check(hash, dir,
                () -> DeviceLock.Device.of("4c4c4544-0042-3510-8051-b4c04f4a4c32", "")),
                "same computer when the hardware ID cannot be read (e.g. a limited account)");
        eq(DeviceLock.State.DAMAGED, DeviceLock.check(DeviceLock.sha256Hex("another build"), dir, () -> laptop),
                "a registration from another build is not accepted");
        boolean refused = false;
        try {
            DeviceLock.write(dir, hash, DeviceLock.Device.of("", ""), "2026-11-25 20:30:00");
        } catch (java.io.IOException e) {
            refused = true;
        }
        check(refused, "never registers a computer whose machine ID could not be read");
    }

    static void testEditedRegistrationIsRejected() throws Exception {
        String hash = DeviceLock.sha256Hex("token-for-this-build");
        Path dir = Files.createTempDirectory("sbp-lock");
        DeviceLock.Device laptop = DeviceLock.Device.of("machine-a", "hw-a");
        DeviceLock.Device other = DeviceLock.Device.of("machine-b", "hw-b");
        DeviceLock.write(dir, hash, laptop, "2026-11-25 20:30:00");
        Path file = dir.resolve(DeviceLock.FILE_NAME);
        String original = Files.readString(file, StandardCharsets.UTF_8);
        // someone copies the files and puts the other computer's IDs in
        Files.writeString(file, original.replace(laptop.machine(), other.machine()).replace(laptop.hardware(), other.hardware()),
                StandardCharsets.UTF_8);
        eq(DeviceLock.State.DAMAGED, DeviceLock.check(hash, dir, () -> other), "edited computer IDs");
        Files.writeString(file, original.replace("hardware=" + laptop.hardware(), "hardware="), StandardCharsets.UTF_8);
        eq(DeviceLock.State.DAMAGED, DeviceLock.check(hash, dir, () -> other), "hardware ID removed to skip the check");
        Files.writeString(file, original.replace("2026-11-25", "2027-01-01"), StandardCharsets.UTF_8);
        eq(DeviceLock.State.DAMAGED, DeviceLock.check(hash, dir, () -> laptop), "edited date");
        Files.writeString(file, "garbage", StandardCharsets.UTF_8);
        eq(DeviceLock.State.DAMAGED, DeviceLock.check(hash, dir, () -> laptop), "unreadable file");
    }

    static void testOnlyTheInstallerTokenCanRegister() {
        String token = "0f9e8d7c6b5a49382716051423324150";
        String hash = DeviceLock.sha256Hex(token);
        check(DeviceLock.tokenMatches(token, hash), "the installer's token");
        check(DeviceLock.tokenMatches("  " + token + "\n", hash.toUpperCase()), "spacing and case");
        check(!DeviceLock.tokenMatches("guess", hash), "a wrong token");
        check(!DeviceLock.tokenMatches(null, hash), "no token");
        check(!DeviceLock.tokenMatches(hash, hash), "the stored hash itself is not the token");
        eq(0, DeviceLock.bind("anything"), "a development build has nothing to register");
    }

    static void testReadsWindowsRegistryOutputAndInstallFolder() throws Exception {
        String out = "\r\nHKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography\r\n"
                + "    MachineGuid    REG_SZ    5d1f3a2b-9c8e-4f70-a1b2-c3d4e5f60718\r\n\r\n";
        eq("5d1f3a2b-9c8e-4f70-a1b2-c3d4e5f60718", DeviceLock.parseRegistryValue(out, "MachineGuid"), "machine GUID");
        eq("", DeviceLock.parseRegistryValue("ERROR: The system was unable to find the specified registry key or value.",
                "MachineGuid"), "missing value");
        Path install = Files.createTempDirectory("sbp-install");
        Path jar = Files.createDirectories(install.resolve("app")).resolve("SmartBillingPro.jar");
        Files.writeString(jar, "jar");
        eq(install.toAbsolutePath(), DeviceLock.installDirFor(jar), "jar in app\\ -> installation folder");
        eq(null, DeviceLock.installDirFor(install), "class folders (development) have no installation folder");
        check(!DeviceLock.explain(DeviceLock.State.OTHER_DEVICE).isBlank(), "explains the lock to the owner");
    }
}
