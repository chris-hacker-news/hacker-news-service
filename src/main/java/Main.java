
public class Main {
    public static void main(String[] args) throws InterruptedException {
        new NewsLoader(NewsLoader.SOURCES.News);

        while(true) {
            Thread.sleep(1000);
        }
    }
}
