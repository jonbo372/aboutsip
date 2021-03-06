/**
 * 
 */
package com.aboutsip.yajpcap.packet.sip.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.aboutsip.buffer.Buffer;
import com.aboutsip.buffer.Buffers;
import com.aboutsip.yajpcap.packet.sip.SipHeader;

/**
 * Tests to verify that basic parsing functionality that is provided by the
 * {@link SipParser}
 * 
 * @author jonas@jonasborjesson.com
 */
public class SipParserTest {

    protected static final String TAB = (new Character('\t')).toString();
    protected static final String SP = (new Character(' ')).toString();
    protected static final String CRLF = "\r\n";
    protected static final String CR = "\r";
    protected static final String LF = "\r";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    /**
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeQuotedString() throws Exception {
        Buffer buffer = Buffers.wrap("\"hello world\" fup");
        assertThat(SipParser.consumeQuotedString(buffer).toString(), is("hello world"));
        assertThat(buffer.toString(), is(" fup"));

        buffer = Buffers.wrap("\"hello world\"");
        assertThat(SipParser.consumeQuotedString(buffer).toString(), is("hello world"));
        assertThat(buffer.toString(), is(""));

        buffer = Buffers.wrap("\"hello\"");
        assertThat(SipParser.consumeQuotedString(buffer).toString(), is("hello"));
        assertThat(buffer.toString(), is(""));

        buffer = Buffers.wrap("\"hello \\\"world\"");
        assertThat(SipParser.consumeQuotedString(buffer).toString(), is("hello \\\"world"));
        assertThat(buffer.toString(), is(""));
    }

    /**
     * Make sure that we can consume addr-spec.
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeAddressSpec() throws Exception {
        Buffer buffer = Buffers.wrap("sip:alice@example.com");
        assertThat(SipParser.consumeAddressSpec(buffer).toString(), is("sip:alice@example.com"));
        assertThat(buffer.isEmpty(), is(true));

        buffer = Buffers.wrap("sip:alice@example.com>");
        assertThat(SipParser.consumeAddressSpec(buffer).toString(), is("sip:alice@example.com"));
        assertThat(buffer.toString(), is(">"));

        buffer = Buffers.wrap("sip:alice@example.com;transport=tcp");
        assertThat(SipParser.consumeAddressSpec(buffer).toString(), is("sip:alice@example.com;transport=tcp"));
        assertThat(buffer.isEmpty(), is(true));

        buffer = Buffers.wrap("sips:alice@example.com> apa");
        assertThat(SipParser.consumeAddressSpec(buffer).toString(), is("sips:alice@example.com"));
        assertThat(buffer.toString(), is("> apa"));

        buffer = Buffers.wrap("sip:alice@example.com\n");
        assertThat(SipParser.consumeAddressSpec(buffer).toString(), is("sip:alice@example.com"));
        assertThat(buffer.toString(), is("\n"));

        buffer = Buffers.wrap("whatever:alice@example.com hello");
        assertThat(SipParser.consumeAddressSpec(buffer).toString(), is("whatever:alice@example.com"));
        assertThat(buffer.toString(), is(" hello"));

        // no scheme part...
        buffer = Buffers.wrap("alice@example.com hello");
        try {
            assertThat(SipParser.consumeAddressSpec(buffer), is((Buffer) null));
            fail("Expected a SipParseException");
        } catch (final SipParseException e) {
        }

        // if we cannot find the scheme within 100 bytes then we will
        // give up...
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; ++i) {
            sb.append("a");
        }
        sb.append(":");
        buffer = Buffers.wrap(sb.toString() + "alice@example.com hello");
        try {
            assertThat(SipParser.consumeAddressSpec(buffer), is((Buffer) null));
            fail("Expected a SipParseException");
        } catch (final SipParseException e) {
        }

        // and if we haven't found the end after 1000 bytes we will also give up
        sb = new StringBuilder();
        for (int i = 0; i < 1000; ++i) {
            sb.append("a");
        }
        buffer = Buffers.wrap("sip:" + sb.toString());
        try {
            assertThat(SipParser.consumeAddressSpec(buffer), is((Buffer) null));
            fail("Expected a SipParseException");
        } catch (final SipParseException e) {
        }

    }

    /**
     * Consuming a display name can be tricky so make sure we do it correctly.
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeDisplayName() throws Exception {
        Buffer buffer = Buffers.wrap("hello <sip:alice@example.com>");
        assertThat(SipParser.consumeDisplayName(buffer).toString(), is("hello"));
        assertThat(buffer.toString(), is(" <sip:alice@example.com>")); // note that the SP should still be there

        // not actually legal but the consumeDisplayName
        // is not the one enforcing this so should work
        buffer = Buffers.wrap("hello sip:alice@example.com");
        assertThat(SipParser.consumeDisplayName(buffer).toString(), is("hello"));
        assertThat(buffer.toString(), is(" sip:alice@example.com"));

        buffer = Buffers.wrap("sip:alice@example.com");
        assertThat(SipParser.consumeDisplayName(buffer).isEmpty(), is(true));
        assertThat(buffer.toString(), is("sip:alice@example.com"));

        assertThat(SipParser.consumeDisplayName(Buffers.wrap("apa:alice@example.com")).isEmpty(), is(true));
        assertThat(SipParser.consumeDisplayName(Buffers.wrap("<sips:alice@example.com>")).isEmpty(), is(true));
        assertThat(SipParser.consumeDisplayName(Buffers.wrap("     sip:alice@example.com")).isEmpty(), is(true));

        buffer = Buffers.wrap("   <sip:alice@example.com>");
        assertThat(SipParser.consumeDisplayName(buffer).isEmpty(), is(true));
        assertThat(buffer.toString(), is("   <sip:alice@example.com>"));
    }

    /**
     * Test to consume parameters (notice the plural).
     * 
     * <pre>
     *  *( SEMI generic-param )
     * </pre>
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeGenericParams() throws Exception {
        assertGenericParams(";a=b;c=d;foo", "a", "b", "c", "d", "foo", null);
        assertGenericParams(";a", "a", null);
        assertGenericParams(";a ;b;c = d", "a", null, "b", null, "c", "d");
        assertGenericParams("hello this is not a params");
        assertGenericParams(";lr the lr was a flag param followed by some crap", "lr", null);
    }

    /**
     * Helper function for asserting the generic param behavior.
     * 
     * @param input
     * @param expectedKey
     * @param expectedValue
     */
    private void assertGenericParams(final String input, final String... expectedKeyValuePairs) throws Exception {
        final List<Buffer[]> params = SipParser.consumeGenericParams(Buffers.wrap(input));
        if (expectedKeyValuePairs.length == 0) {
            assertThat(params.isEmpty(), is(true));
            return;
        }

        final int noOfParams = expectedKeyValuePairs.length / 2;
        assertThat(params.size(), is(noOfParams));

        for (int i = 0; i < noOfParams; ++i) {
            final Buffer[] actual = params.get(i);
            final String expectedKey = expectedKeyValuePairs[i * 2];
            final String expectedValue = expectedKeyValuePairs[(i * 2) + 1];
            assertThat(actual[0].toString(), is(expectedKey));
            if (expectedValue == null) {
                assertThat(actual[1], is((Buffer) null));
            } else {
                assertThat(actual[1].toString(), is(expectedValue));
            }
        }
    }

    /**
     * Test the following:
     * 
     * <pre>
     * generic-param  =  token [ EQUAL gen-value ]
     * gen-value      =  token / host / quoted-string
     * </pre>
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeGenericParam() throws Exception {
        assertGenericParam("hello=world", "hello", "world");
        assertGenericParam("hello = world", "hello", "world");
        assertGenericParam("h=w", "h", "w");

        // flag params
        assertGenericParam("hello", "hello", null);
        assertGenericParam("h", "h", null);

        // this is technically not legal according to the SIP BNF
        // but there are a lot of implementations out there that
        // does this anyway...
        assertGenericParam("h=", "h", null);

        // the SipParser.consumeGenericParam will ONLY consume
        // the first one so we should still only get hello world
        assertGenericParam("hello=world;foo=boo", "hello", "world");

        // also, it doesn't matter what comes after since it should
        // not be consumed. Off course, some of these constructs are
        // not to be found in a SIP message but these low-level parser
        // functions are not concerned about that.
        assertGenericParam("hello=world some spaces to the left", "hello", "world");

        assertGenericParam("hello = world some spaces to the left", "hello", "world");
        assertGenericParam("hello = w orld some spaces to the left", "hello", "w");
        assertGenericParam("hello = w.orld some spaces to the left", "hello", "w.orld");
    }

    /**
     * Helper function for asserting the generic param behavior.
     * 
     * @param input
     * @param expectedKey
     * @param expectedValue
     */
    private void assertGenericParam(final String input, final String expectedKey, final String expectedValue)
            throws Exception {
        final Buffer[] keyValue = SipParser.consumeGenericParam(Buffers.wrap(input));
        assertThat(keyValue[0].toString(), is(expectedKey));
        if (expectedValue == null) {
            assertThat(keyValue[1], is((Buffer) null));
        } else {
            assertThat(keyValue[1].toString(), is(expectedValue));
        }
    }

    /**
     * Make sure that we can detect alphanumerics correctly
     * 
     * @throws Exception
     */
    @Test
    public void testIsAlphaNum() throws Exception {
        for (int i = 0; i < 10; ++i) {
            assertThat(SipParser.isAlphaNum((char) ('0' + i)), is(true));
        }

        for (int i = 0; i < 26; ++i) {
            assertThat(SipParser.isAlphaNum((char) ('A' + i)), is(true));
            assertThat(SipParser.isAlphaNum((char) ('a' + i)), is(true));
        }
    }

    /**
     * Test all the below stuff
     * 
     * (from RFC 3261 25.1)
     * 
     * When tokens are used or separators are used between elements,
     * whitespace is often allowed before or after these characters:
     * 
     * STAR    =  SWS "*" SWS ; asterisk
     * SLASH   =  SWS "/" SWS ; slash
     * EQUAL   =  SWS "=" SWS ; equal
     * LPAREN  =  SWS "(" SWS ; left parenthesis
     * RPAREN  =  SWS ")" SWS ; right parenthesis
     * RAQUOT  =  ">" SWS ; right angle quote
     * LAQUOT  =  SWS "<"; left angle quote
     * COMMA   =  SWS "," SWS ; comma
     * SEMI    =  SWS ";" SWS ; semicolon
     * COLON   =  SWS ":" SWS ; colon
     * LDQUOT  =  SWS DQUOTE; open double quotation mark
     * RDQUOT  =  DQUOTE SWS ; close double quotation mark
     */
    @Test
    public void testConsumeSeparators() throws Exception {

        // basic happy testing
        assertThat(SipParser.consumeSTAR(Buffers.wrap(" * ")), is(3));
        assertThat(SipParser.consumeSTAR(Buffers.wrap("     * ")), is(7));
        assertThat(SipParser.consumeSTAR(Buffers.wrap("     *    asdf")), is(10));
        assertThat(SipParser.consumeSTAR(Buffers.wrap("*    asdf")), is(5));
        assertThat(SipParser.consumeSTAR(Buffers.wrap("*asdf")), is(1));
        assertThat(SipParser.consumeSTAR(Buffers.wrap("*")), is(1));

        assertThat(SipParser.consumeSLASH(Buffers.wrap(" / ")), is(3));
        assertThat(SipParser.consumeEQUAL(Buffers.wrap(" = ")), is(3));
        assertThat(SipParser.consumeLPAREN(Buffers.wrap(" ( ")), is(3));
        assertThat(SipParser.consumeRPAREN(Buffers.wrap(" ) ")), is(3));
        assertThat(SipParser.consumeRAQUOT(Buffers.wrap(" > ")), is(3));
        assertThat(SipParser.consumeLAQUOT(Buffers.wrap(" < ")), is(3));
        assertThat(SipParser.consumeCOMMA(Buffers.wrap(" , ")), is(3));
        assertThat(SipParser.consumeSEMI(Buffers.wrap(" ; ")), is(3));
        assertThat(SipParser.consumeCOLON(Buffers.wrap(" : ")), is(3));
        assertThat(SipParser.consumeLDQUOT(Buffers.wrap(" \"")), is(2));
        assertThat(SipParser.consumeRDQUOT(Buffers.wrap("\" ")), is(2));

        Buffer buffer = Buffers.wrap("    *    hello");
        assertThat(SipParser.consumeSTAR(buffer), is(9));
        assertThat(buffer.toString(), is("hello"));

        buffer = Buffers.wrap("\"hello\"");
        assertThat(SipParser.consumeLDQUOT(buffer), is(1));
        assertThat(SipParser.consumeToken(buffer).toString(), is("hello"));
        assertThat(SipParser.consumeRDQUOT(buffer), is(1));
    }

    /**
     * Test to consume a token as specified by RFC3261 section 25.1
     * 
     * token = 1*(alphanum / "-" / "." / "!" / "%" / "*" / "_" / "+" / "`" / "'"
     * / "~" )
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeAndExpectToken() throws Exception {
        Buffer buffer = Buffers.wrap("hello world");
        assertConsumeAndExpectToken(buffer, "hello");
        SipParser.consumeWS(buffer);
        assertConsumeAndExpectToken(buffer, "world");

        buffer = Buffers.wrap("!hello");
        assertConsumeAndExpectToken(buffer, "!hello");

        final String all = "-.!%*_+`'~";
        buffer = Buffers.wrap(all + "hello");
        assertConsumeAndExpectToken(buffer, all + "hello");

        buffer = Buffers.wrap(all + "hello world" + all);
        assertConsumeAndExpectToken(buffer, all + "hello");
        SipParser.consumeWS(buffer);
        assertConsumeAndExpectToken(buffer, "world" + all);

        buffer = Buffers.wrap(all + "019hello world" + all);
        assertConsumeAndExpectToken(buffer, all + "019hello");

        buffer = Buffers.wrap("0");
        assertConsumeAndExpectToken(buffer, "0");

        buffer = Buffers.wrap("09");
        assertConsumeAndExpectToken(buffer, "09");

        buffer = Buffers.wrap("19");
        assertConsumeAndExpectToken(buffer, "19");

        buffer = Buffers.wrap("0987654321");
        assertConsumeAndExpectToken(buffer, "0987654321");

        // none of the below are part of the token "family"
        assertConsumeAndExpectToken(Buffers.wrap("&"), null);
        assertConsumeAndExpectToken(Buffers.wrap("&asdf"), null);
        assertConsumeAndExpectToken(Buffers.wrap("="), null);
        assertConsumeAndExpectToken(Buffers.wrap(";="), null);
        assertConsumeAndExpectToken(Buffers.wrap(" "), null);
        assertConsumeAndExpectToken(Buffers.wrap("\t"), null);
    }

    /**
     * Helper method that tests both consume and expect token at the same time.
     * 
     * @param buffer
     * @param expected
     */
    private void assertConsumeAndExpectToken(final Buffer buffer, final String expected) throws Exception {
        final Buffer b = buffer.slice();
        if (expected == null) {
            assertThat(SipParser.consumeToken(buffer), is((Buffer) null));
            try {
                SipParser.expectToken(b);
                fail("Expected a SipParseException because there is no token");
            } catch (final SipParseException e) {
                // expected
            }
        } else {
            assertThat(SipParser.consumeToken(buffer).toString(), is(expected));
            assertThat(SipParser.expectToken(b).toString(), is(expected));
        }

    }

    /**
     * Tests so that the index of the SipParseException is correct.
     * 
     * @throws Exception
     */
    @Test
    public void textExpectTokenSipParseException() throws Exception {
        assertSipParseExceptionIndexForExpectToken(Buffers.wrap(";hello world"), 0);
        assertSipParseExceptionIndexForExpectToken(Buffers.wrap(";"), 0);

        Buffer buffer = Buffers.wrap("hello ;world");
        SipParser.consumeToken(buffer);
        SipParser.consumeWS(buffer);
        assertSipParseExceptionIndexForExpectToken(buffer, 6);

        buffer = Buffers.wrap("hello;");
        SipParser.consumeToken(buffer);
        assertSipParseExceptionIndexForExpectToken(buffer, 5);
    }

    /**
     * Helper method for verifying the index in the {@link SipParseException}
     * for the {@link SipParser#expectToken(Buffer)}
     * 
     * @param buffer
     * @param expectedIndex
     * @throws IOException
     * @throws IndexOutOfBoundsException
     */
    private void assertSipParseExceptionIndexForExpectToken(final Buffer buffer, final int expectedIndex)
            throws IndexOutOfBoundsException, IOException {
        try {
            SipParser.expectToken(buffer);
        } catch (final SipParseException e) {
            assertThat(e.getErroOffset(), is(expectedIndex));
        }

    }

    /**
     * Make sure that we consume SEMI as defined by 3261 section 25.1
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeSEMI() throws Exception {
        Buffer buffer = Buffers.wrap("  ;  hello");
        assertThat(SipParser.consumeSEMI(buffer), is(5));
        assertThat(buffer.toString(), is("hello"));

        buffer = Buffers.wrap(";  hello");
        assertThat(SipParser.consumeSEMI(buffer), is(3));
        assertThat(buffer.toString(), is("hello"));

        buffer = Buffers.wrap(";hello");
        assertThat(SipParser.consumeSEMI(buffer), is(1));
        assertThat(buffer.toString(), is("hello"));

        buffer = Buffers.wrap("hello");
        assertThat(SipParser.consumeSEMI(buffer), is(0));
        assertThat(buffer.toString(), is("hello"));

        buffer = Buffers.wrap(";");
        assertThat(SipParser.consumeSEMI(buffer), is(1));
        assertThat(buffer.toString(), is(""));
    }

    /**
     * Make sure we recognize and bail out on a non-digit
     * 
     * @throws Exception
     */
    @Test
    public void testExpectDigitFailure() throws Exception {
        try {
            SipParser.expectDigit(Buffers.wrap("abc"));
            fail("Expected SipParseException");
        } catch (final SipParseException e) {
        }

        try {
            // character '/' is just before zero in the ascii table so
            // therefore some boundary testing
            SipParser.expectDigit(Buffers.wrap("/abc"));
            fail("Expected SipParseException");
        } catch (final SipParseException e) {
        }

        try {
            // character ':' is just after 9 in the ascii table so
            // therefore some boundary testing
            SipParser.expectDigit(Buffers.wrap(":abc"));
            fail("Expected SipParseException");
        } catch (final SipParseException e) {
        }

        try {
            SipParser.expectDigit(Buffers.wrap("    "));
            fail("Expected SipParseException");
        } catch (final SipParseException e) {
        }
    }

    @Test
    public void testExpectDigit() throws Exception {
        assertThat(SipParser.expectDigit(Buffers.wrap("213 apa")).toString(), is("213"));
        assertThat(SipParser.expectDigit(Buffers.wrap("2 apa")).toString(), is("2"));
        assertThat(SipParser.expectDigit(Buffers.wrap("2apa")).toString(), is("2"));
        assertThat(SipParser.expectDigit(Buffers.wrap("2")).toString(), is("2"));
        assertThat(SipParser.expectDigit(Buffers.wrap("0")).toString(), is("0"));
        assertThat(SipParser.expectDigit(Buffers.wrap("9")).toString(), is("9"));
        assertThat(SipParser.expectDigit(Buffers.wrap("9   ")).toString(), is("9"));
    }

    /**
     * Taken from section 7.3.1 Header Field Format in RFC3261
     * 
     * @throws Exception
     */
    @Test
    public void testLunch() throws Exception {

        assertHeader("Subject:            lunch", "Subject", "lunch");
        assertHeader("Subject      :      lunch", "Subject", "lunch");
        assertHeader("Subject            :lunch", "Subject", "lunch");
        assertHeader("Subject            :      lunch", "Subject", "lunch");
        assertHeader("Subject: lunch", "Subject", "lunch");
        assertHeader("Subject   :lunch", "Subject", "lunch");
        assertHeader("Subject                :lunch", "Subject", "lunch");
        assertHeader("Subject                :\r\n lunch", "Subject", "lunch");
    }

    /**
     * Test so that we actually can handle folded lines correctly...
     * 
     * @throws Exception
     */
    @Test
    public void testFoldedHeader() throws Exception {
        final String expectedValue = "I know you're there, pick up the phone and talk to me!";
        final String foldedValue = "I know you're there,\r\n" + "      pick up the phone\r\n" + TAB + "and talk to me!";

        assertHeader("Subject: " + foldedValue + "\r\n", "Subject", expectedValue);
    }

    private void assertHeader(final String rawHeader, final String name, final String value) throws Exception {
        // remember, these headers are being framed and are therefore in a sip
        // message and as such, there will always be CRLF at the end, which is
        // why we pad them here
        final Buffer buffer = Buffers.wrap(rawHeader + "\r\n");
        final SipHeader header = SipParser.nextHeader(buffer);
        assertThat(header.getName().toString(), is(name));
        assertThat(header.getValue().toString(), is(value));
    }

    /**
     * LWS expects 1 WS to be present
     * 
     * @throws Exception
     */
    @Test(expected = SipParseException.class)
    public void testConsumeLWSBad1() throws Exception {
        assertLWSConsumption("", "monkey");
    }

    /**
     * LWS expects 1 WS to be present after a CRLF
     * 
     * @throws Exception
     */
    @Test(expected = SipParseException.class)
    public void testConsumeLWSBad2() throws Exception {
        assertLWSConsumption(CRLF, "monkey");
    }

    @Test(expected = SipParseException.class)
    public void testConsumeLWSBad3() throws Exception {
        assertLWSConsumption(TAB + SP + CRLF, "monkey");
    }

    /**
     * Make sure that we can consume LWS according to spec (even though we do
     * consume a little too much WS in certain cases, see comment in test and in
     * code)
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeLWS() throws Exception {
        assertLWSConsumption(" ", "monkey");
        assertLWSConsumption(TAB, "monkey");

        // LWS expects 1 SP to be present after a CRLF
        assertLWSConsumption(CRLF + SP, "monkey");
        assertLWSConsumption(CRLF + TAB, "monkey");

        // many WS followed by one CRLF followed by one WS is ok
        assertLWSConsumption(TAB + SP + CRLF + SP, "monkey");

        // this is not quite according to spec since we really
        // aren't supposed to consume extra WS if there is no CRLF present
        // However, for now let's keep it like this...
        assertLWSConsumption(TAB + SP + SP, "monkey");
        assertLWSConsumption(SP + SP, "monkey");
        assertLWSConsumption(SP + SP + TAB + TAB, "monkey");
    }

    private void assertLWSConsumption(final String LWS, final String expected) throws Exception {
        final Buffer buffer = stringToBuffer(LWS + expected);
        final boolean stuffConsumed = SipParser.consumeLWS(buffer) > 0;
        assertThat(bufferToString(buffer), is(expected));

        // in the case of LWS, we should always consume at least 1 WS
        // otherwise there should have been an exception thrown
        assertThat(stuffConsumed, is(true));
    }

    @Test(expected = SipParseException.class)
    public void testExpectWSButNoWS() throws Exception {
        SipParser.expectWS(stringToBuffer("no ws here!"));
    }

    @Test
    public void testExpectWS() throws Exception {
        Buffer buffer = stringToBuffer(" hello");
        SipParser.expectWS(buffer);
        assertThat(bufferToString(buffer), is("hello"));

        buffer = stringToBuffer(TAB + "hello");
        SipParser.expectWS(buffer);
        assertThat(bufferToString(buffer), is("hello"));

        try {
            buffer = stringToBuffer("hello");
            SipParser.expectWS(buffer);
            fail("expected ParseException here due to no WS, which was expected");
        } catch (final SipParseException e) {
            // this is important though (we is why we are doing the
            // old style junit3 fail thingie

            // the buffer should still be at "hello" since we did not
            // extract out any white space
            assertThat(bufferToString(buffer), is("hello"));
        }

    }

    @Test
    public void testConsumeWhitespace() throws Exception {
        assertWSConsumption(0, 0, "hello");
        assertWSConsumption(1, 0, "hello");
        assertWSConsumption(0, 1, "hello");
        assertWSConsumption(1, 1, "hello");
        assertWSConsumption(1, 1, "hello ");
        assertWSConsumption(2, 2, "hello ");
        assertWSConsumption(20, 2, "hello whatever more ws at the end         ");
    }

    private void assertWSConsumption(final int spBefore, final int tabBefore, final String expected)
            throws SipParseException {
        String padding = "";
        for (int i = 0; i < spBefore; ++i) {
            padding += SP;
        }

        for (int i = 0; i < tabBefore; ++i) {
            padding += TAB;
        }

        final Buffer buffer = stringToBuffer(padding + expected);
        final boolean stuffConsumed = SipParser.consumeWS(buffer) > 0;
        assertThat(bufferToString(buffer), is(expected));

        // also make sure we return the boolean indicating that we consumed
        // some WS...
        assertThat(stuffConsumed, is((spBefore + tabBefore) > 0));

    }

    @Test
    public void testReadUntilCRLF() throws Exception {
        assertReadUntilCRLF("hello ", " and this is the stuff that should be left");
        assertReadUntilCRLF("", "Having the line start with CRLF should work too");
        assertReadUntilCRLF("", "");
    }

    private void assertReadUntilCRLF(final String whatsRead, final String whatsLeft) throws Exception {
        final Buffer buffer = stringToBuffer(whatsRead + "\r\n" + whatsLeft);
        final Buffer result = buffer.readLine();
        assertThat(bufferToString(result), is(whatsRead));
        assertThat(bufferToString(buffer), is(whatsLeft));
    }

    @Test
    public void testConsumeCRLF() throws Exception {
        assertCRLFConsumption(CRLF + "hello", "hello", true);

        assertCRLFConsumption(LF + "hello", LF + "hello", false);
        assertCRLFConsumption(CR + "hello", CR + "hello", false);
        assertCRLFConsumption(SP + CRLF + "hello", SP + CRLF + "hello", false);
    }

    private void assertCRLFConsumption(final String parse, final String expected, final boolean expectedConsumption)
            throws SipParseException {
        final Buffer buffer = stringToBuffer(parse);
        final boolean stuffConsumed = SipParser.consumeCRLF(buffer) > 0;
        assertThat(bufferToString(buffer), is(expected));
        assertThat(stuffConsumed, is(expectedConsumption));
    }

    /**
     * The difference between SWS and LWS is that SWS has LWS as optional.
     * Hence, there should never be any exceptions thrown out of SWS
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeSWS() throws Exception {

        // same as from the LWS and the underlying implementation
        // is actually the same but from a test perspective we dont
        // "know" that so just make sure it works for SWS as well.
        // Just in case we ever change the underlying implementation
        assertSWSConsumption(" ", "monkey", true);
        assertSWSConsumption(TAB, "monkey", true);
        assertSWSConsumption(CRLF + SP, "monkey", true);
        assertSWSConsumption(CRLF + TAB, "monkey", true);
        assertSWSConsumption(TAB + SP + CRLF + SP, "monkey", true);
        assertSWSConsumption(TAB + SP + SP, "monkey", true);
        assertSWSConsumption(SP + SP, "monkey", true);
        assertSWSConsumption(SP + SP + TAB + TAB, "monkey", true);

        // the following would have blown up in the case of LWS
        // hmmm, not sure about in particularly the last one. Is this really
        // what people do?
        assertSWSConsumption("", "monkey", false);
        assertSWSConsumption(CRLF, "monkey", false);
        assertSWSConsumption(TAB + SP + CRLF, "monkey", false);

    }

    private void assertSWSConsumption(final String SWS, final String expected, final boolean shouldWeConsumeStuff)
            throws Exception {
        final Buffer buffer = stringToBuffer(SWS + expected);
        final boolean stuffConsumed = SipParser.consumeSWS(buffer) > 0;
        assertThat(bufferToString(buffer), is(expected));

        assertThat(stuffConsumed, is(shouldWeConsumeStuff));
    }

    public String bufferToString(final Buffer buffer) {
        return buffer.toString();
    }

    public Buffer stringToBuffer(final String s) {
        return Buffers.wrap(s.getBytes());
    }

}
