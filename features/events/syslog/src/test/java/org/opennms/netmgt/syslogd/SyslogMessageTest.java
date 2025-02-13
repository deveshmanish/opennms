/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2010-2023 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.syslogd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opennms.core.test.ConfigurationTestUtils;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.time.YearGuesser;
import org.opennms.netmgt.config.SyslogdConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyslogMessageTest {
    private static final Logger LOG = LoggerFactory.getLogger(SyslogMessageTest.class);

    private final SyslogdConfigFactory m_config;

    public SyslogMessageTest() throws Exception {
        InputStream stream = null;
        try {
            stream = ConfigurationTestUtils.getInputStreamForResource(this, "/etc/syslogd-configuration.xml");
            m_config = new SyslogdConfigFactory(stream);
        } finally {
            if (stream != null) {
                IOUtils.closeQuietly(stream);
            }
        }
    }
    
    @Before
    public void setUp() {
        MockLogAppender.setupLogging(true, "TRACE");
    }

    @Test
    public void testCustomParserWithProcess() throws Exception {
        final SyslogParser parser = new CustomSyslogParser(m_config, SyslogdTestUtils.toByteBuffer("<6>test: 2007-01-01 127.0.0.1 OpenNMS[1234]: A SyslogNG style message"));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();

        assertEquals(SyslogFacility.KERNEL, message.getFacility());
        assertEquals(SyslogSeverity.INFORMATIONAL, message.getSeverity());
        assertEquals("test", message.getMessageID());
        assertEquals("127.0.0.1", message.getHostName());
        assertEquals("OpenNMS", message.getProcessName());
        assertEquals("1234", message.getProcessId());
        assertEquals("A SyslogNG style message", message.getMessage());
    }

    @Test
    public void testCustomParserWithSimpleForwardingRegexAndSyslog21Message() throws Exception {
        // see: http://searchdatacenter.techtarget.com/tip/Turn-aggregated-syslog-messages-into-OpenNMS-events

        final InputStream stream = new ByteArrayInputStream(("<syslogd-configuration>" +
                                                        "<configuration " +
                                                        "syslog-port=\"10514\" " +
                                                        "new-suspect-on-message=\"false\" " +
                                                        "forwarding-regexp=\"^((.+?) (.*))\\r?\\n?$\" " +
                                                        "matching-group-host=\"2\" " +
                                                        "matching-group-message=\"3\" " +
                                                        "discard-uei=\"DISCARD-MATCHING-MESSAGES\" " +
                                                        "/></syslogd-configuration>").getBytes());
        final SyslogdConfigFactory config = new SyslogdConfigFactory(stream);

        final SyslogParser parser = new CustomSyslogParser(config, SyslogdTestUtils.toByteBuffer("<173>Dec  7 12:02:06 10.13.110.116 mgmtd[8326]: [mgmtd.NOTICE]: Configuration saved to database initial"));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();

        LocalDateTime localeDateTime = LocalDateTime.of(0, Month.DECEMBER, 7, 12, 2, 6);
        localeDateTime = YearGuesser.guessYearForDate(localeDateTime);
        Date date = Date.from(localeDateTime.atZone(ZoneId.systemDefault()).toInstant());

        LOG.debug("got message: {}", message);

        assertEquals(SyslogFacility.LOCAL5, message.getFacility());
        assertEquals(SyslogSeverity.NOTICE, message.getSeverity());
        assertNull(message.getMessageID());
        assertEquals(date, message.getDate());
        assertEquals("10.13.110.116", message.getHostName());
        assertEquals("mgmtd", message.getProcessName());
        assertEquals("8326", message.getProcessId());
        assertEquals("[mgmtd.NOTICE]: Configuration saved to database initial", message.getMessage());
    }
    
    @Test
    public void testCustomParserNms5242() throws Exception {
        final Locale startLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            final InputStream stream = new ByteArrayInputStream(
               (
                   "<?xml version=\"1.0\"?>\n" +
                   "<syslogd-configuration>\n" +
                   "    <configuration\n" +
                   "            syslog-port=\"10514\"\n" +
                   "            new-suspect-on-message=\"false\"\n" +
                   "            parser=\"org.opennms.netmgt.syslogd.CustomSyslogParser\"\n" +
                   "            forwarding-regexp=\"^((.+?) (.*))\\n?$\"\n" +
                   "            matching-group-host=\"2\"\n" +
                   "            matching-group-message=\"3\"\n" +
                   "            discard-uei=\"DISCARD-MATCHING-MESSAGES\"\n" +
                   "            />\n" +
                   "\n" +
                   "    <hideMessage>\n" +
                   "        <hideMatch>\n" +
                   "            <match type=\"substr\" expression=\"TEST\"/>\n" +
                   "        </hideMatch>\n" +
                   "    </hideMessage>\n" +
                   "</syslogd-configuration>\n"
                ).getBytes()
            );
            final SyslogdConfigFactory config = new SyslogdConfigFactory(stream);

            final SyslogParser parser = new CustomSyslogParser(config, SyslogdTestUtils.toByteBuffer("<0>Mar 14 17:10:25 petrus sudo:  cyrille : user NOT in sudoers ; TTY=pts/2 ; PWD=/home/cyrille ; USER=root ; COMMAND=/usr/bin/vi /etc/aliases"));
            assertTrue(parser.find());
            final SyslogMessage message = parser.parse();
            LOG.debug("message = {}", message);
            final Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, getExpectedYear("Mar 14 17:10:25"));
            cal.set(Calendar.MONTH, Calendar.MARCH);
            cal.set(Calendar.DAY_OF_MONTH, 14);
            cal.set(Calendar.HOUR_OF_DAY, 17);
            cal.set(Calendar.MINUTE, 10);
            cal.set(Calendar.SECOND, 25);
            cal.set(Calendar.MILLISECOND, 0);
            assertEquals(SyslogFacility.KERNEL, message.getFacility());
            assertEquals(SyslogSeverity.EMERGENCY, message.getSeverity());
            assertNull(message.getMessageID());
            assertEquals(cal.getTime(), message.getDate());
            assertEquals("petrus", message.getHostName());
            assertEquals("sudo", message.getProcessName());
            assertEquals(null, message.getProcessId());
            assertEquals("cyrille : user NOT in sudoers ; TTY=pts/2 ; PWD=/home/cyrille ; USER=root ; COMMAND=/usr/bin/vi /etc/aliases", message.getMessage());
        } finally {
            Locale.setDefault(startLocale);
        }
    }
    
    @Test
    public void testSyslogNGParserWithProcess() throws Exception {
        final SyslogParser parser = new SyslogNGParser(m_config, SyslogdTestUtils.toByteBuffer("<6>test: 2007-01-01 127.0.0.1 OpenNMS[1234]: A SyslogNG style message"));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        final Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.YEAR, 2007);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DATE, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.clear(Calendar.MINUTE);
        calendar.clear(Calendar.SECOND);
        calendar.clear(Calendar.MILLISECOND);
        final Date date = calendar.getTime();

        assertEquals(SyslogFacility.KERNEL, message.getFacility());
        assertEquals(SyslogSeverity.INFORMATIONAL, message.getSeverity());
        assertEquals("test", message.getMessageID());
        assertEquals(date, message.getDate());
        assertEquals("127.0.0.1", message.getHostName());
        assertEquals("OpenNMS", message.getProcessName());
        assertEquals("1234", message.getProcessId());
        assertEquals("A SyslogNG style message", message.getMessage());
    }

    @Test
    public void testSyslogNGParserWithoutProcess() throws Exception {
        final SyslogParser parser = new SyslogNGParser(m_config, SyslogdTestUtils.toByteBuffer("<6>test: 2007-01-01 127.0.0.1 A SyslogNG style message"));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        final Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.YEAR, 2007);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DATE, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.clear(Calendar.MINUTE);
        calendar.clear(Calendar.SECOND);
        calendar.clear(Calendar.MILLISECOND);
        final Date date = calendar.getTime();

        assertEquals(SyslogFacility.KERNEL, message.getFacility());
        assertEquals(SyslogSeverity.INFORMATIONAL, message.getSeverity());
        assertEquals("test", message.getMessageID());
        assertEquals(date, message.getDate());
        assertEquals("127.0.0.1", message.getHostName());
        assertEquals(null, message.getProcessName());
        assertEquals(null, message.getProcessId());
        assertEquals("A SyslogNG style message", message.getMessage());
    }

    @Test
    public void testSyslogNGParserWithSyslog21Message() throws Exception {
        final SyslogParser parser = new SyslogNGParser(m_config, SyslogdTestUtils.toByteBuffer("<173>Dec  7 12:02:06 10.13.110.116 mgmtd[8326]: [mgmtd.NOTICE]: Configuration saved to database initial"));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();

        LocalDateTime localeDateTime = LocalDateTime.of(0, Month.DECEMBER, 7, 12, 2, 6);
        localeDateTime = YearGuesser.guessYearForDate(localeDateTime);
        Date date = Date.from(localeDateTime.atZone(ZoneId.systemDefault()).toInstant());

        assertEquals(SyslogFacility.LOCAL5, message.getFacility());
        assertEquals(SyslogSeverity.NOTICE, message.getSeverity());
        assertEquals(null, message.getMessageID());
        assertEquals(date, message.getDate());
        assertEquals("10.13.110.116", message.getHostName());
        assertEquals("mgmtd", message.getProcessName());
        assertEquals("8326", message.getProcessId());
        assertEquals("[mgmtd.NOTICE]: Configuration saved to database initial", message.getMessage());
    }

    @Test
    public void testRfc5424ParserExample1() throws Exception {
        final SyslogParser parser = new Rfc5424SyslogParser(m_config, SyslogdTestUtils.toByteBuffer("<34>1 2003-10-11T22:14:15.000Z mymachine.example.com su - ID47 - 'su root' failed for lonvick on /dev/pts/8"));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        final Date date = new Date(1065910455000L);

        assertEquals(1, message.getVersion().intValue());
        assertEquals(SyslogFacility.AUTH, message.getFacility());
        assertEquals(SyslogSeverity.CRITICAL, message.getSeverity());
        assertEquals(date, message.getDate());
        assertEquals("mymachine.example.com", message.getHostName());
        assertEquals("su", message.getProcessName());
        assertEquals("ID47", message.getMessageID());
        assertEquals("'su root' failed for lonvick on /dev/pts/8", message.getMessage());
    }

    @Test
    @Ignore("We cannot handle byte order mark (BOM) characters yet because we always decode the message buffer as US_ASCII in SyslogParser.fromByteBuffer()")
    public void testRfc5424ParserExampleWithByteOrderMark() throws Exception {
        final SyslogParser parser = new Rfc5424SyslogParser(m_config, SyslogdTestUtils.toByteBuffer("<34>1 2003-10-11T22:14:15.000Z mymachine.example.com su - ID47 - \uFEFF'su root' failed for lonvick on /dev/pts/8", StandardCharsets.UTF_16));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        final Date date = new Date(1065910455000L);

        assertEquals(1, message.getVersion().intValue());
        assertEquals(SyslogFacility.AUTH, message.getFacility());
        assertEquals(SyslogSeverity.CRITICAL, message.getSeverity());
        assertEquals(date, message.getDate());
        assertEquals("mymachine.example.com", message.getHostName());
        assertEquals("su", message.getProcessName());
        assertEquals("ID47", message.getMessageID());
        assertEquals("'su root' failed for lonvick on /dev/pts/8", message.getMessage());
    }

    @Test
    public void testRfc5424ParserExample2() throws Exception {
        final SyslogParser parser = new Rfc5424SyslogParser(m_config, SyslogdTestUtils.toByteBuffer("<165>1 2003-10-11T22:14:15.000003-00:00 192.0.2.1 myproc 8710 - - %% It's time to make the do-nuts."));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        final Date date = new Date(1065910455003L);

        assertEquals(SyslogFacility.LOCAL4, message.getFacility());
        assertEquals(SyslogSeverity.NOTICE, message.getSeverity());
        assertEquals(1, message.getVersion().intValue());
        assertEquals(date, message.getDate());
        assertEquals("192.0.2.1", message.getHostName());
        assertEquals("myproc", message.getProcessName());
        assertEquals("8710", message.getProcessId());
        assertEquals(null, message.getMessageID());
        assertEquals("%% It's time to make the do-nuts.", message.getMessage());
    }
    
    @Test
    public void testRfc5424ParserExample3() throws Exception {
        final SyslogParser parser = new Rfc5424SyslogParser(m_config, SyslogdTestUtils.toByteBuffer("<165>1 2003-10-11T22:14:15.003Z mymachine.example.com evntslog - ID47 [exampleSDID@32473 iut=\"3\" eventSource=\"Application\" eventID=\"1011\"] An application event log entry..."));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        assertEquals(SyslogFacility.LOCAL4, message.getFacility());
        assertEquals(SyslogSeverity.NOTICE, message.getSeverity());
        assertEquals(1, message.getVersion().intValue());
        assertEquals("mymachine.example.com", message.getHostName());
        assertEquals("evntslog", message.getProcessName());
        assertEquals(null, message.getProcessId());
        assertEquals("ID47", message.getMessageID());
        assertEquals("An application event log entry...", message.getMessage());
    }

    @Test
    @Ignore("We cannot handle byte order mark (BOM) characters yet because we always decode the message buffer as US_ASCII in SyslogParser.fromByteBuffer()")
    public void testRfc5424ParserExampleWithStructuredDataAndByteOrderMark() throws Exception {
        final SyslogParser parser = new Rfc5424SyslogParser(m_config, SyslogdTestUtils.toByteBuffer("<165>1 2003-10-11T22:14:15.003Z mymachine.example.com evntslog - ID47 [exampleSDID@32473 iut=\"3\" eventSource=\"Application\" eventID=\"1011\"] \uFEFFAn application event log entry...", StandardCharsets.UTF_16));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        assertEquals(SyslogFacility.LOCAL4, message.getFacility());
        assertEquals(SyslogSeverity.NOTICE, message.getSeverity());
        assertEquals(1, message.getVersion().intValue());
        assertEquals("mymachine.example.com", message.getHostName());
        assertEquals("evntslog", message.getProcessName());
        assertEquals(null, message.getProcessId());
        assertEquals("ID47", message.getMessageID());
        assertEquals("An application event log entry...", message.getMessage());
    }

    @Test
    public void testRfc5424ParserExample4() throws Exception {
        final SyslogParser parser = new Rfc5424SyslogParser(m_config, SyslogdTestUtils.toByteBuffer("<165>1 2003-10-11T22:14:15.003Z mymachine.example.com evntslog - ID47 [exampleSDID@32473 iut=\"3\" eventSource=\"Application\" eventID=\"1011\"][examplePriority@32473 class=\"high\"]"));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        assertEquals(SyslogFacility.LOCAL4, message.getFacility());
        assertEquals(SyslogSeverity.NOTICE, message.getSeverity());
        assertEquals(1, message.getVersion().intValue());
        assertEquals("mymachine.example.com", message.getHostName());
        assertEquals("evntslog", message.getProcessName());
        assertEquals(null, message.getProcessId());
        assertEquals("ID47", message.getMessageID());
    }
    
    @Test
    public void testRfc5424Nms5051() throws Exception {
        final SyslogParser parser = new Rfc5424SyslogParser(m_config, SyslogdTestUtils.toByteBuffer("<85>1 2011-11-15T14:42:18+01:00 hostname sudo - - - pam_unix(sudo:auth): authentication failure; logname=username uid=0 euid=0 tty=/dev/pts/0 ruser=username rhost= user=username"));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        assertEquals(SyslogFacility.AUTHPRIV, message.getFacility());
        assertEquals(SyslogSeverity.NOTICE, message.getSeverity());
        assertEquals(1, message.getVersion().intValue());
        assertEquals("hostname", message.getHostName());
        assertEquals("sudo", message.getProcessName());
        assertEquals(null, message.getProcessId());
        assertEquals(null, message.getMessageID());
    }

    @Test
    public void testJuniperCFMFault() throws Exception {
        final SyslogParser parser = new Rfc5424SyslogParser(m_config, SyslogdTestUtils.toByteBuffer("<27>1 2012-04-20T12:33:13.946Z junos-mx80-2-space cfmd 1317 CFMD_CCM_DEFECT_RMEP - CFM defect: Remote CCM timeout detected by MEP on Level: 6 MD: MD_service_level MA: PW_126 Interface: ge-1/3/2.1"));
        assertTrue(parser.find());
        final SyslogMessage message = parser.parse();
        assertNotNull(message);
        assertEquals(SyslogFacility.SYSTEM, message.getFacility());
        assertEquals(SyslogSeverity.ERROR, message.getSeverity());
        assertEquals("junos-mx80-2-space", message.getHostName());
        assertEquals("cfmd", message.getProcessName());
        assertEquals("1317", message.getProcessId());
        assertEquals("CFMD_CCM_DEFECT_RMEP", message.getMessageID());
    }

    @Test
    public void shouldHonorTimezoneWithConfiguredDefault() throws IOException, NumberFormatException, ParseException {
        checkDateParserWith(TimeZone.getTimeZone("CET"), "timezone=\"CET\" ");
    }

    @Test
    public void shouldHonorTimezoneWithoutConfiguredDefault() throws IOException, NumberFormatException, ParseException {
        checkDateParserWith(TimeZone.getDefault(), "");
    }

    private void checkDateParserWith(TimeZone expectedTimeZone, String timezoneProperty) throws IOException, ParseException {
        String configuration = "<syslogd-configuration>" +
                "<configuration " +
                "syslog-port=\"10514\" " +
                timezoneProperty +
                "/></syslogd-configuration>";
        final InputStream stream = new ByteArrayInputStream((configuration).getBytes());
        final SyslogdConfigFactory config = new SyslogdConfigFactory(stream);

        final SyslogParser parser = new SyslogParser(config, SyslogdTestUtils.toByteBuffer("something"));
        assertTrue(parser.find());

        // Date Format 1:
        String dateString = "Feb 03 12:21:20";
        int expectedYear = getExpectedYear(dateString);
        LocalDateTime expectedLocalDateTime = LocalDateTime.of(expectedYear, 2, 3 , 12, 21, 20);
        ZonedDateTime expectedDateTime = ZonedDateTime.of(expectedLocalDateTime, expectedTimeZone.toZoneId());
        Date parsedDate = parser.parseDate(dateString);
        assertEquals(expectedDateTime.toInstant().toEpochMilli(), parsedDate.toInstant().toEpochMilli());

        // Date Format 2:
        LocalDate expectedLocalDate = LocalDate.of(expectedYear, 2, 3 );
        expectedDateTime = expectedLocalDate.atStartOfDay(expectedTimeZone.toZoneId());
        parsedDate = parser.parseDate(DateTimeFormatter.ofPattern("yyyy-MM-dd").format(expectedDateTime));
        assertEquals(expectedDateTime.toInstant().toEpochMilli(), parsedDate.toInstant().toEpochMilli());
    }

    static int getExpectedYear(String dateFragment) throws ParseException {
        Date date = new SimpleDateFormat("yyyy MMM dd hh:mm:ss", Locale.ENGLISH).parse("0000 " + dateFragment);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        LocalDateTime ldt = LocalDateTime.of(0, cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));

        return YearGuesser.guessYearForDate(ldt).getYear();
    }

}
