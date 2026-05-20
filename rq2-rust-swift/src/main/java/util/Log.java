package util;

import java.util.Calendar;

public class Log {

    private static String getTime() {
        return String.format("[%1$tH:%1$tM:%1$tS]", Calendar.getInstance());
    }

    public static void log(String text) {
        System.out.printf("[LOG]%1$s %2$s\n", getTime(), text);
    }

    public static void oLog(String text) {
        System.out.printf("[LOG]%1$s %2$s\r", getTime(), text);
    }

    public static void warn(String text) {
        System.out.printf("[WARN]%1$s %2$s\n", getTime(), text);
    }

    public static void error(String text) {
        System.out.printf("[ERROR]%1$s %2$s\n", getTime(), text);
    }

    public static void error(String text, Exception exception) {
        System.out.printf("[ERROR]%1$s %2$s\n", getTime(), text);
        exception.printStackTrace();
    }
}
