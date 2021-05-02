package de.thiesgerken.commandlineparser;

public class SwitchArgument extends Argument {

	public SwitchArgument(String longName) {
		super(longName);
	}

	public SwitchArgument(String longName, String shortName, boolean isRequired, String description) {
		super(longName, shortName, isRequired, description);
	}

	@Override
	public String getSampleUsage() {
		return new String();
	}

	@Override
	public void parse(String value) throws ParseException {
		if (!value.isEmpty())
			throw new ParseException("Switches do not take any values.");
	}

}
