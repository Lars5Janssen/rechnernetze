package syslog;

import java.time.*;
public final class Syslog {
  private static void printOut(String message) {
    System.out.println(message + "\n");
  }

  public static void syslog(
      int facility, int level, String message) { // TODO message client for level 4 and below.
    syslog(String.valueOf(facility), level, message);
  }

  public static void syslog(String facility, int level, String message) {
    printOut(String.format("[%s] Facility: %s, Level: %d, Message:\n%s", LocalDateTime.now(),facility, level, message));
  }
}
