/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2011-2023 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2023 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.smoketest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests the Admin Password Gate functionality, when user enters the default 'admin' password.
 * Tests must be run in order since we are modifying, then resetting, the password used in all other tests.
 * Using TestInstance.Lifecycle.PER_CLASS because we need to retain the value of loginUsesAlternatePassword
 * between test methods.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AdminPasswordGateIT extends OpenNMSSeleniumIT {
    private static final Logger LOG = LoggerFactory.getLogger(AdminPasswordGateIT.class);

    private static final String ALTERNATE_ADMIN_PASSWORD = "Admin!admin1";

    private boolean loginUsesAlternatePassword = false;

    /**
     * Used to override the login in the AbstractOpenNMSSeleniumHelper.m_watcher JUnit Rule.
     */
    @Override
    public void doTestWatcherLogin() {
        LOG.debug("DEBUG in AdminPasswordGateIT.doTestWatcherLogin...");

        if (loginUsesAlternatePassword) {
            LOG.info("DEBUG in AdminPasswordGateIT.doTestWatcherLogin, loginUsesAlternatePassword is true");
            login(PASSWORD_GATE_USERNAME, ALTERNATE_ADMIN_PASSWORD, false, false);
        } else {
            LOG.info("DEBUG in AdminPasswordGateIT.doTestWatcherLogin, loginUsesAlternatePassword is false");
            login();
        }
    }

    @Before
    public void setUp() throws Exception {
        driver.get(getBaseUrlInternal() + "opennms/login.jsp");
    }

    /**
     * Test logging in as "admin/admin", then getting the Password Change gate.
     * Change the admin password to something else, confirm user reaches the password change
     * successful page.
     * Logout to be ready for next test.
     */
    @Test
    public void test_01_AdminPasswordGateChangePassword() {
        LOG.info("DEBUG in test_01_AdminPasswordGateChangePassword");

        // login with "admin/admin", do not skip past the password gate
        login(PASSWORD_GATE_USERNAME, PASSWORD_GATE_PASSWORD, false, false);

        LOG.info("DEBUG logged in as admin/admin");

        if (!driver.getCurrentUrl().contains("passwordGate.jsp")) {
           fail("Failed to get password gate page after 'admin/admin' login attempt.");
        }

        LOG.info("DEBUG on passwordGate page");

        // TODO: Login as "admin1" and get password complexity failure alert

        // Change the admin password
        enterText(By.name("oldpass"), PASSWORD_GATE_PASSWORD);
        enterText(By.name("pass1"), ALTERNATE_ADMIN_PASSWORD);
        enterText(By.name("pass2"), ALTERNATE_ADMIN_PASSWORD);
        clickElement(By.name("btn_change_password"));

        wait.until((WebDriver driver) -> {
            return driver.getCurrentUrl().contains("passwordGateAction");
        });

        final WebElement element = findElementByXpath("//h3[contains(@class, 'alert-success') and text()='Password successfully changed.']");
        assertNotNull(element);

        // password was changed, set this so that the TestWatcher rule uses this password in its login
        // before running the next test method below
        loginUsesAlternatePassword = true;

        //// logout so we can test login in next test
        //logout();
    }

    /**
     * Login with new password, verify password gate page not displayed.
     * Make Rest API call to reset password back to 'admin'.
     */
    @Test
    public void test_02_LoginWithNewPasswordAndReset() {
        LOG.info("DEBUG in test_02_LoginWithNewPasswordAndReset");

        // login with "admin/newPassword", should go directly to main page
        //LOG.info("DEBUG logging out");
        //logout();
        LOG.info("DEBUG logging in with ALTERNATE_ADMIN_PASSWORD");
        login(PASSWORD_GATE_USERNAME, ALTERNATE_ADMIN_PASSWORD, true, true);

        LOG.info("DEBUG logged in as admin/newPassword");

        assertTrue(driver.getCurrentUrl().contains("index.jsp"));

        LOG.info("DEBUG on main page");

        final WebElement contentMiddleEl = getDriver().findElement(By.id("index-contentmiddle"));
        assertNotNull(contentMiddleEl);
        LOG.info("DEBUG found index-contentmiddle");

        final WebElement element = findElementByXpath("//div[contains(@class, 'card-header')]//span[text()='Status Overview']");
        assertNotNull(element);
        LOG.info("DEBUG found Status Overview");

        LOG.info("DEBUG verified on main page");

        // Now reset password back to "admin" using Rest API
        LOG.info("DEBUG about to reset password to 'admin' via Rest API");

        final String url = getBaseUrlInternal() + "opennms/rest/users/admin";
        final String body = "password=admin&hashPassword=true";

        try {
            sendPut(url, body);
            LOG.info("DEBUG reset password to 'admin' via Rest API succeeded");
        } catch (Exception e) {
            fail("Failed to reset password to 'admin': " + e.getMessage());
        }

        LOG.info("DEBUG setting loginUsesAlternatePassword back to false");
        loginUsesAlternatePassword = false;
        //logout();
    }

    /**
     * Verify user can log in with default "admin" username/password.
     */
    @Test
    public void test_03_LoginWithDefaultPassword() {
        LOG.info("DEBUG in test_03_LoginWithDefaultPassword");

        // login with "admin/admin", should succeed but display passwordGate page, which is skipped
        //LOG.info("DEBUG logging out");
        //logout();
        LOG.info("DEBUG logging in with admin/admin");
        login(PASSWORD_GATE_USERNAME, PASSWORD_GATE_PASSWORD, true, true);

        LOG.info("DEBUG logged in");

        assertTrue(driver.getCurrentUrl().contains("index.jsp"));

        LOG.info("DEBUG login appears to be successful with redirect to main page");
    }
}
