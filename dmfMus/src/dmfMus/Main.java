package dmfMus;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.LinkedList;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;

/**
 * DMF to MIDI converter by loveemu
 * Based on GbaMusRiper source (c) 2012 by Bregalad
 * This is free and open source software
 */

public class Main
{
    final static byte[] DMF_SIGNATURE = { 'D', 'M', 'F', 0 };

    //Output MIDI file
    static DataOutputStream outMID;
    //Input sequence file
    static RandomAccessFile inSEQ;

    //Base of input filename
    static String inBaseName;

    //Number of tracks
    static int num_tracks;
    //Pointers to multiple track data
    static int[] track_ptr;
    //Counters until next event comes on a track
    static int[] counter;
    //True if a note is currently playing
    static boolean[] note_flag;
    //Number of the MIDI key of the note currently playing
    static int[] current_key;

    //True if the entire track has been decoded at least once
    static boolean[] track_completed;

    //FIFO for note on events
    static LinkedList<Integer> pending_note_on_chn;
    static LinkedList<Integer> pending_note_on_key;
    static LinkedList<Integer> pending_note_on_vel;

    //Linearise volume flag (currently unused)
    static boolean lv = false;

    //Which sound engine version is used
    static DMF engine_ver;
    //Output midi stream
    static Sequence midi;

    //Current tick
    static long midiTick;

    public static void main(String[] args) throws Exception
    {
        System.out.println("DMF to MIDI ripper");

        parseArguments(args);

        byte[] signature = new byte[DMF_SIGNATURE.length];
        inSEQ.seek(0);
        inSEQ.read(signature);
        if (!Arrays.equals(signature, DMF_SIGNATURE))
        {
            System.out.println("Invalid signature");
            System.exit(-1);
        }

        //Song Header
        System.out.println();
        System.out.println("Header");
        System.out.println(String.format("0004: %1$d", inSEQ.readInt()));
        System.out.println(String.format("0008: %1$d", inSEQ.readByte()));
        num_tracks = inSEQ.readByte() & 0xff;
        System.out.println(String.format("Tracks: %1$d", num_tracks));
        int timebase = inSEQ.readShort() & 0xffff;
        System.out.println(String.format("Timebase: %1$d", timebase));

        for(int song = 0; song < 1; song++)
        {
            //Output MIDIs are in a new folder called "music"
            //File dir = new File("music");
            //dir.mkdir();

            System.out.println();
            System.out.println(String.format("Converting song..."));

            midi = new Sequence(Sequence.PPQ, timebase);
            for (int trackIndex = 0; trackIndex < num_tracks; trackIndex++)
            {
                midi.createTrack();
            }

            midi.getTracks()[0].add(MidiEventCreator.createSequenceNameEvent(0, "Converted by DMFMus"));
            midi.getTracks()[0].add(MidiEventCreator.createGMResetEvent(0));
            midi.getTracks()[0].add(MidiEventCreator.createGSResetEvent(0));

            //Track Pointers
            //Exit if there is no song to be converted anymore
            if(!engine_ver.init()) break;

            //Reset counters
            midiTick = 0;
            for(int i=0; i<counter.length; i++)
                counter[i] = 0;

            //Open output file once we know the pointer points to correct data
            //(this avoids creating blank files when there is an error)
            try
            {
                outMID = new DataOutputStream(new FileOutputStream(inBaseName + ".mid"));
            }
            catch (FileNotFoundException e)
            {
                System.out.println("Invalid output file.");
                System.exit(-1);
            }

            //This is the main loop which will process all channels
            //until they are all inactive
            int i = 100000;
            try
            {
                while(tick())
                {
                    //This is a security, if we somehow miss the end of the song,
                    //or if we are ripping garbage data, we will eventually exit the loop
                    if(i-- == 0)
                    {
                        System.out.println("Time out");
                        break;
                    }
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
                continue;
            }
            System.out.println("Dump complete. Now outputing MIDI file...");
            MidiSystem.write(midi, 1, outMID);
        }
        //Close files
        inSEQ.close();
        System.out.println(" Done !");
    }

    static boolean tick() throws IOException, InvalidMidiDataException
    {
        //Process all tracks
        for(int track = 0; track<counter.length; track++)
        {
            if (counter[track] > 0)
            {
                counter[track]--;
            }
            //Process events until counter non-null or pointer null
            //This might not be executed if counter both are non null.
            while(!track_completed[track] && counter[track] <= 0)
            {
                engine_ver.process_event(track);
            }
        }

        for(int track = 0; track<counter.length; track++)
        {
            // process portamento, fade, etc.
        }

        //Compute if all still active channels are completely decoded
        boolean all_completed_flag = true;
        for(int i = 0; i < track_ptr.length; i++)
            all_completed_flag &= track_completed[i];

        //If everything is completed, the main program should quit its loop
        if(all_completed_flag)
            return false;

        //Add pending note ons after all events are processed
        while(!pending_note_on_key.isEmpty())
        {
            int channel = pending_note_on_chn.removeFirst().intValue();
            int key = pending_note_on_key.removeFirst().intValue();
            int vel = pending_note_on_vel.removeFirst().intValue();
            midi.getTracks()[channel].add(MidiEventCreator.createNoteOnEvent(midiTick, channel, key, vel));
        }

        //Increment MIDI time
        midiTick++;
        return true;
    }

    static void parseArguments(String[] args)
    {
        if (args.length < 1)
            printInstructions();

        String game = "Hokuto";
        if(game.equalsIgnoreCase("Hokuto"))
        {
            engine_ver = new HokutoDMF();
        }
        else
        {
            //If an invalid game is given
            System.out.println("Invalid game code : " + game);
            printInstructions();
        }

        //Open the input and output files
        String path = args[0];
        try
        {
            inSEQ = new RandomAccessFile(path, "r");
        }
        catch(FileNotFoundException e)
        {
            System.out.println("Invalid input ROM file : " + path);
            System.exit(-1);
        }
        inBaseName = getBaseName(path);

        for(int i=1; i<args.length; i++)
        {
            if(args[i].equals("-lv"))
                lv = true;
            else
                printInstructions();
        }
    }

    static void printInstructions()
    {
        System.out.println("Rips sequence data from DMF to MIDI (.mid) format.");
        System.out.println("\nUsage : Main infile.dmf (options)");
        //System.out.println("Possible Games : ");
        //System.out.println("Hokuto : Hokuto no Ken - Seikimatsu Kyuuseishu Densetsu [J] [SLPS-02993]");
        System.out.println("-lv : Linearise volume and velocities. This should be used to have the output \"sound\" like the original song," +
                           "but shouldn't be used to get an exact dump of sequence data.");
        System.exit(-1);
    }

    static String getBaseName(String s)
    {
        File file = new File(s);
        return removeExtension(file.getName());
    }

    static String removeExtension(String s)
    {
        String separator = System.getProperty("file.separator");
        String filename;

        // Remove the path upto the filename.
        int lastSeparatorIndex = s.lastIndexOf(separator);
        if (lastSeparatorIndex == -1) {
            filename = s;
        } else {
            filename = s.substring(lastSeparatorIndex + 1);
        }

        // Remove the extension.
        int extensionIndex = filename.lastIndexOf(".");
        if (extensionIndex == -1)
            return filename;

        return filename.substring(0, extensionIndex);
    }
}