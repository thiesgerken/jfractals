package de.thiesgerken.commandlineparser;

public class Command {
	private String name;
	private String description;
	private boolean wasParsed;

	public Command(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean wasParsed() {
		return wasParsed;
	}

	public void setWasParsed(boolean wasParsed) {
		this.wasParsed = wasParsed;
	}

}
