package my.stuff;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(NewsLoader.class);

    public static void main(String[] args) {
        try {
            new NewsLoader(NewsLoader.SOURCES.Best);

            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    LOGGER.error("Thread was interrupted.", e);
                }
            }
        } catch (Throwable e) {
            LOGGER.fatal(e);
        }
    }
}
