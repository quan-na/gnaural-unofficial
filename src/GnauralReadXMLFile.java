/*
   GnauralReadXMLFile.java
   Copyright (C) 2011  Bret Logan

   This library is free software; you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public
   License as published by the Free Software Foundation; either
   version 2.1 of the License, or (at your option) any later version.

   This library is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public
   License along with this library; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

/*
 * Bret Logan 20110412
 * The philosophy of Gnaural XML is to be very simple:
 * every kind of gnauralfile element name is unique, so 
 * whether it is passed as an attribute or a tag, it just
 * gets thrown at one big function as a name with a value,
 *  and BB sorts out the rest.
 *  This is a Sax XML parser, doing three-passes on the 
 *  file. First is to count voices so it can allot space
 *  for the voices, second is to count entries in each voice 
 *  so it can allot each voice's entries, and third is to
 *  actually read the values in the file itself to put 
 *  directly in to the BB engine.
 */

import java.io.File;
import java.io.IOException;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import javax.xml.parsers.*;

public class GnauralReadXMLFile extends DefaultHandler {
	// Increments whenever a change to file format obsoletes old gnaural
	// formats:
	static final String GNAURAL_XML_VERSION = "1.20101006";

	BinauralBeatSoundEngine BB = null;;
	int TotalVoiceCount = 0;
	int TotalEntryCount = 0;
	int CurVox = 0;
	int CurEntry = 0;
	String CurElement = "";
	boolean success = false;
	boolean FINAL_PASS = false;
	String voice_desc = ""; // this is required jic voice is an Audio File
	AudioFileOpener AFO; // this is to load PCM arrays for Sound file Voices
	File mFile = null;

	// ///////////////////////////////////
	public GnauralReadXMLFile(String xmlFile, BinauralBeatSoundEngine theBB) {
		// first do a dry-run to count voices:
		BB = null;// this is how parser knows first pass is dry run
		success = false;
		SAXParserFactory factory = null;
		SAXParser parser = null;
		DefaultHandler handler = null;
		mFile = null;

		factory = SAXParserFactory.newInstance();
		try {
			parser = factory.newSAXParser();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		handler = this;
		mFile = new File(xmlFile);
		if (!mFile.exists()) {
			System.out.println("File not found!");
			success = false;
			return;
		}
		// 1) start first pass:
		// Goal: get real Voice Count, allot space for them in BB:
		FINAL_PASS = false;
		TotalVoiceCount = 0;
		TotalEntryCount = 0;
		CurVox = 0;// critical reset!!!
		CurEntry = 0;// critical reset!!!
		// factory = SAXParserFactory.newInstance();
		// parser = factory.newSAXParser();
		// handler = this;
		try {
			parser.parse(mFile, handler);
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		BB = theBB;
		BB.BB_InitVoices(TotalVoiceCount);// the whole point of pass one
		// end first pass

		// 2) start second pass:
		// Goal: get real entry count in each voice, allot space for them in BB:
		FINAL_PASS = false;
		BB.BB_TotalDuration = 0;// added 20110412 to be sure it gets done
								// somewhere before CalibrateVoice
		TotalVoiceCount = 0;
		TotalEntryCount = 0;
		CurVox = 0;// critical reset!!!
		CurEntry = 0;// critical reset!!!
		// factory = SAXParserFactory.newInstance();
		// parser = factory.newSAXParser();
		// handler = this;
		try {
			parser.parse(mFile, handler);
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// end second pass
		System.out.println("Voices: " + TotalVoiceCount + " Entries: "
				+ TotalEntryCount);
		// end second pass

		// 3) start third pass:
		// Goal: finally, collect the real data:
		FINAL_PASS = true;
		BB.BB_TotalDuration = 0;// have to do this somewhere
		TotalVoiceCount = 0;// not necessary to reset
		TotalEntryCount = 0;// not necessary to reset
		CurVox = 0;// critical reset!!!
		CurEntry = 0;// critical reset!!!
		// factory = SAXParserFactory.newInstance();
		// parser = factory.newSAXParser();
		// handler = this;
		try {
			parser.parse(mFile, handler);
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// end third pass
		success = true;
		BB.BB_PauseFlag = false;
	}

	// ///////////////////////////////////////////
	public void openAudioFile(String value) {
		final int BB_VOICETYPE_PCM = 2; // i get this from
										// BinauralBeatSoundEngine.java
		BB.BB_Voice[CurVox].type = Integer.parseInt(value);
		// deal with audio file Voices:
		if (BB_VOICETYPE_PCM == BB.BB_Voice[CurVox].type) {
			AFO = new AudioFileOpener();
			System.out.println("Audio File: " + voice_desc);
			BB.BB_Voice[CurVox].PCM_samples = AFO.ReadBytes(voice_desc);
			if (null == BB.BB_Voice[CurVox].PCM_samples) {
				// try one more time from schedule's directory:
				String tmpstr = mFile.getParent() + File.separator + voice_desc;
				System.out.println("Not there, trying again at: " + tmpstr);
				BB.BB_Voice[CurVox].PCM_samples = AFO.ReadBytes(tmpstr);
				if (null == BB.BB_Voice[CurVox].PCM_samples) {
					System.out
							.println("Audio File load failed, you sure it's at "
									+ voice_desc + "?");
					BB.BB_Voice[CurVox].PCM_samples_size = 0;
					return;
				}
			}
			System.out.println("Got it");
			// IMPORTANT: i judge length by "Frames", not bytes here:
			BB.BB_Voice[CurVox].PCM_samples_size = BB.BB_Voice[CurVox].PCM_samples.length >> 2;
		}
	}

	// /////////////////////////////////////////////
	// everything gets thrown here to get sorted out
	public void GnauralTextSorter(String name, String value) {
		if (null == BB || true != FINAL_PASS)
			return;// first run BB is always null

		// System.out.println("Name:" + name + "   Value:"+ value + " CurVox:" +
		// CurVox + " CurEntry:" + CurEntry);

		if (true == name.equals("duration")) {
			BB.BB_Voice[CurVox].Entry[CurEntry].duration = Float
					.parseFloat(value);
			return;
		}

		if (true == name.equals("volume_left")) {
			BB.BB_Voice[CurVox].Entry[CurEntry].volL_start = Float
					.parseFloat(value);
			return;
		}

		if (true == name.equals("volume_right")) {
			BB.BB_Voice[CurVox].Entry[CurEntry].volR_start = Float
					.parseFloat(value);
			return;
		}

		if (true == name.equals("beatfreq")) {
			BB.BB_Voice[CurVox].Entry[CurEntry].beatfreq_start_HALF = 0.5 * Float
					.parseFloat(value);
			return;
		}

		if (true == name.equals("basefreq")) {
			BB.BB_Voice[CurVox].Entry[CurEntry].basefreq_start = Float
					.parseFloat(value);
			return;
		}
		// end filling Entry variables
		// ///////////////

		// ///////////////
		// start filling Voice variables:
		if (true == name.equals("id")) {
			BB.BB_Voice[CurVox].id = CurVox;
			return;
		}

		if (true == name.equals("type")) {
			openAudioFile(value);
			return;
		}

		/*
		 * if (true == name.equals("state")) { BB.BB_Voice[CurVox].state =
		 * Integer.parseInt(value); return; }
		 */
		// THIS IS UGLY. I shouldn't depend on the file having the right number
		// here
		if (true == name.equals("entrycount")) {
			// 20110411not trusted anymore, so derived by count at second pass
			return;
		}
		// end filling Voice variables:
		// ///////////////

		// ///////////////
		// start filling Overall parameter variables
		// - read how many entries and allot that amount of space:
		if (true == name.equals("voicecount")) {
			// this gets manually counted now, to avoid user file editing
			// craziness
			return;
		}

		if (true == name.equals("totalentrycount")) {
			// this gets manually counted now, to avoid user file editing
			// craziness
			return;
		}

		if (true == name.equals("loops")) {
			BB.BB_Loops = BB.BB_LoopCount = Integer.parseInt(value);
			return;
		}

		if (true == name.equals("overallvolume_left")) {
			BB.BB_VolumeOverall_left = Float.parseFloat(value);
			if (BB.BB_VolumeOverall_right <= BB.BB_VolumeOverall_left) {
				BB.BB_OverallVolume = BB.BB_VolumeOverall_left;
				BB.BB_OverallBalance = 0;
				if (BB.BB_VolumeOverall_left != 0.0) {
					BB.BB_OverallBalance = -(1.f - (BB.BB_VolumeOverall_right / BB.BB_VolumeOverall_left));
				}
			}
			return;
		}

		if (true == name.equals("overallvolume_right")) {
			BB.BB_VolumeOverall_right = Float.parseFloat(value);
			if (BB.BB_VolumeOverall_right >= BB.BB_VolumeOverall_left) {
				BB.BB_OverallVolume = BB.BB_VolumeOverall_right;
				BB.BB_OverallBalance = 0;
				if (BB.BB_VolumeOverall_right != 0.0) {
					BB.BB_OverallBalance = 1.f - (BB.BB_VolumeOverall_left / BB.BB_VolumeOverall_right);
				}
			}
			return;
		}

		if (true == name.equals("stereoswap")) {
			BB.BB_StereoSwap = Integer.parseInt(value);
			return;
		}

		if (true == name.equals("graphview")) {
			// todo: splash this over to main program GUI
			return;
		}

		if (true == name.equals("voice_state")) {
			// todo: splash this over to main program GUI
			return;
		}

		if (true == name.equals("voice_hide")) {
			// todo: splash this over to main program GUI
			return;
		}

		if (true == name.equals("voice_mute")) {
			// todo: splash this over to main program GUI
			// BB.BB_Voice[CurVox].mute = Integer.parseInt(value);
			return;
		}

		if (true == name.equals("voice_mono")) {
			// BB.BB_Voice[CurVox].mono = Integer.parseInt(value);
			return;
		}

		if (true == name.equals("gnauralfile_version")) {
			if (true == value.equals(GNAURAL_XML_VERSION)) {
				System.out.println("Useable file version, " + value);
			} else {
				System.out.println("Unknown File Version, expected "
						+ GNAURAL_XML_VERSION + ", got " + value);
			}
			return;
		}

		if (true == name.equals("title")) {
			BB.mTitle = value;
			return;
		}

		if (true == name.equals("schedule_description")) {
			BB.mDescription = value;
			return;
		}

		if (true == name.equals("author")) {
			BB.mAuthor = value;
			return;
		}

		if (true == name.equals("description")) {
			// THIS CAN BE A AUDIO FILE VOICE, so must save it jic:
			voice_desc = value;
			return;
		}

		if (true == name.equals("gnaural_version")) {
			// todo: compare versions and scold user haha
			return;
		}

		// dead ones (not needing any parsing) follow:
		/*
		 * if (true == name.equals("schedule")) { //todo: nothing, this is a
		 * dead one return; }
		 * 
		 * if (true == name.equals("voice")) { //todo: nothing, this is a dead
		 * one return; }
		 * 
		 * if (true == name.equals("entries")) { //todo: nothing, this is a dead
		 * one return; }
		 * 
		 * if (true == name.equals("parent")) { //todo: nothing, this is a dead
		 * one return; }
		 * 
		 * if (true == name.equals("date")) { //todo: nothing, this is a dead
		 * one return; }
		 * 
		 * if (true == name.equals("totaltime")) { //todo: nothing, this is a
		 * dead one return; }
		 * 
		 * System.out.println("####NOT DEALT WITH!!! Name:" + name +
		 * "   Value:"+ value + " CurVox:" + CurVox + " CurEntry:" + CurEntry);
		 */
	}

	// ///////////////////////////////////
	public void startElement(String namespaceURI, String localName,
			String qName, Attributes attrs) throws SAXException {

		CurElement = qName;

		if (qName.equalsIgnoreCase("entry")) {
			++TotalEntryCount;
			// entry is the only tag type with attributes, so grab them here:
			int count = attrs.getLength();
			if (null != BB)
				for (int i = 0; i < count; i++) {
					GnauralTextSorter(attrs.getQName(i), attrs.getValue(i));
				}
			return;
		}

		// this is where i really determine TotalVoiceCount, ignoring what
		// is in the file (since it could be wrong) & just counting voices
		if (qName.equalsIgnoreCase("voice")) {
			// if (null != BB)
			// BB.BB_CalibrateVoice(CurVox);
			++TotalVoiceCount;
			CurEntry = 0;
			return;
		}
	}

	// ///////////////////////////////////
	public void endElement(String namespaceURI, String localName, String qName)
			throws SAXException {

		if (qName.equalsIgnoreCase("entry")) {
			++CurEntry;
			CurElement = "";
			return;
		}

		// this is where i really determine TotalVoiceCount, ignoring what
		// is in the file (since it could be wrong) & just counting voices
		if (qName.equalsIgnoreCase("voice")) {
			// biggie, ONLY done once, and ONLY on the 2nd pass:
			if (null != BB && true != FINAL_PASS) {
				System.out.println("Allotting Events: CurVox:" + CurVox
						+ ", CurEntry:" + CurEntry);
				BB.BB_SetupVoice(CurVox, 0, BB.BB_Voice[CurVox].mute,
						BB.BB_Voice[CurVox].mono, CurEntry);
			}
			if (true == FINAL_PASS)
				BB.BB_CalibrateVoice(CurVox);// critical!!
			++CurVox;// really stays same as TotalVoiceCount all the time
			CurEntry = 0;
			CurElement = "";
			return;
		}
		CurElement = "";
	}

	// ///////////////////////////////////
	public void characters(char ch[], int start, int length) {
		if (CurElement.equalsIgnoreCase(""))
			return;
		GnauralTextSorter(CurElement, new String(ch, start, length));
	}

	// ///////////////////////////////////
	public void startDocument() throws SAXException {
		// System.out.println("startDocument()");
	}

	// ///////////////////////////////////
	public void endDocument() throws SAXException {
		// System.out.println("endDocument()");
	}

	// ///////////////////////////////////
	public static void main(String args[]) throws Exception {
		BinauralBeatSoundEngine myBB = new BinauralBeatSoundEngine(44100, 2);
		String xmlFile = args[0];
		new GnauralReadXMLFile(xmlFile, myBB);
	}
}
