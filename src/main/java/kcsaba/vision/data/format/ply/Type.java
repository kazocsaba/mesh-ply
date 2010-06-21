package kcsaba.vision.data.format.ply;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 *
 * @author Kazó Csaba
 */
public enum Type {
	CHAR {
		@Override
		public Number parse(Scanner scanner) throws IOException {
			try {
				int value=scanner.nextInt();
				if (value<-Byte.MIN_VALUE || value>Byte.MAX_VALUE) throw new InputMismatchException("Char out of range");
				return value;
			} catch (InputMismatchException e) {
				throw new InvalidPlyFormatException("Cannot parse '"+scanner.next()+"' as char", e);
			} catch (NoSuchElementException e) {
				throw new InvalidPlyFormatException("Unexpected end of file", e);
			}
		}
		@Override
		public Number read(ByteBuffer buffer) throws IOException {
			return buffer.get();
		}
	},
	UCHAR {
		@Override
		public Number parse(Scanner scanner) throws IOException {
			try {
				int value=scanner.nextInt();
				if (value<0 || value>255) throw new InputMismatchException("Uchar out of range: "+value);
				return value;
			} catch (InputMismatchException e) {
				throw new InvalidPlyFormatException("Cannot parse '"+scanner.next()+"' as uchar", e);
			} catch (NoSuchElementException e) {
				throw new InvalidPlyFormatException("Unexpected end of file", e);
			}
		}
		@Override
		public Number read(ByteBuffer buffer) throws IOException {
			return buffer.get() & 0xFF;
		}
	},
	SHORT {
		@Override
		public Number parse(Scanner scanner) throws IOException {
			try {
				int value=scanner.nextInt();
				if (value<-Short.MIN_VALUE || value>Short.MAX_VALUE) throw new InputMismatchException("Short out of range");
				return value;
			} catch (InputMismatchException e) {
				throw new InvalidPlyFormatException("Cannot parse '"+scanner.next()+"' as short", e);
			} catch (NoSuchElementException e) {
				throw new InvalidPlyFormatException("Unexpected end of file", e);
			}
		}
		@Override
		public Number read(ByteBuffer buffer) throws IOException {
			return buffer.getShort();
		}
	},
	USHORT {
		@Override
		public Number parse(Scanner scanner) throws IOException {
			try {
				int value=scanner.nextInt();
				if (value<0 || value>65535) throw new InputMismatchException("Ushort out of range");
				return value;
			} catch (InputMismatchException e) {
				throw new InvalidPlyFormatException("Cannot parse '"+scanner.next()+"' as ushort", e);
			} catch (NoSuchElementException e) {
				throw new InvalidPlyFormatException("Unexpected end of file", e);
			}
		}

		@Override
		public Number read(ByteBuffer buffer) throws IOException {
			return buffer.getShort() & 0xFFFF;
		}
	},
	INT {
		@Override
		public Number parse(Scanner scanner) throws IOException {
			try {
				return scanner.nextInt();
			} catch (InputMismatchException e) {
				throw new InvalidPlyFormatException("Cannot parse '"+scanner.next()+"' as int", e);
			} catch (NoSuchElementException e) {
				throw new InvalidPlyFormatException("Unexpected end of file", e);
			}
		}

		@Override
		public Number read(ByteBuffer buffer) throws IOException {
			return buffer.getInt();
		}
	},
	UINT {
		@Override
		public Number parse(Scanner scanner) throws IOException {
			try {
				long value=scanner.nextLong();
				if (value<0 || value>4294967295L) throw new InputMismatchException("Uint out of range");
				return value;
			} catch (InputMismatchException e) {
				throw new InvalidPlyFormatException("Cannot parse '"+scanner.next()+"' as uint", e);
			} catch (NoSuchElementException e) {
				throw new InvalidPlyFormatException("Unexpected end of file", e);
			}
		}

		@Override
		public Number read(ByteBuffer buffer) throws IOException {
			return buffer.getInt() & 0xFFFFFFFFL;
		}
	},
	FLOAT {
		@Override
		public Number parse(Scanner scanner) throws IOException {
			try {
				return scanner.nextFloat();
			} catch (InputMismatchException e) {
				throw new InvalidPlyFormatException("Cannot parse '"+scanner.next()+"' as float", e);
			} catch (NoSuchElementException e) {
				throw new InvalidPlyFormatException("Unexpected end of file", e);
			}
		}

		@Override
		public Number read(ByteBuffer buffer) throws IOException {
			return buffer.getFloat();
		}
	},
	DOUBLE {
		@Override
		public Number parse(Scanner scanner) throws IOException {
			try {
				return scanner.nextDouble();
			} catch (InputMismatchException e) {
				throw new InvalidPlyFormatException("Cannot parse '"+scanner.next()+"' as double", e);
			} catch (NoSuchElementException e) {
				throw new InvalidPlyFormatException("Unexpected end of file", e);
			}
		}

		@Override
		public Number read(ByteBuffer buffer) throws IOException {
			return buffer.getDouble();
		}

	};
	public abstract Number parse(Scanner scanner) throws IOException;
	public abstract Number read(ByteBuffer buffer) throws IOException;
}
