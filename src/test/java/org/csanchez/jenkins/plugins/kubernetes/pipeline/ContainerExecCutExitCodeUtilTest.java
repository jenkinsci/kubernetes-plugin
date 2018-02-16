package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerExecCutExitCodeUtil.getPartToWriteOut;
import static org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerExecDecorator.FilterOutExitCodeOutputStream.EXIT_COMMAND_TXT_BYTES;
import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class ContainerExecCutExitCodeUtilTest {

  final static String EXITCODE_GOOD = "\nEXITCODE   0";
  final static String EXITCODE_ERR = "\nEXITCODE   127";
  final static String EXITCODE_BAD_NONUM = "\nEXITCODE   ";
  final static String LONG_STR = "this is a very long string. followd by something. or not. who knows?";
  final static String SHORT_STR = "a";
  final static byte[] EMPTY = "".getBytes(StandardCharsets.UTF_8);
  final static byte[] GOOD_LONG = (LONG_STR + EXITCODE_GOOD).getBytes(StandardCharsets.UTF_8);
  final static byte[] GOOD_SHORT = (SHORT_STR + EXITCODE_GOOD).getBytes(StandardCharsets.UTF_8);
  final static byte[] BAD_LONG = (LONG_STR + EXITCODE_BAD_NONUM).getBytes(StandardCharsets.UTF_8);
  final static byte[] BAD_SHORT = (SHORT_STR + EXITCODE_BAD_NONUM).getBytes(StandardCharsets.UTF_8);
  final static byte[] BAD_LONG_NOEXIT = LONG_STR.getBytes(StandardCharsets.UTF_8);
  final static byte[] BAD_SHORT_NOEXIT = SHORT_STR.getBytes(StandardCharsets.UTF_8);
  final static byte[] ERR_LONG = (LONG_STR + EXITCODE_ERR).getBytes(StandardCharsets.UTF_8);
  final static byte[] ERR_SHORT = (SHORT_STR + EXITCODE_ERR).getBytes(StandardCharsets.UTF_8);

  @Test
  public void testGoodLong() {
    byte[] p = getPartToWriteOut(GOOD_LONG, EXIT_COMMAND_TXT_BYTES);
    String s = new String(p, StandardCharsets.UTF_8);
    assertEquals(LONG_STR, s);
  }

  @Test
  public void testGoodShort() {
    byte[] p = getPartToWriteOut(GOOD_SHORT, EXIT_COMMAND_TXT_BYTES);
    String s = new String(p, StandardCharsets.UTF_8);
    assertEquals(SHORT_STR, s);
  }

  @Test
  public void testErrLong() {
    byte[] p = getPartToWriteOut(ERR_LONG, EXIT_COMMAND_TXT_BYTES);
    String s = new String(p, StandardCharsets.UTF_8);
    assertEquals(LONG_STR, s);
  }

  @Test
  public void testErrShort() {
    byte[] p = getPartToWriteOut(ERR_SHORT, EXIT_COMMAND_TXT_BYTES);
    String s = new String(p, StandardCharsets.UTF_8);
    assertEquals(SHORT_STR, s);
  }

  @Test
  public void testBadShort() {
    byte[] p = getPartToWriteOut(BAD_SHORT, EXIT_COMMAND_TXT_BYTES);
    String s = new String(p, StandardCharsets.UTF_8);
    assertEquals(new String(BAD_SHORT, StandardCharsets.UTF_8), s);
  }

  @Test
  public void testBadLong() {
    byte[] p = getPartToWriteOut(BAD_LONG, EXIT_COMMAND_TXT_BYTES);
    String s = new String(p, StandardCharsets.UTF_8);
    assertEquals(new String(BAD_LONG, StandardCharsets.UTF_8), s);
  }

  @Test
  public void testBadShortNoExit() {
    byte[] p = getPartToWriteOut(BAD_SHORT_NOEXIT, EXIT_COMMAND_TXT_BYTES);
    String s = new String(p, StandardCharsets.UTF_8);
    assertEquals(new String(BAD_SHORT_NOEXIT, StandardCharsets.UTF_8), s);
  }

  @Test
  public void testBadLongNoExit() {
    byte[] p = getPartToWriteOut(BAD_LONG_NOEXIT, EXIT_COMMAND_TXT_BYTES);
    String s = new String(p, StandardCharsets.UTF_8);
    assertEquals(new String(BAD_LONG_NOEXIT, StandardCharsets.UTF_8), s);
  }
}
