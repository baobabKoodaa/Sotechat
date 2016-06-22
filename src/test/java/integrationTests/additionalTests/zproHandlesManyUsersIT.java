package integrationTests.additionalTests;

import com.github.webdriverextensions.junitrunner.WebDriverRunner;
import com.github.webdriverextensions.junitrunner.annotations.Chrome;
import integrationTests.util.DriverHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;


import java.util.ArrayList;
import java.util.List;
import static integrationTests.util.sotechatITCommands.*;
import static org.junit.Assert.*;

/**
 * As a user I want to send a message
 */
@RunWith(WebDriverRunner.class)
@Chrome
public class zproHandlesManyUsersIT {

    private static final int MAX_CUSTOMERS = 10;
    private DriverHandler handler;
    private WebDriverWait proWait;
    private List<WebDriver> customers;

    @Before
    public void setUp() throws Exception {
        handler = new DriverHandler("pro");
        handler.HttpGet("pro", PROADDRESS);

        /** Load max webdrivers representing customers. */
        customers = new ArrayList<>(MAX_CUSTOMERS);
        for (int i = 0; i < MAX_CUSTOMERS; i++) {
            String username = "user" + i;
            customers.add(handler.addDriver(username));
        }
    }


    @After
    public void tearDown() throws Exception {
        handler.closeAll();
    }

    /**
     * Test disabled while waiting for AngularJS changes.
     */
    @Test
    public void KillerTest() {

        /** All customers join the queue. */
        for (WebDriver customer : customers) {
            customer.get(CUSTOMERADDRESS);
            WebDriverWait wait = new WebDriverWait(customer, 4);
            waitAndFillInformation(wait);
        }

        /** Pro logs in and for each customer: polls from queue, sends msg. */
        proWait = handler.getWaitDriver("pro");
        proLogin(proWait);
        for (int i = 0; i < MAX_CUSTOMERS; i++) {
            waitAndPickFromQueue(proWait);
            assertEquals(1,tabsCountToBe(proWait, i+1));
        }

        /** Customers send many messages. */
        for (WebDriver customer : customers) {
            customer.get(CUSTOMERADDRESS);
            WebDriverWait wait = new WebDriverWait(customer, 4);
            for (int i = 0; i < MAX_CUSTOMERS; i++) {
                sendMessageChatWindow(wait, "Oletko okei");
            }
        }

    }


}

