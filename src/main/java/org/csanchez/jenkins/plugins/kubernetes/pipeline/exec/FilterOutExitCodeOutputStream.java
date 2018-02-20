package org.csanchez.jenkins.plugins.kubernetes.pipeline.exec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import org.apache.commons.lang.ArrayUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class FilterOutExitCodeOutputStream extends OutputStream {

	public FilterOutExitCodeOutputStream(OutputStream sink, List<FilterOutExitCodeOutputStream> streamsToFilter) {
		this.sink = sink;
		streamsToFilter.add(this);
	}

	private final static byte[] EXIT_COMMAND_TXT_BYTES;

	public static byte[] getExitCommandTxtBytes() {
		return EXIT_COMMAND_TXT_BYTES.clone();
	}

	private final static int QUEUE_SIZE = 20;
	private final OutputStream sink;
	private final Queue<Byte> queue = new ArrayDeque<Byte>(QUEUE_SIZE);

	static {
		byte[] newLine = new byte[1];
		Arrays.fill(newLine, "\n".getBytes(StandardCharsets.UTF_8)[0]);
		EXIT_COMMAND_TXT_BYTES = ArrayUtils.addAll(newLine, ExitCodeOutputStream.EXIT_COMMAND_TXT.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public void write(int b) throws IOException {
		if (queue.size() >= QUEUE_SIZE)
			sink.write(queue.poll());
		queue.offer((byte) b);
	}

	public void writeOutBuffer() throws IOException {
		byte[] q = ArrayUtils.toPrimitive(queue.toArray(new Byte[queue.size()]));
		byte[] partToWriteOut = ContainerExecCutExitCodeUtil.getPartToWriteOut(q, EXIT_COMMAND_TXT_BYTES);
		sink.write(partToWriteOut);
	}
}