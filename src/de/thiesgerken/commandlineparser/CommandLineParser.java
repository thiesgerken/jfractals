package de.thiesgerken.commandlineparser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public class CommandLineParser {

	private TreeMap<Command, ArrayList<Argument>> commands;
	private ArrayList<Argument> generalArguments;

	private Command parsedCommand;
	private ArrayList<Argument> availableArguments;

	public CommandLineParser() {
		commands = new TreeMap<Command, ArrayList<Argument>>(new Comparator<Command>() {
			@Override
			public int compare(Command o1, Command o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});

		generalArguments = new ArrayList<Argument>();
	}

	public void addGeneralArgument(Argument arg) {
		if (arg == null)
			return;

		generalArguments.add(arg);
	}

	public void putCommand(Command command, Argument[] extraArguments) {
		if (command == null)
			return;

		ArrayList<Argument> args = new ArrayList<Argument>();

		if (extraArguments != null)
			for (Argument arg : extraArguments)
				args.add(arg);

		commands.put(command, args);
	}

	public ArrayList<Argument> getArguments(Command command) {
		if (command == null)
			return generalArguments;

		return commands.containsKey(command) ? commands.get(command) : null;
	}

	private static String[] split(String cmd) {
		ArrayList<String> args = new ArrayList<String>();

		boolean inQuote = false;
		String currentPart = new String();

		for (char c : cmd.toCharArray()) {
			if (c == ' ' && !inQuote) {
				if (!currentPart.isEmpty()) {
					args.add(currentPart);
					currentPart = new String();
				}
			} else if (c == '"')
				inQuote = !inQuote;
			else
				currentPart += c;
		}

		if (!currentPart.isEmpty())
			args.add(currentPart);

		String[] result = new String[args.size()];

		for (int i = 0; i < args.size(); i++)
			result[i] = args.get(i);

		return result;
	}

	public String listCommands(int width) {
		TreeMap<String, String> data = new TreeMap<String, String>();

		for (Command cmd : commands.keySet())
			data.put(cmd.getName(), cmd.getDescription());

		return printTable(data, width, -1, 0);
	}

	public String listArguments(int width, Command command) {
		ArrayList<Argument> args = null;

		if (command == null)
			args = generalArguments;
		else if (commands.containsKey(command))
			args = commands.get(command);
		else
			return "";

		if (args.size() == 0)
			return "  (none)";

		TreeMap<String, String> data = new TreeMap<String, String>();

		for (Argument arg : args)
			data.put(arg.getUsageString(), arg.getDescription());

		return printTable(data, width, 6, 0);
	}

	public static String printTable(Map<String, String> data, int width, int indentation, int globalIndentation) {
		StringBuilder sb = new StringBuilder();

		int maxLength = 0;

		for (String heading : data.keySet())
			maxLength = Math.max(maxLength, heading.length() + globalIndentation + 1);

		boolean first = true;

		for (String heading : data.keySet()) {
			if (!first)
				sb.append("\n");
			else
				first = false;

			StringBuilder lineBuilder = new StringBuilder();

			for (int i = 0; i < globalIndentation; i++)
				lineBuilder.append(" ");

			lineBuilder.append(heading);

			for (int i = lineBuilder.length(); i < maxLength; i++)
				lineBuilder.append(" ");

			lineBuilder.append(data.get(heading));

			printFormatted(sb, lineBuilder.toString(), width, indentation < 0 ? maxLength : indentation, globalIndentation);
		}

		return sb.toString();
	}

	private static void printFormatted(StringBuilder sb, String text, int width, int indentation, int globalIndentation) {
		if (indentation > width)
			return;

		sb.append(text.substring(0, Math.min(text.length(), width)));

		if (text.length() <= width)
			return;

		String remainingText = text.substring(width);
		int realWidth = width - indentation - globalIndentation;

		for (int i = 0; i < Math.ceil(remainingText.length()) / realWidth; i++) {
			sb.append("\n");

			for (int k = 0; k < indentation + globalIndentation; k++)
				sb.append(" ");

			sb.append(remainingText.substring(i * realWidth, Math.min(remainingText.length(), (i + 1) * realWidth)));
		}
	}

	public void parse(String args) throws ParseException {
		parse(split(args));
	}

	public void parse(String[] args) throws ParseException {
		if (args.length == 0)
			throw new MissingCommandParseException("No command specified.");

		for (Argument arg : generalArguments)
			arg.setWasParsed(false);

		parsedCommand = null;

		for (Command cmd : commands.keySet()) {
			if (args[0].equals(cmd.getName())) {
				parsedCommand = cmd;
				cmd.setWasParsed(true);
			} else
				cmd.setWasParsed(false);

			for (Argument arg : commands.get(cmd))
				arg.setWasParsed(false);
		}

		if (parsedCommand == null)
			throw new MissingCommandParseException("'" + args[0] + "' is not a valid command.");

		availableArguments = new ArrayList<Argument>();
		availableArguments.addAll(generalArguments);
		availableArguments.addAll(commands.get(parsedCommand));

		Argument currentArgument = null;

		for (int i = 1; i < args.length; i++) {
			boolean startsWithNeg = (args[i].length() >= 2 && args[i].charAt(1) >= '0' && args[i].charAt(1) <= '9');

			if (currentArgument != null && args[i].startsWith("-") && !startsWithNeg) {
				// no value for last argument

				currentArgument.setWasParsed(true);
				currentArgument.parse("");
				currentArgument = null;
			}

			if (currentArgument == null && !args[i].startsWith("-"))
				throw new UnexpectedValueParseException("Expected an argument, not the value '" + args[i] + "'.");

			if (args[i].startsWith("-") && !startsWithNeg) {
				// this must be an argument name

				boolean useLongName = args[i].startsWith("--");

				for (Argument arg : availableArguments)
					if (useLongName && args[i].equals("--" + arg.getLongName()) && !arg.getLongName().isEmpty() || !useLongName
							&& args[i].equals("-" + arg.getShortName()) && !arg.getShortName().isEmpty())
						currentArgument = arg;

				if (currentArgument == null)
					throw new UnknownArgumentParseException("'" + args[i] + "' is not a valid argument");

				if (i == args.length - 1) {
					currentArgument.setWasParsed(true);
					currentArgument.parse("");
					currentArgument = null;

				}
			} else {
				// this is the value for the current argument

				currentArgument.setWasParsed(true);
				currentArgument.parse(args[i].trim());
				currentArgument = null;
			}
		}

		for (Argument arg : availableArguments)
			if (arg.isRequired() && !arg.wasParsed())
				throw new MissingArgumentParseException("The required argument '" + arg.getLongName() + "' is missing.");

	}
}
