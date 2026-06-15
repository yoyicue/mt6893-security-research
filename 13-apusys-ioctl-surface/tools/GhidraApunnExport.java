/* ###
 * Exports a compact APUNN firmware analysis snapshot from Ghidra headless.
 *
 * Usage:
 *   analyzeHeadless /tmp/ghidra_apunn_full_elf ApunnFullElf \
 *     -import /tmp/apunn_core0_full.elf \
 *     -analysisTimeoutPerFile 300 \
 *     -scriptPath 13-apusys-ioctl-surface/tools \
 *     -postScript GhidraApunnExport.java /tmp/apunn_ghidra_full_elf_export
 */

import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressRange;
import ghidra.program.model.address.AddressRangeIterator;
import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.DataIterator;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.ReferenceIterator;
import ghidra.program.model.symbol.ReferenceManager;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GhidraApunnExport extends GhidraScript {
	private static final long MAX_WINDOW_FORWARD_BYTES = 0x100L;

	private static final long[] INTERESTING_ADDRS = {
		0x70000010L, // execute_op string
		0x70000b80L, // rodata pointer table cluster
		0x70001ab0L, // process_command string
		0x70001c20L, // dma_barrier string
		0x70001e60L, // d2d_flo.c string
		0x70006794L  // kernel-provided INFO16 target for core mask 0x61
	};

	private static final String[] INTERESTING_TEXT = {
		"execute_op",
		"process_command",
		"kernelProcess",
		"flk_",
		"XTENSA_ANN_VERSION",
		"main_proc.c",
		"d2d_flo.c",
		"xrp_dsp.c",
		"dmaif.c",
		"dma_barrier",
		"DMA",
		"iDMA",
		"Invalid",
		"buffer",
		"Desc",
		"TM_DMA"
	};

	private static final long[] DECOMPILE_CANDIDATES = {
		0x70006794L, // ELF entry / kernel INFO16 target
		0x70006590L, // early APUNN startup helper found by Ghidra
		0x70007440L, // early callee from 0x70006590
		0x700169a4L, // rodata function pointer table candidate
		0x70017c00L, // rodata function pointer cluster vicinity
		0x70017d40L, // rodata function pointer table candidate
		0x70071b88L, // repeated op-wrapper call pattern
		0x700301d8L, // mid-level dispatcher-like function
		0x7003b424L, // largest auto-identified function in the core
		0x70081ee6L, // second rodata function pointer table cluster
		0x70083068L  // dense op-kernel branch cluster
	};

	@Override
	public void run() throws Exception {
		String[] args = getScriptArgs();
		File outDir = new File(args.length > 0 ? args[0] : "/tmp/apunn_ghidra_export");
		if (!outDir.isDirectory() && !outDir.mkdirs()) {
			throw new RuntimeException("failed to create " + outDir);
		}

		List<String> functions = collectFunctions();
		List<String> strings = collectInterestingStrings();
		List<String> windows = collectDisassemblyWindows();
		List<String> decompiled = collectDecompileCandidates();

		writeLines(new File(outDir, "functions.txt"), functions);
		writeLines(new File(outDir, "interesting_strings.txt"), strings);
		writeLines(new File(outDir, "disassembly_windows.txt"), windows);
		writeLines(new File(outDir, "decompile_candidates.c"), decompiled);
		writeSummaryJson(new File(outDir, "summary.json"), functions.size(), strings.size());

		println("APUNN export wrote " + outDir.getAbsolutePath());
	}

	private Address addr(long value) {
		return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(value);
	}

	private String fmt(Address address) {
		return address == null ? "null" : "0x" + Long.toHexString(address.getOffset());
	}

	private long bodySize(AddressSetView body) {
		long total = 0;
		AddressRangeIterator ranges = body.getAddressRanges();
		while (ranges.hasNext()) {
			AddressRange range = ranges.next();
			total += range.getLength();
		}
		return total;
	}

	private List<String> collectFunctions() {
		List<String> out = new ArrayList<String>();
		Listing listing = currentProgram.getListing();
		FunctionIterator functions = currentProgram.getFunctionManager().getFunctions(true);
		while (functions.hasNext() && !monitor.isCancelled()) {
			Function function = functions.next();
			AddressSetView body = function.getBody();
			InstructionIterator instructions = listing.getInstructions(body, true);
			int insnCount = 0;
			int callCount = 0;
			StringBuilder calls = new StringBuilder();
			while (instructions.hasNext() && !monitor.isCancelled()) {
				Instruction instruction = instructions.next();
				insnCount++;
				for (Reference ref : instruction.getReferencesFrom()) {
					if (ref.getReferenceType().isCall() || ref.getReferenceType().isJump()) {
						if (callCount < 16) {
							if (calls.length() > 0) {
								calls.append(",");
							}
							calls.append(fmt(ref.getFromAddress()));
							calls.append("->");
							calls.append(fmt(ref.getToAddress()));
							calls.append(":");
							calls.append(ref.getReferenceType().toString());
						}
						callCount++;
					}
				}
			}
			out.add(String.format(
				"%s size=0x%x insns=%d calls=%d name=%s sample_calls=%s",
				fmt(function.getEntryPoint()),
				bodySize(body),
				insnCount,
				callCount,
				function.getName(),
				calls.toString()));
		}
		return out;
	}

	private boolean isInteresting(String value) {
		for (String token : INTERESTING_TEXT) {
			if (value.indexOf(token) >= 0) {
				return true;
			}
		}
		return false;
	}

	private List<String> collectInterestingStrings() {
		List<String> out = new ArrayList<String>();
		ReferenceManager refman = currentProgram.getReferenceManager();
		DataIterator dataIt = currentProgram.getListing().getDefinedData(true);
		while (dataIt.hasNext() && !monitor.isCancelled()) {
			Data data = dataIt.next();
			Object valueObject = data.getValue();
			String value = valueObject == null ? "" : valueObject.toString();
			String dataType = data.getDataType() == null ? "" : data.getDataType().getName();
			if (dataType.toLowerCase().indexOf("string") < 0 && value.indexOf("..") < 0) {
				continue;
			}
			if (!isInteresting(value)) {
				continue;
			}
			List<String> refs = new ArrayList<String>();
			ReferenceIterator refIt = refman.getReferencesTo(data.getAddress());
			while (refIt.hasNext() && refs.size() < 32) {
				Reference ref = refIt.next();
				refs.add(fmt(ref.getFromAddress()) + ":" + ref.getReferenceType());
			}
			out.add(String.format(
				"%s len=0x%x refs=%d refs_sample=%s value=%s",
				fmt(data.getAddress()),
				data.getLength(),
				refs.size(),
				join(refs, ","),
				value));
		}
		return out;
	}

	private List<String> collectDisassemblyWindows() {
		List<String> out = new ArrayList<String>();
		for (long start : INTERESTING_ADDRS) {
			out.add("## window 0x" + Long.toHexString(start));
			out.addAll(disassembleWindow(addr(start), 80));
			out.add("");
		}
		return out;
	}

	private List<String> collectDecompileCandidates() {
		List<String> out = new ArrayList<String>();
		DecompInterface decompiler = new DecompInterface();
		decompiler.openProgram(currentProgram);
		try {
			for (long value : DECOMPILE_CANDIDATES) {
				Address address = addr(value);
				Function function = currentProgram.getFunctionManager().getFunctionContaining(address);
				if (function == null) {
					function = currentProgram.getFunctionManager().getFunctionAt(address);
				}
				out.add("/* ============================================================");
				out.add(" * candidate " + fmt(address) + " function=" +
					(function == null ? "null" : function.getName() + "@" + fmt(function.getEntryPoint())));
				out.add(" * ============================================================ */");
				if (function == null) {
					out.add("/* no function identified */");
					out.add("");
					continue;
				}
				DecompileResults results = decompiler.decompileFunction(function, 30, monitor);
				if (!results.decompileCompleted() || results.getDecompiledFunction() == null) {
					out.add("/* decompile failed: " + results.getErrorMessage() + " */");
					out.add("");
					continue;
				}
				out.add(results.getDecompiledFunction().getC());
				out.add("");
			}
		}
		finally {
			decompiler.dispose();
		}
		return out;
	}

	private List<String> disassembleWindow(Address start, int count) {
		List<String> out = new ArrayList<String>();
		Listing listing = currentProgram.getListing();
		Instruction instruction = listing.getInstructionAt(start);
		if (instruction == null) {
			instruction = listing.getInstructionAfter(start);
		}
		if (instruction == null ||
				instruction.getAddress().getOffset() - start.getOffset() > MAX_WINDOW_FORWARD_BYTES) {
			out.add("no instruction within +0x" + Long.toHexString(MAX_WINDOW_FORWARD_BYTES) +
				" of " + fmt(start));
			return out;
		}
		while (instruction != null && out.size() < count && !monitor.isCancelled()) {
			String operands = "";
			for (int i = 0; i < instruction.getNumOperands(); i++) {
				if (i > 0) {
					operands += ", ";
				}
				operands += instruction.getDefaultOperandRepresentation(i);
			}
			out.add(String.format(
				"%s  %-14s %s",
				fmt(instruction.getAddress()),
				instruction.getMnemonicString(),
				operands));
			instruction = instruction.getNext();
		}
		return out;
	}

	private void writeSummaryJson(File path, int functionCount, int interestingStringCount)
			throws Exception {
		PrintWriter out = new PrintWriter(new FileWriter(path));
		try {
			out.println("{");
			out.println("  \"program\": " + json(currentProgram.getName()) + ",");
			out.println("  \"language\": " + json(currentProgram.getLanguageID().getIdAsString()) + ",");
			out.println("  \"compiler\": " + json(currentProgram.getCompilerSpec().getCompilerSpecID().getIdAsString()) + ",");
			out.println("  \"image_base\": " + json(fmt(currentProgram.getImageBase())) + ",");
			out.println("  \"min_address\": " + json(fmt(currentProgram.getMinAddress())) + ",");
			out.println("  \"max_address\": " + json(fmt(currentProgram.getMaxAddress())) + ",");
			out.println("  \"function_count\": " + functionCount + ",");
			out.println("  \"interesting_string_count\": " + interestingStringCount);
			out.println("}");
		}
		finally {
			out.close();
		}
	}

	private void writeLines(File path, List<String> lines) throws Exception {
		PrintWriter out = new PrintWriter(new FileWriter(path));
		try {
			for (String line : lines) {
				out.println(line);
			}
		}
		finally {
			out.close();
		}
	}

	private String join(List<String> values, String separator) {
		StringBuilder builder = new StringBuilder();
		for (String value : values) {
			if (builder.length() > 0) {
				builder.append(separator);
			}
			builder.append(value);
		}
		return builder.toString();
	}

	private String json(String value) {
		StringBuilder builder = new StringBuilder();
		builder.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '\\':
				builder.append("\\\\");
				break;
			case '"':
				builder.append("\\\"");
				break;
			case '\n':
				builder.append("\\n");
				break;
			case '\r':
				builder.append("\\r");
				break;
			case '\t':
				builder.append("\\t");
				break;
			default:
				builder.append(c);
				break;
			}
		}
		builder.append('"');
		return builder.toString();
	}
}
