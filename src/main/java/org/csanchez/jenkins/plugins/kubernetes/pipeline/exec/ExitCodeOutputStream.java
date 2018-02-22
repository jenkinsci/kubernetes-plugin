package org.csanchez.jenkins.plugins.kubernetes.pipeline.exec;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.EXIT;
import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.NEWLINE;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.csanchez.jenkins.plugins.kubernetes.pipeline.EvictingQueue;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Keeps the last bytes of the output stream to parse the exit code
 */
@Restricted(NoExternalUse.class)
public class ExitCodeOutputStream extends OutputStream {

    public static final String EXIT_COMMAND_TXT = "EXITCODE";
    public static final String EXIT_COMMAND = "printf \"" + EXIT_COMMAND_TXT + " %3d\" $?; " + EXIT + NEWLINE;
    private static final Logger LOGGER = Logger.getLogger(ExitCodeOutputStream.class.getName());

    private EvictingQueue<Integer> queue = EvictingQueue.create(20);

    public ExitCodeOutputStream() {
    }

    @Override
    public void write(int b) throws IOException {
        queue.add(b);
    }

    public int getExitCode() {
        ByteBuffer b = ByteBuffer.allocate(queue.size());
        queue.stream().filter(Objects::nonNull).forEach((i) -> b.put((byte) i.intValue()));
        // output ends in a 3 digit padded exit code + newline (13 10)
        // as defined in ContainerExecDecorator#doExec
        // ie. 32 32 49 13 10 for exit code 1
        int i = 1;
        String s = new String(b.array(), StandardCharsets.UTF_8);
        if (s.indexOf(EXIT_COMMAND_TXT) < 0) {
        	ExitCodeOutputStream.LOGGER.log(Level.WARNING, "Unable to find \"{0}\" in {1}", new Object[]{EXIT_COMMAND_TXT, s});
            return i;
        }
        // parse the exitcode int printed after EXITCODE
        int start = s.indexOf(EXIT_COMMAND_TXT) + EXIT_COMMAND_TXT.length();
        s = s.substring(start, start + 4).trim();
        try {
            i = Integer.parseInt(s);
        } catch (NumberFormatException e) {
        	ExitCodeOutputStream.LOGGER.log(Level.WARNING, "Unable to parse exit code as integer: \"{0}\" {1} / {2}",
                    new Object[]{s, queue.toString(), Arrays.toString(b.array())});
        }
        return i;
    }
}