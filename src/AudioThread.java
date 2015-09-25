// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

/*
 * AudioThread - a cleaned-up and reusable chunk of Java audio code. 
 * As is, it will open the sound system to play 4-byte-per-sample 
 * stereo little-endian PCM data at 44.1khz -- your basic CD quality
 * sound. Just fill run()'s local_buffer byte array with data
 * before the call to sdl.write() in the  while (quit == false)" loop.
 *
 * 
 * By Bret Logan (c) 2007
 */
//*************************************
//*************************************
import javax.sound.sampled.*;
//import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
//import java.io.File;

//*************************************
class AudioThread implements Runnable {//remember to un-pause!
	AudioFormat af;
	boolean quit = false;
	boolean pause = true;
	Thread t;
	BinauralBeatSoundEngine BB;
	boolean ThreadIsAlive = false;

	//*************************************
	AudioThread(BinauralBeatSoundEngine theBB) {
		BB = theBB;
		System.out.println("AudioThread: Starting");
		t = new Thread(this);
		t.start();
		//now that the thread is started, this returns
	}

	//*************************************
	public void Cleanup() {
		//all this cleanup might not be necessary in Java; kept more to remind when re-porting back to C
		//BB.BB_CleanupVoices();
	}

	//*************************************
	public void run() {
		ThreadIsAlive = true;
		int local_buffsize = 1024 << 2;//must always be multiple of 4, since 4 bytes are used for each stereo sample
		byte [] local_buffer = new byte[local_buffsize + 4];
		//int sound_buffsize = local_buffsize;

		//====================
		//set up your sound processing class here, then put the
		//"callback" down in the while-loop below, and any cleanup needed for
		//sound processing in Cleanup():
		//    BB = new BinauralBeatSoundEngine(2);
		//    MyScheduleXML = new ScheduleXML(schedule_filename, BB);
		//    bNewFile = true;//This just alerts user to update stuff; user must set this false after done
		//note: next line is done in ScheduleXML:
		//int vc; for (vc = 0; vc < BB.BB_VoiceCount; vc++) { BB.BB_CalibrateVoice(vc); }
		//====================

		//set up your basic CD-quality sound format (stereo 44100hz):
		af = new AudioFormat(
				(javax.sound.sampled.AudioFormat.Encoding)AudioFormat.Encoding.PCM_SIGNED,//encoding
				(float)44100.0,//sampleRate
				16,//sampleSizeInBits
				2, //channels
				4,//frameSize
				(float)44100.0,//frameRate
				false);//bigEndian

		/*
            //this is the equivalent of above, except in the simpler constructor form:
            AudioFormat af = new AudioFormat((float)44100.0,//sampleRate
                                              16,//sampleSizeInBits,
                                              2,//channels,
                                              true,//signed,
                                              false);// bigEndian
		 */

		/*
    //==START original way to open a SDL
            SourceDataLine sdl = null;
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
            try {
                //grab a line to send the audio data out on:
                sdl = (SourceDataLine) AudioSystem.getLine(info);
                if (buffsize < 1) sdl.open(af);
                else sdl.open(af, buffsize);
            } catch (LineUnavailableException e) {
                e.printStackTrace();
                System.exit(1);
            } catch (SecurityException e) {
                e.printStackTrace();
                System.exit(1);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
    //==END original way to open SDL
		 */

		//==START new way to open a SDL -- since 1.5. See:
		// http://java.sun.com/j2se/1.5.0/docs/api/javax/sound/sampled/AudioSystem.html
		SourceDataLine sdl = null;
		try {
			sdl = (SourceDataLine) AudioSystem.getSourceDataLine(af);
			sdl.open(af, 44100);
			//sdl.open(af);
		} catch (LineUnavailableException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (SecurityException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		sdl.start();
		//==END new way to open SDL

		//this is the main loop of AudioThread; it only quits when done with all Audio:
		while (quit == false) {

			//====================
			//Now fill the local buffer so I have something to write to the SDL:
			if (pause == false) {
				//if (sdl.isRunning() == false) { sdl.start();  }
				BB.BB_MainLoop(local_buffer, local_buffsize);
			} else {
				int i = local_buffsize;
				while ( (--i) != 0) local_buffer[i] = 0;
			}
			//====================

			if (((BB.BB_InfoFlag) & BinauralBeatSoundEngine.BB_COMPLETED) != 0) {
				//        gtk_label_set_text (LabelProgramStatus, "Schedule Completed");
				pause = true;       //so bbl_OnButton_Play() thinks I was running
				//main_OnButton_Play (); // I'm simulating user button push to pause
				//   main_UpdateGUI_Status ("Schedule Completed");
				//   bb->loopcount = bb->loops;
				//   gtk_progress_bar_set_fraction (ProgressBar_Overall, 1.0);
			}


			/*
      //Here is an example of how to fill the local_buffer, inc. byte ordering
      //for little-endian audio:
      int j=0;
        while (j < local_buffsize) {
                  double angle = waveform_index++ / (44100.0 / hz) * 2.0 * Math.PI;
         //do right channel:
         short tmpwaveform = (short)(Math.sin(angle) * 0xFFF);
                  local_buffer[j] = (byte) (tmpwaveform & 0xFF);
         ++j;
                  local_buffer[j] = (byte) (tmpwaveform >> 8);
         ++j;
         //do left channel:
                  tmpwaveform = (short)(Math.sin(angle * 1.01) * 0xFFF);
                  local_buffer[j] = (byte) (tmpwaveform & 0xFF);
         ++j;
                  local_buffer[j] = (byte) (tmpwaveform >> 8);
         ++j;
        }
			 */

			//deal with pause/play:
			//NOTE: this words, but for some reason makes CPU processing go up INCREDIBLY
			//if (pause == true && sdl.isRunning() == true) sdl.stop();
			//else if (pause == false && sdl.isRunning() == false) sdl.start();

			//now send the buffer out to SDL:
			sdl.write(local_buffer, 0, local_buffsize);
		}

		sdl.drain();
		sdl.stop();
		sdl.close();
		Cleanup();
		ThreadIsAlive = false; //signals to user that thread is done.
	}//end run()



	public final static int swabInt(int v) {
		return  (v >>> 24) | (v << 24) |
		((v << 8) & 0x00FF0000) | ((v >> 8) & 0x0000FF00);
	}

	////////////////////////////
	//NOTES: JavaVM is always little-endian; WAV is always (as Intel) big-endian
	public void AT_WriteWAVHeader(String filename) {
		//WAVheader_part1 is 4 bytes long:
		byte WAVheader_part1[]={'R','I','F','F'};
		//WAVheader_part2 is 32 bytes long:
		byte WAVheader_part2[]={87,65,86,69,102,109,116,32,16,0,0,0,1,0,2,0,68,-84,0,0,16,-79,2,0,4,0,16,0,100,97,116,97};
		long BB_FileByteCount = ((int)(BB.BB_Loops * BB.BB_TotalDuration)) * 176400;
		byte unsignedintholder[]={0,0,0,0};

		//Brute force approach to writing WAV files here, as follows:
		//1) write WAVheader_part1 to disk
		//2) write value (BB_FileByteCount + 36) to disk as an unsigned int in little-endian order
		//3) write WAVheader_part2 to disk
		//4) write value (BB_FileByteCount) to disk as an unsigned int in little-endian order
		//5) write all the data to disk, stopping when BB_FileByteCount bytes are written
		System.out.println("Total number of bytes to write: "+BB_FileByteCount);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(filename);
		} catch (Exception e) {
			System.out.println(e);
		}
		try {
			fos.write(WAVheader_part1);
			//must write the length in little-endian:
			unsignedintholder[3]=(byte)(((BB_FileByteCount+36)>>24)&0xff);
			unsignedintholder[2]=(byte)(((BB_FileByteCount+36)>>16)&0xff);
			unsignedintholder[1]=(byte)(((BB_FileByteCount+36)>>8)&0xff);
			unsignedintholder[0]=(byte)(((BB_FileByteCount+36)>>0)&0xff);
			fos.write(unsignedintholder, 0, 4);
			fos.write(WAVheader_part2);
			//must write the length in little-endian:
			unsignedintholder[3]=(byte)(((BB_FileByteCount)>>24)&0xff);
			unsignedintholder[2]=(byte)(((BB_FileByteCount)>>16)&0xff);
			unsignedintholder[1]=(byte)(((BB_FileByteCount)>>8)&0xff);
			unsignedintholder[0]=(byte)(((BB_FileByteCount)>>0)&0xff);
			fos.write(unsignedintholder, 0, 4);
			fos.close();
		} catch (Exception e) {
			System.out.println(e);
		}

		/*
    //here is some WAV file writing experiment (works, but so-what):
      byte[] myByteSoundArray;
      myByteSoundArray = new byte[4];
      long mySampleFrameLength = 1;
      AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(myByteSoundArray),af,mySampleFrameLength);
      try {
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(filename));
      } catch (Exception e) {
        System.out.println(e);
      }
		 */
	}

}//end class AudioThread
