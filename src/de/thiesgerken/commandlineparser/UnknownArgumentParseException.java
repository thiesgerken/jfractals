package de.thiesgerken.commandlineparser;

public class UnknownArgumentParseException extends ParseException {

	private static final long serialVersionUID = -5168358028220929497L;

	public UnknownArgumentParseException() {
	}

	public UnknownArgumentParseException(String message) {
		super(message);
	}

}
