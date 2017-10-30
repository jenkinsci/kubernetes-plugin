package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.lang.ArrayUtils;

public class ContainerExecCutExitCodeUtil {
    private static byte SPACE = " ".getBytes(StandardCharsets.UTF_8)[0];

    public static byte[] getPartToWriteOut(byte[] q, byte[] exitCommandTxt) {
        int exitCodeStartIndex = getExitCodeIndex(q, exitCommandTxt);
        int endIndex = exitCodeStartIndex != -1 ? exitCodeStartIndex : q.length;
        return partToWriteOut(q, endIndex);
    }

    private static byte[] partToWriteOut(byte[] q, int endIndex) {
        return ArrayUtils.subarray(q, 0, endIndex);
    }

    private static int getExitCodeIndex(byte[] q, byte[] exitCommandTxt) {
        if (!endsWithNumber(q)) {
            return -1;
        } else {
            int numbersStartIndex = getNumbersStartIndex(q);
            return getExitCodeStartIndex(q, numbersStartIndex, exitCommandTxt);
        }
    }

    private static int getExitCodeStartIndex(byte[] q, int numbersStartIndex, byte[] exitCommandTxt) {
        int possibleStartIndex = getPossibleStartIndex(q, numbersStartIndex, exitCommandTxt);
        byte[] exitCodeTxtSubarray = getExitCodeTxtSubarray(q, possibleStartIndex, numbersStartIndex);
        if (Arrays.equals(exitCodeTxtSubarray, exitCommandTxt)) {
            return possibleStartIndex;
        }
        return -1;
    }

    private static int getPossibleStartIndex(byte[] q, int numbersStartIndex, byte[] exitCommandTxt) {
        return q.length - (q.length - numbersStartIndex) - exitCommandTxt.length;
    }

    private static byte[] getExitCodeTxtSubarray(byte[] q, int possibleStartIndex, int numbersStartIndex) {
        return ArrayUtils.subarray(q, possibleStartIndex, numbersStartIndex);
    }

    private static int getNumbersStartIndex(byte[] ba) {
        int i = ba.length - 1;
        for (; isNumber(ba[i]); i--)
            ;
        for (; ba[i] == SPACE; i--)
            ;
        return ++i;
    }

    private static boolean endsWithNumber(byte[] ba) {
        return isNumber(ba[ba.length - 1]);
    }

    private static boolean isNumber(byte b) {
        String s = new String(new byte[] { (byte) b }, StandardCharsets.UTF_8);
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
}
