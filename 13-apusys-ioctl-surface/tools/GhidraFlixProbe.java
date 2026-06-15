import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.mem.Memory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class GhidraFlixProbe extends GhidraScript {
	private static final long[] PROBE_ADDRS = {
		0x700169a2L,
		0x700169a4L,
		0x70016e2dL,
		0x70017b73L,
		0x70017cd1L,
		0x70017cd3L,
		0x70017d41L,
		0x70081ec5L,
		0x70081ee7L,
		0x70082aacL
	};

	@Override
	public void run() throws Exception {
		String[] args = getScriptArgs();
		File outFile = new File(args.length > 0 ? args[0] : "/tmp/ghidra_flix_probe.txt");
		PrintWriter out = new PrintWriter(new FileWriter(outFile));
		try {
			out.println("program=" + currentProgram.getName());
			out.println("language=" + currentProgram.getLanguageID().getIdAsString());
			out.println("compiler=" + currentProgram.getCompilerSpec().getCompilerSpecID().getIdAsString());
			out.println("image_base=" + fmt(currentProgram.getImageBase()));
			out.println("min_address=" + fmt(currentProgram.getMinAddress()));
			out.println("max_address=" + fmt(currentProgram.getMaxAddress()));
			out.println();
			for (long value : PROBE_ADDRS) {
				probe(out, value);
			}
		}
		finally {
			out.close();
		}
		println("wrote " + outFile.getAbsolutePath());
	}

	private Address addr(long value) {
		return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(value);
	}

	private String fmt(Address address) {
		return address == null ? "null" : "0x" + Long.toHexString(address.getOffset());
	}

	private void probe(PrintWriter out, long value) throws Exception {
		Address start = addr(value);
		Listing listing = currentProgram.getListing();
		Function fnAt = currentProgram.getFunctionManager().getFunctionAt(start);
		Function fnContaining = currentProgram.getFunctionManager().getFunctionContaining(start);
		out.println("## probe " + fmt(start));
		out.println("function_at=" + functionName(fnAt));
		out.println("function_containing=" + functionName(fnContaining));
		out.println("bytes=" + bytes(start, 64));
		boolean disassembled = false;
		try {
			disassembled = disassemble(start);
		}
		catch (Exception e) {
			out.println("explicit_disassemble_exception=" + e.getClass().getName() + ": " + e.getMessage());
		}
		out.println("explicit_disassemble=" + disassembled);

		Instruction insn = listing.getInstructionAt(start);
		if (insn == null) {
			insn = listing.getInstructionAfter(start);
			if (insn != null && insn.getAddress().subtract(start) > 0x80) {
				insn = null;
			}
		}
		if (insn == null) {
			out.println("instructions=<none within +0x80>");
			out.println();
			return;
		}
		out.println("instructions=");
		for (int i = 0; i < 24 && insn != null && !monitor.isCancelled(); i++) {
			if (insn.getAddress().subtract(start) > 0x80) {
				break;
			}
			out.println("  " + fmt(insn.getAddress()) + " " + insn.toString());
			insn = insn.getNext();
		}
		out.println();
	}

	private String functionName(Function fn) {
		if (fn == null) {
			return "null";
		}
		return fn.getName() + "@" + fmt(fn.getEntryPoint());
	}

	private String bytes(Address start, int count) throws Exception {
		Memory memory = currentProgram.getMemory();
		byte[] buf = new byte[count];
		int n = memory.getBytes(start, buf);
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < n; i++) {
			if (i > 0) {
				out.append(' ');
			}
			out.append(String.format("%02x", buf[i] & 0xff));
		}
		return out.toString();
	}
}
