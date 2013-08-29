import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;

import com.googlecode.loveemu.PetiteMM.Midi2MML;

public class PetiteMM {

	/**
	 * Name of the tool.
	 */
	public final static String NAME = "PetiteMM";

	/**
	 * Version of the tool.
	 */
	public final static String VERSION = "2013-08-28";

	/**
	 * Author of the tool.
	 */
	public final static String AUTHOR = "loveemu, gocha";

	/**
	 * Website of the tool.
	 */
	public final static String WEBSITE = "http://loveemu.googlecode.com/";

	/**
	 * Removes the extension from a filename.
	 * @param filename the filename to query, null returns null
	 * @return the filename minus the extension
	 */
	public static String removeExtension(String filename)
	{
		if (filename == null)
			return null;

		int extensionIndex = filename.lastIndexOf(".");
		if (extensionIndex == -1)
			return filename;

		String separator = System.getProperty("file.separator");
		int lastSeparatorIndex = filename.lastIndexOf(separator);
		if (extensionIndex > lastSeparatorIndex)
			return filename.substring(0, extensionIndex);
		else
			return filename;
	}

	/**
	 * Convert the given MIDI file into MML.
	 * @param args Parameters, specify the empty array for details.
	 */
	public static void main(String[] args)
	{
		boolean showAbout = false;
		Midi2MML opt = new Midi2MML();

		// list of available option switches
		final String[] argsAvail = {
				"--dots", "<count>", "Maximum dot counts allowed for dotted-note.",
				"--octave-reverse", "", "Swap the octave symbol.",
				"--use-triplet", "", "Use triplet if possible.",
		};

		int argi = 0;

		//args = new String[] { "--dots", "2", "--use-triplet", "test.mid", "test2.mid", "test3.mid" };

		// dispatch option switches
		while (argi < args.length && args[argi].startsWith("-"))
		{
			if (args[argi].equals("--dots"))
			{
				if (argi + 1 >= args.length)
				{
					throw new IllegalArgumentException("Too few arguments for " + args[argi]);
				}
				opt.setMmlMaxDotCount(Integer.parseInt(args[argi + 1]));
				argi += 1;
			}
			else if (args[argi].equals("--octave-reverse"))
			{
				opt.setOctaveReversed(true);
			}
			else if (args[argi].equals("--use-triplet"))
			{
				opt.setTripletPreference(true);
			}
			argi++;
		}

		// show about the program and exit, if needed
		if (argi >= args.length || showAbout)
		{
			System.out.println(PetiteMM.NAME + " " + VERSION + " by " + AUTHOR);
			System.out.println(WEBSITE);
			System.out.println();

			if (argsAvail.length > 0)
				System.out.println("Options:");
			for (int i = 0; i < argsAvail.length / 3; i++)
			{
				System.out.format("%-17s %-8s %s\n", argsAvail[i * 3], argsAvail[i * 3 + 1], argsAvail[i * 3 + 2]);
			}

			System.exit(1);
		}

		// convert the all given file(s)
		for (; argi < args.length; argi++)
		{
			File midiFile = new File(args[argi]);
			File mmlFile = new File(PetiteMM.removeExtension(args[argi]) + ".txt");

			Midi2MML converter = new Midi2MML(opt);
			BufferedWriter writer = null;
			try {
				writer = new BufferedWriter(new FileWriter(mmlFile));
				converter.writeMML(MidiSystem.getSequence(midiFile), writer);
				writer.flush();
			} catch (InvalidMidiDataException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (writer != null)
				{
					try {
						writer.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

}