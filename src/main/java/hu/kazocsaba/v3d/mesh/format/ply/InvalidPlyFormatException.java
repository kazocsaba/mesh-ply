package hu.kazocsaba.v3d.mesh.format.ply;

import java.io.IOException;

/**
 * Thrown to indicate that a PLY file cannot be read because its format is invalid or
 * unrecognized.
 * @author Kazó Csaba
 */
public class InvalidPlyFormatException extends IOException {
	/**
	 * Constructs an InvalidPlyFormatException with the specified
	 * detail message and cause.
	 *
	 * @param   message   the detail message. The detail message is saved for
	 *          later retrieval by the {@link Throwable#getMessage()} method.
	 * @param  cause the cause (which is saved for later retrieval by the
	 *         {@link Throwable#getCause()} method).
	 */
	public InvalidPlyFormatException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs an InvalidPlyFormatException with the specified
	 * cause.
	 *
	 * @param  cause the cause (which is saved for later retrieval by the
	 *         {@link Throwable#getCause()} method).
	 */
	public InvalidPlyFormatException(Throwable cause) {
		super(cause == null ? null : cause.toString());
		this.initCause(cause);
	}

	/**
	 * Constructs an InvalidPlyFormatException with the specified
	 * detail message.
	 *
	 * @param   message   the detail message. The detail message is saved for
	 *          later retrieval by the {@link Throwable#getMessage()} method.
	 */
	public InvalidPlyFormatException(String message) {
		super(message);
	}
}
