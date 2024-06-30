package syslog;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class Syslog {
  private static StringBuilder sb = new StringBuilder();

  private static void printOut(String message) {
    System.out.print(message + "\n");
  }
  public static void syslog(
      int facility, int level, String message) { // TODO message client for level 4 and below.
    syslog(String.valueOf(facility),level,message);
  }

  public static void syslog(
          String facility, int level, Object message) {
    syslog(facility, level, String.valueOf(message));
  }


  public static void syslog(String facility, int level, String message) {
    String line = String.format("[%s] Facility: %s, Level: %d, Message:\n%s\n",
            java.time.LocalTime.now(),
            facility, level, message);

    printOut(line);
    sb.append(line + "\n");
  }
  public static void writeToFile(String filename) {
    try {
      FileWriter myWriter = new FileWriter(filename);
      myWriter.write(sb.toString());
      myWriter.close();
    } catch (IOException e) {
      System.err.println("FUCK");
    }
  }
}
