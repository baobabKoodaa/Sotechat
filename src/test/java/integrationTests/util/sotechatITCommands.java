package integrationTests.util;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

/**
 * Selenium webdriverin käyttöä nopeuttavia funktioita hyväksymätestejä varten
 */
public final class sotechatITCommands {

    /**
     * Osoitteet, joista löytyvät asiakkaan ja ammitlaisen näkymä
     */
    public static final String CUSTOMERADDRESS = "http://localhost:8080";
    public static final String PROADDRESS = "http://localhost:8080/pro";

    /**
     * Täyttää käyttäjänimen ja aloitusviestin ja lähettää sen serverille
     *
     * @param wait Odottavaan webdriveriin kiinnitetty WebDriverWait
     */
    public static void waitAndFillInformation(WebDriverWait wait) {
        waitElementPresent(wait, By.id("username")).sendKeys("Testi");
        waitElementPresent(wait, By.id("startMessage")).sendKeys("Moikkamoi!");
        waitElementPresent(wait, By.tagName("button")).click();
    }

    /**
     * Odottaa kunnes käyttäjä näkee jonon
     *
     * @param wait Odottavaan webdriveriin kiinnitetty WebDriverWait
     * @return Jonotus elementin
     */
    public static WebElement waitQueueWindowsAppear(WebDriverWait wait) {
        return waitElementPresent(wait, By.id("userInQueue"));
    }

    /**
     * Odottaa kunnes chat-ikkuna näkyy
     *
     * @param wait Odottavaan webdriveriin kiinnitetty WebDriverWait
     * @return Viestinkirjoitus kentän elementin
     */
    public static WebElement waitChatWindowsAppear(WebDriverWait wait) {
        return waitElementPresent(wait, By.name("messageArea"));
    }

    /**
     * Lähettää viestin chatti ruutuunm kun ruutu havaitaan
     *
     * @param wait    Odottavaan webdriveriin kiinnitetty WebDriverWait
     * @param message viesti joka lähetetään chattiin
     */
    public static void sendMessageChatWindow(WebDriverWait wait, String message) {
        waitElementPresent(wait, By.name("messageArea")).sendKeys(message);
        waitElementPresent(wait, By.name("send")).submit();
    }

    /**
     * Kirjauttaa hoitajan sisään järjestelmään, kun login tulee näkyviin
     *
     * @param wait Odottavaan webdriveriin kiinnitetty WebDriverWait
     */
    public static void proLogin(WebDriverWait wait) {
        waitElementPresent(wait, By.name("username")).sendKeys("hoitaja");
        waitElementPresent(wait, By.name("password")).sendKeys("salasana");
        waitElementPresent(wait, By.name("login")).submit();
    }

    public static void proLogout(WebDriverWait wait) {
        waitElementClickable(wait, By.name("logout")).click();
    }

    public static void endConversationPro(WebDriverWait wait) {
        waitElementPresent(wait, By.name("endConversation")).click();
        waitElementPresent(wait, By.name("sure")).click();
        waitInVisibilityOfElement(wait, By.name("messageArea"));
    }

    /**
     * Odottaa kunnes jonosta voi nostaa seuraavan ja sitten nostaa
     *
     * @param wait Odottavaan webdriveriin kiinnitetty WebDriverWait
     */
    public static void waitAndPickFromQueue(WebDriverWait wait) {
        waitElementClickable(wait, By.name("next")).click();
    }

    /**
     * Odotetaan elementtiä kunnes sitä voi klikata
     *
     * @param wait Odottavaan webdriveriin kiinnitetty WebDriverWait
     * @param by   By ehto jonka avulla odotetaan clikattavaa elementtiä
     * @return Elementti kun klikattavissa
     */
    public static WebElement waitElementClickable(WebDriverWait wait, By by) {
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    /**
     * Odotetaan että hakuehdoilal löytyvä elementti ilmestyy sivulle
     *
     * @param wait Odottavaan webdriveriin kiinnitetty WebDriverWait
     * @param by   By ehto jonka avulla odotetaan määrättyä elementtiä
     * @return Elementti kun löytyy
     */
    public static WebElement waitElementPresent(WebDriverWait wait, By by) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    public static WebElement waitVisibilityOfElement(WebDriverWait wait, By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    public static boolean waitInVisibilityOfElement(WebDriverWait wait, By by) {
        return wait.until(ExpectedConditions.invisibilityOfElementLocated(by));
    }

    /**
     * @param wait         Odottavaan webdriveriin kiinnitetty WebDriverWait
     * @param textToAppear Teksti jonka oletetaan ilmestyvän sivulle
     * @return true kun teksti löytyy, muuten timeout
     */
    public static boolean waitForTextToAppear(WebDriverWait wait, String textToAppear) {
        By byXpath = By.xpath("//*[contains(text(),'" + textToAppear + "')]");
        wait.until(ExpectedConditions.presenceOfElementLocated(byXpath));
        return true;
    }



    public static int countTabs(WebDriverWait wait, WebDriver driver, By by) {
        return driver.findElements(by).size();
    }

    public static int tabsCountToBe(WebDriverWait wait, int count) {
        wait.until(ExpectedConditions.numberOfElementsToBe(By.name("chatTab"), count));
        return count;
    }

    public static void sendMessageLastChatWindow(WebDriverWait wait, WebDriver driver, String message) {
        List<WebElement> l = findAllElements(driver, By.name("messageArea"));
        List<WebElement> l1 = findAllElements(driver, By.name("send"));
        if (!l.isEmpty() && !l1.isEmpty()) {
            l.get(l.size()-1).sendKeys(message);
            l1.get(l1.size()-1).submit();
        }
    }

    public static void closeLastChatWindow(WebDriverWait wait, WebDriver driver) {
        List<WebElement> l = findAllElements(driver, By.name("endConversation"));
        if (!l.isEmpty()) {
            l.get(l.size()-1).click();
            waitElementPresent(wait, By.name("sure")).click();
            tabsCountToBe(wait, l.size()-1);
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.className("modal")));
        }
    }

    public static void closeFirstChatWindow(WebDriverWait wait, WebDriver driver) {
        List<WebElement> l = findAllElements(driver, By.name("endConversation"));
        if (!l.isEmpty()) {
            waitElementPresent(wait, By.name("endConversation"));
            waitElementClickable(wait, By.name("endConversation")).click();
           // l.get(0).click();
            waitElementPresent(wait, By.name("sure")).click();
            tabsCountToBe(wait, l.size()-1);
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.className("modal")));
        }
    }


    public static List<WebElement> findAllElements(WebDriver driver, By by) {
        return driver.findElements(by);
    }


}
