package de.thiesgerken.commandlineparser;

public class UnexpectedValueParseException extends ParseException {

	private static final long serialVersionUID = -4496930726615067489L;

	public UnexpectedValueParseException() {
	}

	public UnexpectedValueParseException(String message) {
		super(message);
	}

}
