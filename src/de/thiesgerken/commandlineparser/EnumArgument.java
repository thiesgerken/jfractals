package de.thiesgerken.commandlineparser;

import java.util.ArrayList;
import java.util.List;

public class EnumArgument extends Argument {

	private ArrayList<String> possibleValues;
	private String value;

	public String getValue() {
		return value;
	}

	public EnumArgument(String longName, String[] possibleValues) {
		super(longName);
		this.possibleValues = new ArrayList<String>();

		for (String s : possibleValues)
			this.possibleValues.add(s);
	}

	public EnumArgument(String longName, List<String> possibleValues) {
		super(longName);
		this.possibleValues = new ArrayList<String>();

		for (String s : possibleValues)
			this.possibleValues.add(s);
	}

	public EnumArgument(String longName, String shortName, boolean isRequired, String description, String[] possibleValues) {
		super(longName, shortName, isRequired, description);
		this.possibleValues = new ArrayList<String>();

		for (String s : possibleValues)
			this.possibleValues.add(s);
	}

	public EnumArgument(String longName, String shortName, boolean isRequired, String description, List<String> possibleValues) {
		super(longName, shortName, isRequired, description);
		this.possibleValues = new ArrayList<String>();

		for (String s : possibleValues)
			this.possibleValues.add(s);
	}

	@Override
	public String getSampleUsage() {
		String usage = "[";

		for (String s : possibleValues) {
			if (usage.length() != 1)
				usage += "|";

			usage += s;
		}

		return usage + "]";
	}

	@Override
	public void parse(String value) throws ParseException {
		for (String s : possibleValues) {
			if (s.equals(value)) {
				this.value = s;
				return;
			}
		}

		throw new ParseException("\"" + value + "\" is not a valid value.");
	}

	public ArrayList<String> getPossibleValues() {
		return possibleValues;
	}
}
