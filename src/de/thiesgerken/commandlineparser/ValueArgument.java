package de.thiesgerken.commandlineparser;

public abstract class ValueArgument<T> extends Argument {

	private T value;

	public ValueArgument(String longName) {
		super(longName);
	}

	public ValueArgument(String longName, String shortName, boolean isRequired, String description) {
		super(longName, shortName, isRequired, description);
	}

	@Override
	public String getSampleUsage() {
		return "[value]";
	}

	protected abstract T convert(String value) throws ParseException;

	@Override
	public void parse(String value) throws ParseException {
		this.value = convert(value);
	}

	public T getValue() {
		return value;
	}
}
