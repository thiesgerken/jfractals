package de.thiesgerken.commandlineparser;

public class MissingCommandParseException extends ParseException {

	private static final long serialVersionUID = 800071581731327226L;

	public MissingCommandParseException() {
	}

	public MissingCommandParseException(String message) {
		super(message);
	}

}
