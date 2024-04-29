package syslog;

public final class Syslog {
    public static void syslog(int facility, int level, String message) { // TODO message client for level 4 and below.
        System.out.printf("Facility: %d, Level: %d, Message: %s%n", facility, level, message);
    }
}
