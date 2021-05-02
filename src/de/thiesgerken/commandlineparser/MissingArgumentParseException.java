package de.thiesgerken.commandlineparser;

public class MissingArgumentParseException extends ParseException {

	private static final long serialVersionUID = -4936894900264158739L;

	public MissingArgumentParseException() {
	}

	public MissingArgumentParseException(String message) {
		super(message);
	}

}
