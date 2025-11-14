package Assessment;


import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.*;
import org.openqa.selenium.support.ui.*;
import org.testng.annotations.*;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.NoSuchElementException;

public class TestCase
{
	 private WebDriver driver;
	    private static Set<String> savedImageUrls = new HashSet<>();

	    
	    @Parameters({"browser", "os", "isRemote"})
	    @BeforeTest
	    public void setUp(@org.testng.annotations.Optional("chrome") String browser,
	                      @org.testng.annotations.Optional("Windows 10") String os,
	                      @org.testng.annotations.Optional("false") String isRemote) throws Exception {

	        System.out.println("\n===============================================");
	        System.out.println("===== " + browser.toUpperCase() + " Execution Started =====");
	        System.out.println("===============================================\n");

	        if (isRemote.equalsIgnoreCase("true")) {
	            String username = System.getenv("BROWSERSTACK_USERNAME");
	            String accessKey = System.getenv("BROWSERSTACK_ACCESS_KEY");

	            if (username == null || accessKey == null) {
	                username = "YOUR_BROWSERSTACK_USERNAME";  
	                accessKey = "YOUR_BROWSERSTACK_ACCESS_KEY"; 
	            }

	            MutableCapabilities caps = new MutableCapabilities();
	            caps.setCapability("browserName", browser);
	            caps.setCapability("os", os);
	            caps.setCapability("project", "ElPais Scraper Automation");
	            caps.setCapability("build", "CrossBrowser-Sequential-Build");
	            caps.setCapability("name", "ElPais Article Validation");
	            caps.setCapability("browserstack.debug", "true");
	            caps.setCapability("browserstack.networkLogs", "true");

	            driver = new RemoteWebDriver(
	                    new URL("https://" + username + ":" + accessKey + "@hub-cloud.browserstack.com/wd/hub"),
	                    caps
	            );

	        } else {
	            switch (browser.toLowerCase()) {
	                case "chrome":
	                    driver = new ChromeDriver();
	                    break;
	                case "firefox":
	                    driver = new FirefoxDriver();
	                    break;
	                default:
	                    throw new IllegalArgumentException("Unsupported browser: " + browser);
	            }
	        }

	        driver.manage().window().maximize();
	    }

	    @Test
	    public void elPaisArticleScraper() {
	        try {
	            driver.get("https://elpais.com/");

	            // Accept cookies
	            try {
	                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(8));
	                WebElement acceptCookies = wait.until(ExpectedConditions.elementToBeClickable(
	                        By.cssSelector("button[data-testid='TcfAccept'], button.didomi-accept-button, button#didomi-notice-agree-button")));
	                acceptCookies.click();
	                System.out.println("Cookie consent accepted.");
	                Thread.sleep(2000);
	            } catch (Exception e) {
	                System.out.println("No cookie banner found or already dismissed.");
	            }

	            // Navigate to Opinion section
	            driver.get("https://elpais.com/opinion/");
	            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
	            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("article")));

	            List<WebElement> articles = driver.findElements(By.tagName("article"));
	            int count = Math.min(articles.size(), 5);

	            System.out.println("\n Found Articles: " + count);
	            System.out.println("--------------------------------------------------");

	            List<String> translatedTitles = new ArrayList<>();

	            for (int i = 0; i < count; i++) {
	                try {
	                    WebElement article = articles.get(i);

	                    String title;
	                    try {
	                        title = article.findElement(By.tagName("h2")).getText().trim();
	                    } catch (NoSuchElementException e) {
	                        title = "No title found";
	                    }

	                    try {
	                        article.findElement(By.tagName("a")).click();
	                    } catch (Exception e) {
	                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", article);
	                        Thread.sleep(1000);
	                        article.findElement(By.tagName("a")).click();
	                    }

	                    wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("article")));
	                    Thread.sleep(2000);

	                    String content;
	                    try {
	                        content = driver.findElement(By.tagName("article")).getText();
	                    } catch (Exception e) {
	                        content = "Could not extract content.";
	                    }

	                    // Scroll and save image if exists
	                    try {
	                        WebElement img = driver.findElement(By.cssSelector("article img"));
	                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", img);
	                        Thread.sleep(2000);

	                        String imgUrl = img.getAttribute("src");
	                        if (imgUrl == null || imgUrl.isEmpty() || imgUrl.startsWith("data:")) {
	                            imgUrl = img.getAttribute("data-src");
	                        }
	                        if (imgUrl == null || imgUrl.isEmpty()) {
	                            imgUrl = img.getAttribute("srcset");
	                            if (imgUrl != null && imgUrl.contains(" ")) {
	                                imgUrl = imgUrl.split(" ")[0];
	                            }
	                        }

	                        if (imgUrl != null && imgUrl.startsWith("http")) {
	                            if (!savedImageUrls.contains(imgUrl)) {
	                                savedImageUrls.add(imgUrl);
	                                String hash = Integer.toHexString(imgUrl.hashCode());
	                                String safeName = title.replaceAll("[^a-zA-Z0-9]", "_") + "_" + hash + ".jpg";
	                                saveImageToDownloads(imgUrl, safeName);
	                                System.out.println("Image saved for: " + title);
	                            } else {
	                                System.out.println("Skipping duplicate image (Its already downloaded) for: " + title);
	                            }
	                        } else {
	                            System.out.println("No valid image found for: " + title);
	                        }

	                    } catch (NoSuchElementException e) {
	                        System.out.println("No image found for this article: " + title);
	                    }

	                    // Print article info
	                    System.out.println("Title in Spanish: " + title);
	                    System.out.println("Content");
	                    System.out.println(content.substring(0, Math.min(content.length(), 300)) + "...");

	                    String translated = translateText(title);
	                    String decodedTitle = URLDecoder.decode(translated, StandardCharsets.UTF_8);
	                    translatedTitles.add(decodedTitle);
						System.out.println();
	                    System.out.println("Translated Title to English: " + decodedTitle);
	                    System.out.println("--------------------------------------------------");

	                    driver.navigate().back();
	                    wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("article")));
	                    Thread.sleep(2000);
	                    articles = driver.findElements(By.tagName("article"));

	                } catch (Exception e) {
	                    System.out.println("Error processing article " + (i + 1) + ": " + e.getMessage());
	                }
	            }

	            analyzeRepeatedWords(translatedTitles);

	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    // Download Images
	    public static void saveImageToDownloads(String imageUrl, String fileName) {
	        try {
	            String userHome = System.getProperty("user.home");
	            Path downloadsDir = Paths.get(userHome, "Downloads");
	            Path filePath = downloadsDir.resolve(fileName);

	            try (InputStream in = new URL(imageUrl).openStream();
	                 OutputStream out = new FileOutputStream(filePath.toFile())) {
	                byte[] buffer = new byte[4096];
	                int n;
	                while ((n = in.read(buffer)) != -1) {
	                    out.write(buffer, 0, n);
	                }
	            }
	            System.out.println("Saved image to: " + filePath);
	        } catch (Exception e) {
	            System.out.println("Could not save image: " + e.getMessage());
	        }
	    }

	    // Translation to english
	    public static String translateText(String text) {
	        try {
	            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=es&tl=en&dt=t&q=" +
	                    URLEncoder.encode(text, StandardCharsets.UTF_8);
	            Response response = RestAssured.get(url);
	            String jsonResponse = response.getBody().asString();

	            Object obj = new JSONParser().parse(jsonResponse);
	            JSONArray arr = (JSONArray) obj;
	            JSONArray transArray = (JSONArray) arr.get(0);
	            JSONArray first = (JSONArray) transArray.get(0);
	            return first.get(0).toString();
	        } catch (Exception e) {
	            return "[Translation failed: " + e.getMessage() + "]";
	        }
	    }

	    // Word frequency
	    public static void analyzeRepeatedWords(List<String> titles) {
	        Map<String, Integer> wordCount = new HashMap<>();
	        List<String> stopwords = Arrays.asList("the", "and", "for", "but", "with", "are", "you", "this",
	                "that", "from", "was", "not", "has", "have", "a", "an", "to", "of", "in", "on", "it");

	        for (String t : titles) {
	            for (String word : t.toLowerCase().replaceAll("[^a-z]", " ").split("\\s+")) {
	                if (word.length() > 2 && !stopwords.contains(word)) {
	                    wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
	                }
	            }
	        }

	        System.out.println("\n Repeated words (more than 2 occurrences):");
	        boolean found = false;
	        for (Map.Entry<String, Integer> entry : wordCount.entrySet()) {
	            if (entry.getValue() >= 2) {
	                System.out.println(entry.getKey() + " -> " + entry.getValue());
	                found = true;
	            }
	        }
	        if (!found) System.out.println("None found.");
	    }

	    
	    @AfterTest
	    public void tearDown() {
	        if (driver != null) driver.quit();
	        System.out.println("\n===============================================");
	        System.out.println("===== Execution Completed =====");
	        System.out.println("===============================================\n");
	    }
	

}
