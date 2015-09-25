import java.io.*;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class AudioFileOpener {

	/////////////////////////////////////////////////////////
	//give it a filename and an un-allotted byte array pointer,
	//and it fills it up with audiodata:
	public byte [] ReadBytes (String filepath)
	{
		int numBytesRead = 0;
		int bytesPerFrame = 0;
		byte[] audioBytes = null;
		//System.out.println("Going to open a file:");
		File fileIn = new File(filepath);
		//System.out.println("Done opening file");
		try {
			AudioInputStream audioInputStream = 
				AudioSystem.getAudioInputStream(fileIn);
			bytesPerFrame = 
				audioInputStream.getFormat().getFrameSize();
			if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) {
				// some audio formats may have unspecified frame size
				// in that case we may read any amount of bytes
				bytesPerFrame = 1;
			} 

			int numBytes = (int) (audioInputStream.getFrameLength() * bytesPerFrame);

			if (4 != bytesPerFrame && 2 != bytesPerFrame) {
				System.out.println("Number of bytes per frame:"+numBytes+", can't handle that, bye");
				return null;
			}

			System.out.println("Number of bytes allotted:"+numBytes+
					" FrameLen:"+audioInputStream.getFrameLength()+
					" FrameSize: "+audioInputStream.getFormat().getFrameSize());

			audioBytes = new byte[numBytes];
			try {
				//read all the bytes at once:
				numBytesRead = 	audioInputStream.read(audioBytes); 
				System.out.println("Bytes read on one pass:"+numBytesRead);
			} catch (Exception ex) {
				return null;
			}
		} catch (Exception e) {
			return null;
		}
		if (-1 != numBytesRead && 2 == bytesPerFrame) {//convert mono to stereo:
			System.out.println("Converting Mono to Stereo");
			byte [] tmpbytes = new byte[numBytesRead*2];
			int i = 0;
			int j = 0;
			while (i < numBytesRead) {
				tmpbytes[j] = tmpbytes[j+2]= audioBytes[i];
				tmpbytes[j+1] = tmpbytes[j+3]= audioBytes[(i+1)];
				i+=2;
				j+=4;
			}
			return tmpbytes;
		}
		return audioBytes;
	}//end ReadBytes()

}

