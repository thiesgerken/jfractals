package de.thiesgerken.commandlineparser;

public abstract class Argument {

	private String longName;
	private String shortName;
	private String description;
	private boolean isRequired;

	private boolean wasParsed;

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getLongName() {
		return longName;
	}

	public void setLongName(String longName) {
		this.longName = longName;
	}

	public Argument(String longName) {
		this.longName = longName;
		this.description = "";
		this.shortName = "";
		this.isRequired = false;
	}

	public Argument(String longName, String shortName, Boolean isRequired, String description) {
		this(longName);
		this.shortName = shortName;
		this.isRequired = isRequired;
		this.description = description;
	}

	public boolean isRequired() {
		return isRequired;
	}

	public void setRequired(boolean isRequired) {
		this.isRequired = isRequired;
	}

	public abstract String getSampleUsage();

	public abstract void parse(String value) throws ParseException;

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Boolean wasParsed() {
		return wasParsed;
	}

	public void setWasParsed(Boolean wasParsed) {
		this.wasParsed = wasParsed;
	}

	protected String getUsageString() {
		StringBuilder sb = new StringBuilder();

		sb.append("--" + longName);

		if (!shortName.isEmpty())
			sb.append(", -" + shortName);

		if (getSampleUsage().length() != 0)
			sb.append(" " + getSampleUsage());

		return sb.toString();
	}

}
