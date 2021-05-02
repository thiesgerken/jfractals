package de.thiesgerken.commandlineparser.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import de.thiesgerken.commandlineparser.Argument;
import de.thiesgerken.commandlineparser.Command;
import de.thiesgerken.commandlineparser.CommandLineParser;
import de.thiesgerken.commandlineparser.EnumArgument;
import de.thiesgerken.commandlineparser.MissingArgumentParseException;
import de.thiesgerken.commandlineparser.MissingCommandParseException;
import de.thiesgerken.commandlineparser.ParseException;
import de.thiesgerken.commandlineparser.SwitchArgument;
import de.thiesgerken.commandlineparser.UnexpectedValueParseException;
import de.thiesgerken.commandlineparser.ValueArgument;

public class ParserTest {
	private CommandLineParser parser;
	private Command helpCommand;
	private Command sampleCommand;
	private SwitchArgument generalArgument;
	private ValueArgument<Double> valueArgument;
	private EnumArgument deviceArgument;

	@Before
	public void init() {
		parser = new CommandLineParser();

		helpCommand = new Command("help", "displays help on how to use this application");
		sampleCommand = new Command("dosomething",
				"does something which is really important. Now follows a loooooong text to test the printing of command listings. Lorem Ipsum blabla foobar");
		generalArgument = new SwitchArgument(
				"general",
				"g",
				false,
				"a general argument. controls absolutely nothing and is very optional. I would not use it at all since it has no function, although that certainly is your choice.");
		valueArgument = new ValueArgument<Double>("value", "", true, "required. sets a value (double)") {
			@Override
			protected Double convert(String value) {
				return Double.parseDouble(value);
			}
		};
		deviceArgument = new EnumArgument("device", "d", false, "controls the device which is used for calculation", new String[] { "dev-1", "dev-2" });
		parser.addGeneralArgument(generalArgument);
		parser.putCommand(helpCommand, null);
		parser.putCommand(sampleCommand, new Argument[] { valueArgument, deviceArgument });
	}

	@Test(expected = MissingCommandParseException.class)
	public void testParse_01() throws ParseException {
		parser.parse("");
	}

	@Test
	public void testParse_02() throws ParseException {
		parser.parse("help");

		assertTrue(helpCommand.wasParsed());
		assertFalse(sampleCommand.wasParsed());
		assertFalse(generalArgument.wasParsed());
		assertFalse(valueArgument.wasParsed());
		assertFalse(deviceArgument.wasParsed());
	}

	@Test
	public void testParse_03() throws ParseException {
		parser.parse("help --general");

		assertTrue(helpCommand.wasParsed());
		assertFalse(sampleCommand.wasParsed());
		assertTrue(generalArgument.wasParsed());
		assertFalse(valueArgument.wasParsed());
		assertFalse(deviceArgument.wasParsed());
	}

	@Test
	public void testParse_04() throws ParseException {
		parser.parse("help -g");

		assertTrue(helpCommand.wasParsed());
		assertFalse(sampleCommand.wasParsed());
		assertTrue(generalArgument.wasParsed());
		assertFalse(valueArgument.wasParsed());
		assertFalse(deviceArgument.wasParsed());
	}

	@Test(expected = ParseException.class)
	public void testParse_05() throws ParseException {
		parser.parse("help -g val");
	}

	@Test(expected = UnexpectedValueParseException.class)
	public void testParse_06() throws ParseException {
		parser.parse("help val");
	}

	@Test(expected = MissingArgumentParseException.class)
	public void testParse_07() throws ParseException {
		parser.parse("dosomething --general");
	}

	@Test
	public void testParse_08() throws ParseException {
		parser.parse("dosomething --value 3.14");

		assertFalse(helpCommand.wasParsed());
		assertTrue(sampleCommand.wasParsed());
		assertFalse(generalArgument.wasParsed());
		assertTrue(valueArgument.wasParsed());
		assertFalse(deviceArgument.wasParsed());

		assertEquals(new Double(3.14), valueArgument.getValue());
	}

	@Test
	public void testParse_09() throws ParseException {
		parser.parse("dosomething --value 3.14 -d dev-2");

		assertFalse(helpCommand.wasParsed());
		assertTrue(sampleCommand.wasParsed());
		assertFalse(generalArgument.wasParsed());
		assertTrue(valueArgument.wasParsed());
		assertTrue(deviceArgument.wasParsed());

		assertEquals(new Double(3.14), valueArgument.getValue());
		assertEquals("dev-2", deviceArgument.getValue());
	}

	@Test(expected = ParseException.class)
	public void testParse_10() throws ParseException {
		parser.parse("dosomething --value 3.14 -d dev-22");
	}

	@Test(expected = ParseException.class)
	public void testParse_11() throws ParseException {
		parser.parse("dosomething --value 3.14 -d Dev-2");
	}

	@Test(expected = UnexpectedValueParseException.class)
	public void testParse_12() throws ParseException {
		parser.parse("dosomething --value 3.14 -d dev-2 2");
	}

}
