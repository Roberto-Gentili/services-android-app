package org.rg.util;

public class Hex {

	private static final char[] DIGITS_LOWER = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private static final char[] DIGITS_UPPER = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };


	public static String encode(final byte[] data, final boolean toLowerCase) {
		return new String(encode(data, toLowerCase ? DIGITS_LOWER : DIGITS_UPPER));
	}

	private static char[] encode(final byte[] data, final char[] toDigits) {
		final int l = data.length;
		final char[] out = new char[l << 1];
		encode(data, 0, data.length, toDigits, out, 0);
		return out;
	}

	private static void encode(final byte[] data, final int dataOffset, final int dataLen, final char[] toDigits, final char[] out, final int outOffset) {
		for (int i = dataOffset, j = outOffset; i < dataOffset + dataLen; i++) {
			out[j++] = toDigits[(0xF0 & data[i]) >>> 4];
			out[j++] = toDigits[0x0F & data[i]];
		}
	}

}
