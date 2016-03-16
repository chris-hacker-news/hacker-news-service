package my.stuff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NewsLoader {
    private static final Logger LOGGER = LogManager.getLogger(NewsLoader.class);
    private static final int DAY_OFFSET = -7;

    public enum SOURCES {
        News("https://hacker-news.firebaseio.com/v0/newstories.json?print=pretty"),
        Best("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");

        private final String url;

        SOURCES(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }

    private final Map<String, NewsEntry> entries = new HashMap<>();
    private Thread jsonLoader;
    private SOURCES source;

    private class NewsEntry {
        public String id;
        public Date timestamp;
        public String topic;
        public String url;
        public int votes;
        public Date posted;
    }

    public NewsLoader(SOURCES source) {
        try {
            for (NewsEntry entry : new Gson().fromJson(
                    FileUtils.readFileToString(new File("news.json")), NewsEntry[].class)) {
                entries.put(entry.id, entry);
            }

            Date lastWeek = DateUtils.addDays(Date.from(Instant.now()), DAY_OFFSET);
            entries.values().stream()
                    .filter(x -> x.posted.after(lastWeek))
                    .collect(Collectors.toList())
                    .forEach(x -> entries.remove(x));
        } catch (Exception e) {
            LOGGER.error("Unable to load data.", e);

            FileUtils.deleteQuietly(new File("news.json"));
        }

        this.jsonLoader = new Thread(this::fetchLatestEntries);
        this.source = source;

        jsonLoader.start();

        LOGGER.info("Running...");
    }

    public Stream<NewsEntry> getEntries() {
        Date lastWeek = DateUtils.addDays(Date.from(Instant.now()), DAY_OFFSET);
        return entries.values()
                .stream()
                .filter(x -> x.posted.after(lastWeek))
                .sorted((a, b) -> b.posted.compareTo(a.posted));
    }

    private void fetchLatestEntries() {
        try {
            while (jsonLoader.isAlive()) {

                do {
                    try (InputStream in = new URL(source.getUrl()).openStream()) {
                        processLatestEntries(new Gson().fromJson(IOUtils.toString(in), Long[].class));
                    } catch (Exception e) {
                        LOGGER.error("Unable to load '" + source.getUrl() + "'.", e);
                        break;
                    }

                    try {
                        File src = new File("news.json.next"), dst = new File("news.json");

                        FileUtils.write(src, new GsonBuilder().setPrettyPrinting().create().toJson(
                                getEntries().collect(Collectors.toList())));
                        FileUtils.deleteQuietly(dst);
                        FileUtils.moveFile(src, dst);

                        FileUtils.write(
                                new File("website/news.js"),
                                "var newsArray = " + FileUtils.readFileToString(dst) + ";");

                    } catch (Exception e) {
                        LOGGER.error("Unable to save data.", e);
                        break;
                    }
                } while (false);

                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e1) {
                }
            }
        } catch (Throwable e) {
            LOGGER.fatal(e);
        }
    }

    private void processLatestEntries(Long[] ids) {
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i].toString();
            if (entries.containsKey(id)) {
                continue;
            }

            NewsEntry entry = loadNewsInfo(id);

            if (entry != null) {
                entries.put(id, entry);
            }
        }
    }

    private NewsEntry loadNewsInfo(String id) {
        NewsEntry entry = new NewsEntry();
        Map json;
        entry.timestamp = Date.from(Instant.now());
        entry.id = id;

        try (InputStream in = new URL("https://hacker-news.firebaseio.com/v0/item/" + entry.id + ".json?print=pretty").openStream()) {
            json = new Gson().fromJson(IOUtils.toString(in), Map.class);
            int score = (int) (double) json.get("score");

            entry.topic = (String) json.get("title");
            entry.votes = score;
            entry.posted = new Date(1000 * (long) (double) json.get("time"));
            entry.url = (String) json.get("url");
        } catch (Exception e) {
            LOGGER.error("Unable to load '" + source.getUrl() + "'.", e);
            return null;
        }

        LOGGER.info("News: '" + entry.topic + "'");

        return entry;
    }
}
