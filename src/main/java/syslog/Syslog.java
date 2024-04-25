package syslog;

public final class Syslog {
    public static void syslog(int facility, int level, String message) {
        System.out.printf("Facility: %d, Level: %d, Message: %s%n", facility, level, message);
    }
}
