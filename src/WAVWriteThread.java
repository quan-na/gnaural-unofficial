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
* WAVWriteThread - a cleaned-up and reusable chunk of Java audio code. 
* As is, it will open the sound system to play 4-byte-per-sample 
* stereo little-endian PCM data at 44.1khz -- your basic CD quality
* sound. Just fill run()'s local_buffer byte array with data
* before the call to sdl.write() in the  while (quit == false)" loop.
*
* 
* WAVWriteThread.java By Bret Logan (c) 2007
*/
//NOTE: All WAV files are assumed to be (stereo 16-bit 44100hz, framesize 4-bytes):

//*************************************
//*************************************
import java.io.FileOutputStream;

//*************************************
class WAVWriteThread implements Runnable {//remember to un-pause!
  boolean quit = false;
  Thread t;
  BinauralBeatSoundEngine BB;
  FileOutputStream fos = null;
  long WT_FileByteCountTotal = 0;
  long WT_FileByteCountCurrent = 0;
  boolean ThreadIsAlive = false;
  String WT_filename = "";

//*************************************
  WAVWriteThread(BinauralBeatSoundEngine theBB, String filename) {
    BB = theBB;
    WT_filename = filename;
    if (BB.BB_Loops <1) {
      return;
    } //simply won't allow user to create WAV file in infinite loop mode
    WT_FileByteCountTotal = ((int)(BB.BB_Loops * BB.BB_TotalDuration)) * 176400;
    if (0 != AT_WriteWAVHeader(WT_filename)) {
      return;
    }
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
    try {
      WT_FileByteCountCurrent = 0;
      ThreadIsAlive = true;
      int local_buffsize = 1024 << 2;//must always be multiple of 4, since 4 bytes are used for each stereo sample
      byte [] local_buffer = new byte[local_buffsize + 4];
      //int sound_buffsize = local_buffsize;
      //this is the main loop of WAVWriteThread; it only quits when done with all Audio:
      while (quit == false && WT_FileByteCountCurrent <= WT_FileByteCountTotal) {
        BB.BB_MainLoop(local_buffer, local_buffsize);
        if (((BB.BB_InfoFlag) & BinauralBeatSoundEngine.BB_COMPLETED) != 0) {
          quit = true;       //so bbl_OnButton_Play() thinks I was running
        } else {
          //now send the buffer out to SDL:
          fos.write(local_buffer, 0, local_buffsize);
          WT_FileByteCountCurrent+=local_buffsize;
        }
      }
      fos.close();
      Cleanup();
    } catch (Exception e) {
      System.out.println(e);
    }
    System.out.println("WAVWriteThread: Done, wrote "+(36+WT_FileByteCountCurrent)+" bytes");
    ThreadIsAlive = false; //signals to user that thread is done.
  }//end run()



  public final static int swabInt(int v) {
    return  (v >>> 24) | (v << 24) |
            ((v << 8) & 0x00FF0000) | ((v >> 8) & 0x0000FF00);
  }

////////////////////////////
//NOTES: JavaVM is always little-endian; WAV is always (as Intel) big-endian
//returns 0 on success
  public int AT_WriteWAVHeader(String filename) {
//WAVheader_part1 is 4 bytes long:
    byte WAVheader_part1[]={'R','I','F','F'};
//WAVheader_part2 is 32 bytes long:
    byte WAVheader_part2[]={87,65,86,69,102,109,116,32,16,0,0,0,1,0,2,0,68,-84,0,0,16,-79,2,0,4,0,16,0,100,97,116,97};
    byte unsignedintholder[]={0,0,0,0};

//Brute force approach to writing WAV files here, as follows:
//1) write WAVheader_part1 to disk
//2) write value (WT_FileByteCountTotal + 36) to disk as an unsigned int in little-endian order
//3) write WAVheader_part2 to disk
//4) write value (WT_FileByteCountTotal) to disk as an unsigned int in little-endian order
//5) write all the data to disk, stopping when WT_FileByteCountTotal bytes are written
    System.out.println("WAVWriteThread: Total to write: "+(36+WT_FileByteCountTotal)+" bytes");
    try {
      fos = new FileOutputStream(filename);
    } catch (Exception e) {
      System.out.println(e);
    }
    try {
      fos.write(WAVheader_part1);
      //must write the length in little-endian:
      unsignedintholder[3]=(byte)(((WT_FileByteCountTotal+36)>>24)&0xff);
      unsignedintholder[2]=(byte)(((WT_FileByteCountTotal+36)>>16)&0xff);
      unsignedintholder[1]=(byte)(((WT_FileByteCountTotal+36)>>8)&0xff);
      unsignedintholder[0]=(byte)(((WT_FileByteCountTotal+36)>>0)&0xff);
      fos.write(unsignedintholder, 0, 4);
      fos.write(WAVheader_part2);
      //must write the length in little-endian:
      unsignedintholder[3]=(byte)(((WT_FileByteCountTotal)>>24)&0xff);
      unsignedintholder[2]=(byte)(((WT_FileByteCountTotal)>>16)&0xff);
      unsignedintholder[1]=(byte)(((WT_FileByteCountTotal)>>8)&0xff);
      unsignedintholder[0]=(byte)(((WT_FileByteCountTotal)>>0)&0xff);
      fos.write(unsignedintholder, 0, 4);
//      fos.close();// no need to do this here; do it in main loop
      return 0;//success
    } catch (Exception e) {
      System.out.println(e);
      return 1;//failure
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

}//end class WAVWriteThread
