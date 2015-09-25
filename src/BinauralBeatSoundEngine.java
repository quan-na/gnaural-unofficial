/*
   BinauralBeatSoundEngine.java
   Copyright (C) 2008  Bret Logan

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

//Notes:
// - To use:
//  1) Set the number of Voices with BB_InitVoices(#);
//  2) Allot Entry memory for each voice by running BB_SetupVoice() on each
//  3) Load memory for each entry alotted in step 2 (for each Voice) in any way you see fit;
//  4) Run BB_CalibrateVoice() on each voice
//  5) init the sound with BB_SoundInit();
//  6) start the sound with Pa_StartStream( gnaural_pa_stream );
//  7) go to sleep or do whatever you want; sound runs in it's own thread
//  8) when done, cleanup all sound resources with BB_SoundCleanup();
//  9) cleanup BinauralBeatSoundEngine by running BB_CleanupVoices();
//  10) you are all done
// - All volumes are in the range 0.0 to 1.0
// - You can start up sound and not worry that no data has been given
//    yet, since as long as BB_VoiceCount == 0, only 0 will be passed to sound engine.
// TODO:
// - figure out a way (union?) to have data members have names that sound relevant to their voices
// - decide whether they're called Entries or Events

//======================================
class BB_EventData {
	double duration;
	long AbsoluteStart_samples; // total sample count at start of entry. about
	// 27 hours possible with uint
	long AbsoluteEnd_samples; // total sample count at end of entry. about 27
	// hours possible with uint
	double volL_start;
	double volL_end;
	double volL_spread;
	double volR_start;
	double volR_end;
	double volR_spread;
	// == Following names reflect BB_VOICETYPE_BINAURALBEAT, but get used for
	// arbitrarily for other voice types:
	double basefreq_start;
	double basefreq_end;
	double basefreq_spread;
	double beatfreq_start_HALF; // for a Beat Freq of 1.0, you'd make this 0.5.
	// Done for calculation speed
	double beatfreq_end_HALF; // .5 the Beat Freq of the next Event (or first
	// event if there is no next).
	double beatfreq_spread_HALF; // this is the difference between start_HALF
	// and end_HALF
	double LastPositionL;
	double LastPositionR;
	// --------------------
	int X;// here strictly for graphing, not used at all in BB
	int Y;// here strictly for graphing, not used at all in BB
};

class BB_Waterdrop {
	float count;
	float decrement;
	float stereoMix;
};

class BB_VoiceData {
	int id; // 0,1,2,...
	int type; // masks: BB_VOICETYPE_BINAURALBEAT, BB_VOICETYPE_PINKNOISE,
	// BB_VOICETYPE_PCM
	int mute; // TRUE or FALSE
	int mono; // TRUE or FALSE [added 20100614]
	double TotalDuration; // NOTE: this is strictly the duration of this voice,
	// which may or may not be the same as BB_TotalDuration
	int EntryCount;
	int CurEntry; // this will always hold the current entry being processed in
	// voice
	BB_EventData[] Entry;
	double CurVolL;
	double CurVolR;
	// the rest are all Voice-type specific data, to be used in any way
	// appropriate for their kind of voice:
	double cur_basefreq; // BB_VOICETYPE_BINAURALBEAT: cur_basefreq;
	double cur_beatfreq; // BB_VOICETYPE_BINAURALBEAT: a freq snapshot in Hz of
	// actual beat being generated
	double cur_beatfreqL_factor; // BB_VOICETYPE_BINAURALBEAT:
	// cur_beatfreqL_factor;
	double cur_beatfreqR_factor; // BB_VOICETYPE_BINAURALBEAT:
	// cur_beatfreqR_factor;
	int cur_beatfreq_phasesamplecount; // decremented to determine where in
	// phase we are for binaural and
	// isochronic (also calls user func if
	// !=NULL)
	int cur_beatfreq_phasesamplecount_start; // cupdated each time a beatfreq
	// changes to load
	// cur_beatfreq_phasesamplecount
	int cur_beatfreq_phaseflag; // toggles between TRUE/FALSE every BB cycle
	// (useful for triggering external stimuli)
	double cur_beatfreq_phaseenvelope; // BB_VOICETYPE_BINAURALBEAT: snapshot
	// between 0 and 2 of phase data of BB
	// frequency
	double sinPosL; // BB_VOICETYPE_BINAURALBEAT: sinPosL; phase info for left
	// channel
	double sinPosR; // BB_VOICETYPE_BINAURALBEAT: sinPosR; phase info for right
	// channel
	double sinL; // BB_VOICETYPE_BINAURALBEAT: sinL; instantaneous sin being
	// used for the sample's left channel
	double sinR; // BB_VOICETYPE_BINAURALBEAT: sinR; instantaneous sin being
	// used for the sample's right channel
	int noiseL; // BB_VOICETYPE_PINKNOISE: instantaneous noise value left sample
	int noiseR; // BB_VOICETYPE_PINKNOISE: instantaneous noise value left sample
	// == Following is only used for BB_VOICETYPE_PCM:
	byte[] PCM_samples; // this is an int array holding stereo 44.1khz data,
	// created by user (and MUST BE free'd by user too). Set
	// to NULL if it holds no data.
	int PCM_samples_size; // this is the number of elements in PCM_samples (in
	// "frames", which means ints)
	int PCM_samples_currentcount;
	BB_Waterdrop[] Drop;
	int ManualFreqBeatControl; // 0 == "not on"
	int ManualFreqBaseControl; // 0 == "not on"
	int ManualVolumeControl; // 0 == "not on"
	// --------------------
	int Hide;// here strictly for graphing, not used at all in BB
	String Description;// here strictly for graphing, not used at all in BB
};

// ======================================
class BinauralBeatSoundEngine {
	// these are globals defined in SoundEngine.h:
	// You can add new to the end, but NEVER change old ones; everything
	// depends on their consistency:
	final static int BB_VOICETYPE_BINAURALBEAT = 0;
	final static int BB_VOICETYPE_PINKNOISE = 1;
	final static int BB_VOICETYPE_PCM = 2;
	final static int BB_VOICETYPE_ISOPULSE = 3;
	final static int BB_VOICETYPE_ISOPULSE_ALT = 4;
	final static int BB_VOICETYPE_WATERDROPS = 5;
	final static int BB_VOICETYPE_RAIN = 6;
	// //////////////////////////////////////////

	// a little 4-event default "schedule", order is::
	// beatfreqL_start
	// beatfreqR_start
	// duration
	// basefreq_start
	// volL_start
	// volR_start
	// static final double BB_AUDIOSAMPLERATE = (double) 44100.0;
	static final int BB_EVENT_NUM_OF_ELEMENTS = 5; // this MUST exactly match
	// number of "columns" in a
	// "row" of event data
	static final int BB_SELECTED = 1;
	static final int BB_UNSELECTED = 0;
	static final long BB_COMPLETED = 1;
	static final long BB_NEWLOOP = 2;
	static final long BB_NEWENTRY = 4;
	static final int BB_UPDATEPERIOD_SAMPLES = 16; // larger the number, less
	// frequently I do some
	// computationally expensive
	// stuff. Ex: 441 = every
	// .01 sec. @ 44100khz
	static final int BB_SIN_SCALER = 0x3FFF; // factors max output of sin() to
	// fit a short (0x3fff)
	static final double BB_TWO_PI = (double) Math.PI * 2;

	// static final float BB_BEAT_CONVERTER = (float)(1.0 / BB_SAMPLE_FACTOR);
	// //this is strictly for user to estimate Beat Freq; multiply cur_beatfreqL
	// by it, subtract cur_basefreq
	// C stuff:
	// #define BB_DBGOUT(a) fprintf(stderr,"BB: %s\n",a)
	// #define BB_DBGOUT_INT(a,b) fprintf(stderr,"BB: %s%d\n",a,b)
	// #define BB_DBGOUT_DBL(a,b) fprintf(stderr,"BB: %s%g\n",a,b)
	// #define BB_ERROUT(a) fprintf(stderr,"BB: #Error# %s\n",a)
	// #define GNAURAL_USEDEFAULTSOUNDDEVICE -255

	BB_VoiceData[] BB_Voice;
	double BB_TotalDuration = 0; // THIS IS ONLY HERE FOR USER -- you MUST zero
	// it any time you modify a voice, then run
	// BB_CalibrateVoice to set it
	long BB_CurrentSampleCount = 0;
	long BB_CurrentSampleCountLooped = 0;
	long BB_InfoFlag = 0; // BB uses this only to send messages to the user, who
	// must reset the messages.
	int BB_VoiceCount = 0;

	int BB_LoopCount = 1; // This IS used -- set to 1 to do one pass before BB
	// sets BB_InfoFlag to BB_COMPLETED
	int BB_Loops; // This is used whenever BB_Reset() is called, and sets
	// BB_LoopCount (like when writing a WAV file, for
	// isntance);

	double BB_OverallVolume = 1.0; // user needs to initially set this!
	double BB_OverallBalance = 0.0; // user needs to initally set this!
	double BB_VolumeOverall_left = 1.0; // 1.0 is 100% of whatever is mixed;
	// only needed if sound api doesn't
	// provide an internal one
	double BB_VolumeOverall_right = 1.0; // 1.0 is 100% of whatever is mixed;
	// only needed if sound api doesn't
	// provide an internal one
	int BB_StereoSwap = 0; // set non-0 to swap left and right stereo channels
	// of overall output
	// IMPORTANT NOTE, new 20070831 - BB_PauseFlag is mostly
	// used to keep BB_MainLoop()'s thread from entering BB data while main
	// thread is creating it. USER MUST FALSE IT WHEN DONE CREATING THE DATA:
	boolean BB_PauseFlag = true;// (added to Java form on 20110411)
	boolean BB_InCriticalLoopFlag = false;// added 20110411
	int BB_Mono = 0; // set non-0 to mix stereo channels

	// Ugly, next 3 are here so GnauralReadXMLFile has a joint
	// place to save them, but BB doesn't even know they exist:
	String mTitle = "[Internal Default]"; // gets assigned in GnauralReadXMLFile
	String mDescription = "Basic meditation schedule"; // gets assigned in
	// GnauralReadXMLFile
	String mAuthor = "Gnaural"; // gets assigned in GnauralReadXMLFile

	// the three biggies:
	static int BB_AUDIOSAMPLERATE = 44100;// 11025;//22050;//44100;
	static double BB_AUDIOSAMPLERATE_HALF = .5 * BB_AUDIOSAMPLERATE;
	static double BB_SAMPLE_FACTOR = ((Math.PI * 2 * 2.0) / BB_AUDIOSAMPLERATE);

	// Drop stuff:
	static final int BB_DROPLEN = 8192;
	static final int BB_RAINLEN = 44;
	short[] BB_DropMother = null;
	short[] BB_RainMother = null;
	float BB_WaterWindow = 126;// used for rain & drop
	float BB_DropLowcut = 8.0f;
	float BB_RainLowcut = 0.15f;

	// End Drop stuff

	// ======================================
	BinauralBeatSoundEngine(int samplerate, int nNumberOfVoices) {
		BB_AUDIOSAMPLERATE = samplerate;
		BB_AUDIOSAMPLERATE_HALF = .5 * BB_AUDIOSAMPLERATE;
		BB_SAMPLE_FACTOR = ((Math.PI * 2.0) / BB_AUDIOSAMPLERATE);

		// calibrate for different samplerates based
		// on 44100 constant:
		BB_WaterWindow = BB_WaterWindow * 44100 / (float) BB_AUDIOSAMPLERATE;
		BB_DropLowcut = BB_DropLowcut * 44100 / (float) BB_AUDIOSAMPLERATE;
		BB_RainLowcut = BB_RainLowcut * 44100 / (float) BB_AUDIOSAMPLERATE;

		BB_InitVoices(nNumberOfVoices);
		SeedRand(3676, 2676862);
		int i;
		for (i = 0; i < BB_VoiceCount; i++) {
			// BB_SetupVoice(i , BB_DefaultBBSched, (sizeof(BB_DefaultBBSched) /
			// sizeof(double)) / BB_EVENT_NUM_OF_ELEMENTS);
			BB_LoadDefaultVoice(i);
			// just to give a bit of variety for what gets played:
			// if ((i&0x1) == 0) {
			// BB_Voice[0].type = BB_VOICETYPE_PINKNOISE;
			// Log.d (TAG,"Making voice " + i + " noise");
			// }
		}
		BB_Reset();
	}

	// ======================================
	int BB_InitVoices(int NumberOfVoices) {
		// 20070806: Was doing this here... until I realized it gets called
		// every re-loading!
		// SeedRand (3676, 2676862);
		int i = 0;

		// a20070730: fixed critical memory leak -- was setting BB_VoiceCount =
		// 0 BEFORE running BB_CleanupVoices()!
		if (null != BB_Voice) {
			while (true == BB_InCriticalLoopFlag) {
				++i;
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			BB_PauseFlag = true; // added 20070803 SEE NOTE IN DECLARATIONS,
			// VERY important
			BB_CleanupVoices();
		}
		// 20070803: Next line seems redundant, but it can be important: may not
		// have gotten zero'd
		// in BB_CleanupVoices(), and BB_VoiceCount might be used by audio
		// thread to
		// keep from accessing invalid BB data:
		BB_VoiceCount = 0;
		BB_Voice = new BB_VoiceData[NumberOfVoices]; // in C, I also need to
		// zero this array
		if (null == BB_Voice) {
			return 0;
		}
		for (i = 0; i < NumberOfVoices; i++) {
			BB_Voice[i] = new BB_VoiceData();
			BB_Voice[i].Entry = null;
			BB_Voice[i].Drop = null;// 20110516
			BB_Voice[i].id = 0; // looks like this is sort of arbitrary now, for
			// user's use
			BB_Voice[i].mute = 0; // TRUE, FALSE
			BB_Voice[i].mono = 0; // TRUE, FALSE [20100614]
			BB_Voice[i].type = BB_VOICETYPE_BINAURALBEAT;
			BB_Voice[i].EntryCount = 0;
			BB_Voice[i].CurEntry = 0;
			BB_Voice[i].PCM_samples = null;
			BB_Voice[i].PCM_samples_size = 0; // this is the raw array size (in
			// bytes) of the PCM_samples
			// array (NOT frame count, which
			// would be 1/4th this)
			BB_Voice[i].PCM_samples_currentcount = 0; // this holds current
			// place in the array
			BB_Voice[i].cur_beatfreq = 0.0;
			BB_Voice[i].cur_beatfreq_phaseenvelope = 0.0;
			BB_Voice[i].cur_beatfreq_phaseflag = 0;
			BB_Voice[i].cur_beatfreq_phasesamplecount = 1;
			BB_Voice[i].cur_beatfreq_phasesamplecount_start = 1;
			BB_Voice[i].sinL = 0;
			BB_Voice[i].sinR = 0;
			BB_Voice[i].noiseL = 1;
			BB_Voice[i].noiseR = 1;
		}
		BB_VoiceCount = NumberOfVoices;
		return BB_VoiceCount;
	}

	// ======================================
	// ======================================
	// This gets called after BB_InitVoices:
	int BB_SetupVoice(int VoiceID, // array index of voice created by
			// BB_InitVoices
			int VoiceType, // a kind of BB_VOICETYPE_*
			int mute, // TRUE or FALSE
			int mono, // TRUE or FALSE [20100614]
			int NumberOfEvents) {
		int i = VoiceID;

		BB_Voice[i].type = VoiceType;
		BB_Voice[i].mute = mute;
		BB_Voice[i].mono = mono; // [20100614]
		BB_Voice[i].EntryCount = 0;
		BB_Voice[i].Entry = null; // just in case thread is looking here
		BB_Voice[i].CurEntry = 0;
		// BB_Voice[i].CurEntry_old = -1;
		BB_Voice[i].Entry = new BB_EventData[NumberOfEvents];
		for (int j = 0; j < NumberOfEvents; j++) {
			BB_Voice[i].Entry[j] = new BB_EventData();
		}
		BB_Voice[i].TotalDuration = 0;
		BB_Voice[i].EntryCount = NumberOfEvents;
		return 1;
		// Now user must load this voice with data in any way they see fit, then
		// call BB_CalibrateVoice()
	}

	// ======================================
	// you must call InitVoices() before calling this.
	// This simply loads Gnaural's default schedule, first voice BB, second
	// noise,
	// any other voices else silent
	void BB_LoadDefaultVoice(int VoiceID) {
		int j = 0;
		if (VoiceID == 0) {
			BB_Loops = 1; // added 20070314 so default sched. doesn't start in
			// Inf. mode
			int k = 0;
			int NumberOfEvents = BB_DefaultBBSched.length
					/ BB_EVENT_NUM_OF_ELEMENTS;
			BB_SetupVoice(VoiceID, BB_VOICETYPE_BINAURALBEAT, 0, 0,
					NumberOfEvents);
			// load up starting values, order is:
			// duration, volume_left, volume_right, beatfreq, basefreq,
			for (j = 0; j < NumberOfEvents; j++) {
				BB_Voice[VoiceID].Entry[j].duration = BB_DefaultBBSched[k++];
				BB_Voice[VoiceID].Entry[j].volL_start = BB_DefaultBBSched[k++];
				BB_Voice[VoiceID].Entry[j].volR_start = BB_DefaultBBSched[k++];
				BB_Voice[VoiceID].Entry[j].beatfreq_start_HALF = 0.5 * BB_DefaultBBSched[k++];
				BB_Voice[VoiceID].Entry[j].basefreq_start = BB_DefaultBBSched[k++];
			}
		} else {
			BB_SetupVoice(VoiceID, BB_VOICETYPE_PINKNOISE, 0, 0, 1);
			int NumberOfEvents = BB_DefaultBBSched.length
					/ BB_EVENT_NUM_OF_ELEMENTS;
			double dur = 0.0;
			for (j = 0; j < NumberOfEvents; j++) {
				dur += BB_Voice[0].Entry[j].duration;
			}
			BB_Voice[VoiceID].Entry[0].duration = dur;
			BB_Voice[VoiceID].Entry[0].volL_start = (VoiceID == 1) ? 0.2 : 0.0;
			BB_Voice[VoiceID].Entry[0].volR_start = (VoiceID == 1) ? 0.2 : 0.0;
			BB_Voice[VoiceID].Entry[0].beatfreq_start_HALF = 0.0;
			BB_Voice[VoiceID].Entry[0].basefreq_start = 0.0;
		}

		// do preprocessing on dataset:
		BB_CalibrateVoice(VoiceID);
	}

	// ======================================
	// you must call BB_InitVoices() before calling this...
	int BB_CalibrateVoice(int VoiceID) {
		int i = VoiceID;
		int j;
		int prevEntry, nextEntry;

		// reset total duration:
		BB_Voice[i].TotalDuration = 0;

		for (j = 0; j < BB_Voice[i].EntryCount; j++) {
			// increment voice's total duration:
			BB_Voice[i].TotalDuration += BB_Voice[i].Entry[j].duration;

			// Now save exact total schedule samplecount at end of entry:
			BB_Voice[i].Entry[j].AbsoluteEnd_samples = (long) (BB_Voice[i].TotalDuration * BB_AUDIOSAMPLERATE); // should
			// I
			// this?
		}

		// now figure TotalDuration, SampleCounts, Ends, and Spreads:
		for (j = 0; j < BB_Voice[i].EntryCount; j++) {
			if ((nextEntry = j + 1) >= BB_Voice[i].EntryCount) {
				nextEntry = 0;
			}
			BB_Voice[i].Entry[j].beatfreq_end_HALF = BB_Voice[i].Entry[nextEntry].beatfreq_start_HALF;
			BB_Voice[i].Entry[j].beatfreq_spread_HALF = BB_Voice[i].Entry[j].beatfreq_end_HALF
					- BB_Voice[i].Entry[j].beatfreq_start_HALF;
			BB_Voice[i].Entry[j].basefreq_end = BB_Voice[i].Entry[nextEntry].basefreq_start;
			BB_Voice[i].Entry[j].basefreq_spread = BB_Voice[i].Entry[j].basefreq_end
					- BB_Voice[i].Entry[j].basefreq_start;
			BB_Voice[i].Entry[j].volL_end = BB_Voice[i].Entry[nextEntry].volL_start;
			BB_Voice[i].Entry[j].volL_spread = BB_Voice[i].Entry[j].volL_end
					- BB_Voice[i].Entry[j].volL_start;
			BB_Voice[i].Entry[j].volR_end = BB_Voice[i].Entry[nextEntry].volR_start;
			BB_Voice[i].Entry[j].volR_spread = BB_Voice[i].Entry[j].volR_end
					- BB_Voice[i].Entry[j].volR_start;

			if ((prevEntry = j - 1) < 0)
				BB_Voice[i].Entry[j].AbsoluteStart_samples = 0;
			else
				BB_Voice[i].Entry[j].AbsoluteStart_samples = BB_Voice[i].Entry[prevEntry].AbsoluteEnd_samples;
		}

		// NOTE: User must be sure to zero BB_TotalDuration if resetting all
		// voices
		if (BB_TotalDuration < BB_Voice[i].TotalDuration) {
			BB_TotalDuration = BB_Voice[i].TotalDuration;
		}
		return 1;
	}

	// ======================================
	void BB_CleanupVoices() {
		for (int i = 0; i < BB_VoiceCount; i++) {
			BB_Voice[i].Entry = null;
			BB_Voice[i].Drop = null;
		}
		BB_Voice = null;
		BB_VoiceCount = 0;
		BB_Voice = null;

	}

	// ======================================
	void BB_ResetAllVoices() {
		if (null != BB_Voice) {
			int i;

			for (i = 0; i < BB_VoiceCount; i++) {
				BB_Voice[i].CurEntry = 0;
			}
		}
	}

	// ======================================
	// ////////////////////////////////////////////////
	// UserSoundProc(void *pSoundBuffer,long bufferLen)
	// Give this a byte (char) array of len bufferLen, and it fills it
	// Extra info: BB_UPDATEPERIOD_SAMPLES sets the period of update;
	// the higher it is, the less often periodic precalculating is done
	// (determining entry, changing frequency, etc.).
	// These three vars are ONLY USED IN BB_MainLoop; moved here because static
	// locals aren't allowed in Java:
	static int updateperiod = 1; // critical: initialize as 1

	void BB_MainLoop(byte[] pSoundBuffer, int bufferLen) {
		double sumL = 0, sumR = 0;
		double Sample_left, Sample_right;
		int k;
		int voice;

		// Cast whatever form pSoundBuffer came in to short ints (16 bits),
		// since
		// each 32 bit frame will consist of two 16bit mono waveforms,
		// alternating
		// left and right stereo data: [see the Java version for a byte-tailored
		// approach]
		byte pSample[] = pSoundBuffer;
		int pSample_index = 0; // needed for Java, since no pointer math

		// Generally speaking, this is what I am doing:
		// long nbSample = bufferLen / (sizeof(int));
		// But since I always know bufferlen in in chars, I just divide by four:
		long nbSample = bufferLen >> 2;

		// -------------------------------------------
		// START Fill sound buffer
		// OUTER LOOP: do everything in this loop for every sample in
		// pSoundBuffer to be filled:
		for (k = 0; k < nbSample; k++) {
			--updateperiod;
			// zero-out the current sample values:
			sumL = sumR = 0;
			if (false == BB_PauseFlag && null != BB_Voice) // a20070730 --
				// critical bug fix
				for (voice = 0; voice < BB_VoiceCount; voice++) {
					Sample_left = Sample_right = 0;
					BB_InCriticalLoopFlag = true; // added 20070803
					// ##### START Periodic stuff (the stuff NOT done every
					// cycle)
					if (updateperiod == 0) {
						// Should not need this, will eventually drop it:
						while ((BB_Voice[voice].CurEntry >= BB_Voice[voice].EntryCount)) {
							BB_ResetAllVoices();
						}

						// First figure out which Entry we're at for this voice.
						// 20070728: big changes to make CurEntry persistent, to
						// obviate need for
						// each voice to start from zero every time (CPU load
						// would progressively
						// go sour with lots of entries). Method: don't zero
						// CurEntry, instead only
						// increment it if totalsamples is now greater than
						// sched entry's endtime/
						// 20080218: The above method turns out not to account
						// for when user sets
						// BB_CurrentSampleCount manually to place BEFORE
						// CurEntry. Fixed with this:
						// See if totalsamples is LESS than CurEntry (very rare
						// event):
						while (BB_CurrentSampleCount < BB_Voice[voice].Entry[BB_Voice[voice].CurEntry].AbsoluteStart_samples) {
							BB_InfoFlag |= BB_NEWENTRY;
							--BB_Voice[voice].CurEntry;
							if (BB_Voice[voice].CurEntry < 0) {
								BB_Voice[voice].CurEntry = 0;
							}
						}

						// Now see if totalsamples is Greater than CurEntry
						// (common event):
						while (BB_CurrentSampleCount > BB_Voice[voice].Entry[BB_Voice[voice].CurEntry].AbsoluteEnd_samples) {
							BB_InfoFlag |= BB_NEWENTRY;
							++BB_Voice[voice].CurEntry;
							if (BB_Voice[voice].CurEntry >= BB_Voice[voice].EntryCount) {
								// BB_DBGOUT_INT ("Completed loop", 1 + BB_Loops
								// - BB_LoopCount);
								BB_CurrentSampleCountLooped += BB_CurrentSampleCount;
								BB_CurrentSampleCount = 0;
								// Zero CurEntry for ALL voices:
								BB_ResetAllVoices();
								BB_InfoFlag |= BB_NEWLOOP;
								if (--BB_LoopCount == 0) { // tell user Schedule
									// is totally done
									BB_InfoFlag |= BB_COMPLETED;
									// BB_DBGOUT ("Schedule complete");
								}
								break;
							}
						}

						// Now that entry housecleaning is done, start actual
						// signal processing:
						// NOTE: I wanted to put next line at start of voice
						// loop, but can't
						// because any voice can end schedule, even if muted --
						// so all voice
						// must get checked.
						if (0 == BB_Voice[voice].mute) { // START
							// "Voice NOT muted"
							// this just to make BB_Voice[voice].CurEntry more
							// handy:
							int entry = BB_Voice[voice].CurEntry;

							// First come up with a factor describing exact
							// point in the schedule
							// by dividing exact point in period by total period
							// time:
							// [NOTE: critically, duration should never be able
							// to == 0 here because the
							// entry "for" loop above only gets here if
							// BB_Voice[voice].Entry[entry].AbsoluteEnd_samples
							// is GREATER than 0]:
							// if (BB_Voice[voice].Entry[entry].duration == 0)
							// BB_BB_DBGOUT("BUG: duration == 0");
							double factor;

							if (0 != BB_Voice[voice].Entry[entry].duration) {
								factor = (BB_CurrentSampleCount - BB_Voice[voice].Entry[entry].AbsoluteStart_samples)
										/ (BB_Voice[voice].Entry[entry].duration * BB_AUDIOSAMPLERATE);
							} else {
								// NOTE: Interestingly, it actually almost never
								// gets here unless it is first-DP, because
								// because otherwise it is a lotto pick that
								// BB_CurrentSampleCount would land on that DP
								factor = 0;
								// BB_DBGOUT ("Duration == 0!");
							}
							// now determine volumes for this slice, since all
							// voices use volume:
							if (0 == BB_Voice[voice].ManualVolumeControl) {// user
								// does
								// everything;
								// i
								// just
								// skip
								// setting
								// myself
								BB_Voice[voice].CurVolL = (BB_Voice[voice].Entry[entry].volL_spread * factor)
										+ BB_Voice[voice].Entry[entry].volL_start;
								BB_Voice[voice].CurVolR = (BB_Voice[voice].Entry[entry].volR_spread * factor)
										+ BB_Voice[voice].Entry[entry].volR_start;
							}
							// ///////////////////////////
							// Now do stuff that must be figured out at Entry
							// level:
							// now figure out what sort of voice it is, to do
							// it's particular processing:
							// ///////////////////////////
							switch (BB_Voice[voice].type) {
							// ---------A-------------
							case BB_VOICETYPE_BINAURALBEAT: {
								// first determine base frequency to be used for
								// this slice:
								if (0 == BB_Voice[voice].ManualFreqBaseControl) {
									BB_Voice[voice].cur_basefreq = (BB_Voice[voice].Entry[entry].basefreq_spread * factor)
											+ BB_Voice[voice].Entry[entry].basefreq_start;
								}
								// 20071010 NOTE: heavily changed to optimize
								// calculations and avail instantaneous BeatFreq
								// It is now assumed that all beatfreqs are
								// symmetric around the basefreq.
								// now that more difficult one, calculating
								// partially factored beat freqs:
								// Left Freq is equal to frequency spread from
								// Left Start to Left End (next
								// schedule's Left Start) multiplied by above
								// factor. Then add FreqBase.
								BB_Voice[voice].cur_beatfreqL_factor = BB_Voice[voice].cur_beatfreqR_factor = (BB_Voice[voice].Entry[entry].beatfreq_spread_HALF * factor);

								// get current beatfreq in Hz (for user external
								// use):
								double old_beatfreq = BB_Voice[voice].cur_beatfreq;
								if (0 == BB_Voice[voice].ManualFreqBeatControl) {
									BB_Voice[voice].cur_beatfreq = (BB_Voice[voice].cur_beatfreqL_factor + BB_Voice[voice].Entry[entry].beatfreq_start_HALF) * 2;
								}

								if (0 == BB_Voice[voice].ManualFreqBeatControl) {
									// NOTE: BB_SAMPLE_FACTOR ==
									// 2*PI/sample_rate
									BB_Voice[voice].cur_beatfreqL_factor = (BB_Voice[voice].cur_basefreq
											+ BB_Voice[voice].Entry[entry].beatfreq_start_HALF + BB_Voice[voice].cur_beatfreqL_factor)
											* BB_SAMPLE_FACTOR;

									BB_Voice[voice].cur_beatfreqR_factor = (BB_Voice[voice].cur_basefreq
											- BB_Voice[voice].Entry[entry].beatfreq_start_HALF - BB_Voice[voice].cur_beatfreqR_factor)
											* BB_SAMPLE_FACTOR;
								} else {
									BB_Voice[voice].cur_beatfreqL_factor = (BB_Voice[voice].cur_basefreq + BB_Voice[voice].cur_beatfreq)
											* BB_SAMPLE_FACTOR;
									BB_Voice[voice].cur_beatfreqR_factor = (BB_Voice[voice].cur_basefreq - BB_Voice[voice].cur_beatfreq)
											* BB_SAMPLE_FACTOR;
								}

								// extract phase info for external stimulus cue:
								// must have a lower limit for beatfreq or else
								// we have divide by zero issues
								if (BB_Voice[voice].cur_beatfreq < .0001) {
									BB_Voice[voice].cur_beatfreq = .0001;
								}
								if (old_beatfreq != BB_Voice[voice].cur_beatfreq) {
									double phasefactor = (BB_Voice[voice].cur_beatfreq_phasesamplecount / (double) BB_Voice[voice].cur_beatfreq_phasesamplecount_start);
									BB_Voice[voice].cur_beatfreq_phasesamplecount_start = (int) (BB_AUDIOSAMPLERATE_HALF / BB_Voice[voice].cur_beatfreq);
									BB_Voice[voice].cur_beatfreq_phasesamplecount = (int) (BB_Voice[voice].cur_beatfreq_phasesamplecount_start * phasefactor);
								}
							}
								break;

							// ---------A-------------
							case BB_VOICETYPE_PINKNOISE:
								break;

							// ---------A-------------
							case BB_VOICETYPE_PCM:
								if (null != BB_Voice[voice].PCM_samples) {
									BB_Voice[voice].PCM_samples_currentcount = (int) BB_CurrentSampleCount;
									if (BB_Voice[voice].PCM_samples_currentcount >= BB_Voice[voice].PCM_samples_size) {
										BB_Voice[voice].PCM_samples_currentcount = (int) (BB_CurrentSampleCount % (BB_Voice[voice].PCM_samples_size));
									}
								}
								break;

							// ---------A-------------
							case BB_VOICETYPE_ISOPULSE:
							case BB_VOICETYPE_ISOPULSE_ALT: {
								// in isochronic tones, the beat frequency
								// purely affects base
								// frequency pulse on/off duration, not its
								// frequency:
								// New way starting 20110119:
								// The approach here is to re-scale however much
								// time was left in
								// the old beatfreq's pulse to how much would be
								// left in the new
								// one's. Otherwise each pulse would have to
								// time-out before a
								// new beatfreq could be modulated -
								// unacceptable in many ways.
								// first get the exact base frequency for this
								// slice:
								BB_Voice[voice].cur_basefreq = (BB_Voice[voice].Entry[entry].basefreq_spread * factor)
										+ BB_Voice[voice].Entry[entry].basefreq_start;

								// Now get current beatfreq in Hz for this
								// slice:
								// ("half" works because period alternates
								// silence and tone for this kind of voice)
								double old_beatfreq = BB_Voice[voice].cur_beatfreq;
								BB_Voice[voice].cur_beatfreq = ((BB_Voice[voice].Entry[entry].beatfreq_spread_HALF * factor) + BB_Voice[voice].Entry[entry].beatfreq_start_HALF) * 2;

								// must have a lower limit for beatfreq or else
								// we have divide by zero issues
								if (BB_Voice[voice].cur_beatfreq < .0001) {
									BB_Voice[voice].cur_beatfreq = .0001;
								}

								// Set both channels to the same base frequency:
								// NOTE: BB_SAMPLE_FACTOR == 2*PI/sample_rate
								BB_Voice[voice].cur_beatfreqR_factor = BB_Voice[voice].cur_beatfreqL_factor = BB_Voice[voice].cur_basefreq
										* BB_SAMPLE_FACTOR;

								// if this is a new beatfreq, adjust phase
								// accordingly
								if (old_beatfreq != BB_Voice[voice].cur_beatfreq) {
									double phasefactor = (BB_Voice[voice].cur_beatfreq_phasesamplecount / (double) BB_Voice[voice].cur_beatfreq_phasesamplecount_start);
									BB_Voice[voice].cur_beatfreq_phasesamplecount_start = (int) (BB_AUDIOSAMPLERATE_HALF / BB_Voice[voice].cur_beatfreq);
									BB_Voice[voice].cur_beatfreq_phasesamplecount = (int) (BB_Voice[voice].cur_beatfreq_phasesamplecount_start * phasefactor);
								}
							}
								break;

							// ---------A-------------
							// if a Drop that's supposed to exist is found to
							// be NULL, that's the signal to set up the whole
							// shebang:
							case BB_VOICETYPE_WATERDROPS: // 20110516
							{
								if (null == BB_Voice[voice].Drop) {
									if (null == BB_DropMother) {
										BB_DropMother = new short[BB_DROPLEN];
									}
									BB_Voice[voice].PCM_samples_size = (int) (BB_Voice[voice].Entry[entry].beatfreq_start_HALF * 2);
									// make sure there's at least one drop:
									if (1 > BB_Voice[voice].PCM_samples_size)
										BB_Voice[voice].PCM_samples_size = 1;
									// limit # of drops to 128:
									if (128 < BB_Voice[voice].PCM_samples_size)
										BB_Voice[voice].PCM_samples_size = 128;

									BB_Voice[voice].Drop = new BB_Waterdrop[BB_Voice[voice].PCM_samples_size];
									for (int j = 0; j < BB_Voice[voice].PCM_samples_size; j++) {
										BB_Voice[voice].Drop[j] = new BB_Waterdrop();
									}
									int i;
									for (i = 0; i < BB_Voice[voice].PCM_samples_size; i++) {
										BB_Voice[voice].Drop[i].count = 0;
										BB_Voice[voice].Drop[i].decrement = 1;
										BB_Voice[voice].Drop[i].stereoMix = .5f;
									}
									BB_ToneRain(BB_DropMother, 600);
								}
								// now do the real stuff:
								// get base frequency to be used for this slice:
								BB_Voice[voice].cur_basefreq = (BB_Voice[voice].Entry[entry].basefreq_spread * factor)
										+ BB_Voice[voice].Entry[entry].basefreq_start;
								if (BB_AUDIOSAMPLERATE != 44100) {
									BB_Voice[voice].cur_basefreq = BB_Voice[voice].cur_basefreq
											* 44100
											/ (float) BB_AUDIOSAMPLERATE;
								}

							}
								break;

							// ---------A-------------
							case BB_VOICETYPE_RAIN: {
								if (null == BB_Voice[voice].Drop) {
									if (null == BB_RainMother) {
										BB_RainMother = new short[BB_RAINLEN];
									}
									BB_Voice[voice].PCM_samples_size = (int) (BB_Voice[voice].Entry[entry].beatfreq_start_HALF * 2);
									// make sure there's at least one drop:
									if (1 > BB_Voice[voice].PCM_samples_size)
										BB_Voice[voice].PCM_samples_size = 1;
									// limit # of drops to 128:
									if (128 < BB_Voice[voice].PCM_samples_size)
										BB_Voice[voice].PCM_samples_size = 128;

									BB_Voice[voice].Drop = new BB_Waterdrop[BB_Voice[voice].PCM_samples_size];
									for (int j = 0; j < BB_Voice[voice].PCM_samples_size; j++) {
										BB_Voice[voice].Drop[j] = new BB_Waterdrop();
									}
									int i;
									for (i = 0; i < BB_Voice[voice].PCM_samples_size; i++) {
										BB_Voice[voice].Drop[i].count = 0;
										BB_Voice[voice].Drop[i].decrement = 1;
										BB_Voice[voice].Drop[i].stereoMix = .5f;
									}
									BB_ToneRain(BB_RainMother, 3.4f);
								}
								// now do the real stuff:
								// get base frequency to be used for this slice:
								BB_Voice[voice].cur_basefreq = (BB_Voice[voice].Entry[entry].basefreq_spread * factor)
										+ BB_Voice[voice].Entry[entry].basefreq_start;
								if (BB_AUDIOSAMPLERATE != 44100) {
									BB_Voice[voice].cur_basefreq = BB_Voice[voice].cur_basefreq
											* 44100
											/ (float) BB_AUDIOSAMPLERATE;
								}

							}
								break;

							default:
								break;
							} // END voicetype switch
						} // END "Voice NOT muted"
					}
					// ##### END Periodic stuff (the stuff NOT done every cycle)

					// ####START high priority calculations (done for every
					// sample)
					if (0 == BB_Voice[voice].mute) { // START "Voice NOT muted"
						// now figure out what sort of voice it is, to do it's
						// particular processing:
						switch (BB_Voice[voice].type) {
						// ---------B-------------
						case BB_VOICETYPE_BINAURALBEAT:
							// advance to the next sample for each channel:
							BB_Voice[voice].sinPosL += BB_Voice[voice].cur_beatfreqL_factor;
							if (BB_Voice[voice].sinPosL >= BB_TWO_PI) {
								BB_Voice[voice].sinPosL -= BB_TWO_PI;
							}
							BB_Voice[voice].sinPosR += BB_Voice[voice].cur_beatfreqR_factor;
							if (BB_Voice[voice].sinPosR >= BB_TWO_PI) {
								BB_Voice[voice].sinPosR -= BB_TWO_PI;
							}

							// there are probably shortcuts to avoid doing a
							// sine for every voice, but I don't know them:
							BB_Voice[voice].sinL = Math
									.sin(BB_Voice[voice].sinPosL);
							Sample_left = BB_Voice[voice].sinL * BB_SIN_SCALER;
							BB_Voice[voice].sinR = Math
									.sin(BB_Voice[voice].sinPosR);
							Sample_right = BB_Voice[voice].sinR * BB_SIN_SCALER;

							// extract external stimuli cue:
							if (1 > --BB_Voice[voice].cur_beatfreq_phasesamplecount) {
								BB_Voice[voice].cur_beatfreq_phasesamplecount = BB_Voice[voice].cur_beatfreq_phasesamplecount_start;
								if (0 != BB_Voice[voice].cur_beatfreq_phaseflag) {
									BB_Voice[voice].cur_beatfreq_phaseflag = 0;
								} else {
									BB_Voice[voice].cur_beatfreq_phaseflag = 1;
								}
								BB_Voice[voice].cur_beatfreq_phaseenvelope = 0;
							}
							break;

						// ---------B-------------
						case BB_VOICETYPE_PINKNOISE: {
							// NOTE: the following odd equation is a fast way
							// of simulating "pink noise" from white:
							BB_Voice[voice].noiseL = (((BB_Voice[voice].noiseL * 31) + (rand() >> 15)) >> 5);
							Sample_left = BB_Voice[voice].noiseL;
							BB_Voice[voice].noiseR = (((BB_Voice[voice].noiseR * 31) + (rand() >> 15)) >> 5);
							Sample_right = BB_Voice[voice].noiseR;
						}
							break;

						// ---------B-------------
						case BB_VOICETYPE_PCM:
							if (null != BB_Voice[voice].PCM_samples) {
								if (BB_Voice[voice].PCM_samples_currentcount >= BB_Voice[voice].PCM_samples_size) {
									BB_Voice[voice].PCM_samples_currentcount = (int) (BB_CurrentSampleCount % (BB_Voice[voice].PCM_samples_size));
								}
								int bytecount = (BB_Voice[voice].PCM_samples_currentcount << 2);
								Sample_left = (short) ((BB_Voice[voice].PCM_samples[bytecount + 0] & 0xff) | (BB_Voice[voice].PCM_samples[bytecount + 1] << 8));
								Sample_right = (short) ((BB_Voice[voice].PCM_samples[bytecount + 2] & 0xff) | (BB_Voice[voice].PCM_samples[bytecount + 3] << 8));
								++BB_Voice[voice].PCM_samples_currentcount;
							}
							break;

						// ---------B-------------
						case BB_VOICETYPE_ISOPULSE:
						case BB_VOICETYPE_ISOPULSE_ALT: {
							int iso_alternating = 0;
							if (BB_VOICETYPE_ISOPULSE_ALT == BB_Voice[voice].type) {
								iso_alternating = 1;
							}
							// New way starting 20110119:
							// advance to the next sample for each channel:
							BB_Voice[voice].sinPosL += BB_Voice[voice].cur_beatfreqL_factor;
							if (BB_Voice[voice].sinPosL >= BB_TWO_PI) {
								BB_Voice[voice].sinPosL -= BB_TWO_PI;
							}
							BB_Voice[voice].sinPosR = BB_Voice[voice].sinPosL;

							// now determine whether tone or silence:
							// Set toggle-time for current beat polarity (for
							// user external use)
							if (1 > --BB_Voice[voice].cur_beatfreq_phasesamplecount) {
								BB_Voice[voice].cur_beatfreq_phasesamplecount = BB_Voice[voice].cur_beatfreq_phasesamplecount_start;
								if (0 != BB_Voice[voice].cur_beatfreq_phaseflag) {
									BB_Voice[voice].cur_beatfreq_phaseflag = 0;
								} else {
									BB_Voice[voice].cur_beatfreq_phaseflag = 1;
								}

								BB_Voice[voice].cur_beatfreq_phaseenvelope = 0;
							}

							// there are probably shortcuts to avoid doing a
							// sine for every sample, but I don't know them:
							BB_Voice[voice].sinR = BB_Voice[voice].sinL = Math
									.sin(BB_Voice[voice].sinPosL);

							if (0 != BB_Voice[voice].cur_beatfreq_phaseflag) {
								Sample_left = BB_Voice[voice].sinL
										* BB_SIN_SCALER
										* (1.0 - BB_Voice[voice].cur_beatfreq_phaseenvelope);

								// Decide if it is alternating or not:
								if (0 == iso_alternating) {
									Sample_right = Sample_left;
								} else {
									Sample_right = BB_Voice[voice].sinL
											* BB_SIN_SCALER
											* BB_Voice[voice].cur_beatfreq_phaseenvelope;
								}
							} else {
								Sample_left = BB_Voice[voice].sinL
										* BB_SIN_SCALER
										* BB_Voice[voice].cur_beatfreq_phaseenvelope;
								if (0 == iso_alternating) {
									Sample_right = Sample_left;
								} else {
									Sample_right = BB_Voice[voice].sinL
											* BB_SIN_SCALER
											* (1.0 - BB_Voice[voice].cur_beatfreq_phaseenvelope);
								}
							}

							// cur_beatfreq_data is used arbitrarily as a
							// Modulator here (to reduce harmonics in transition
							// between on and off pulses)
							if ((BB_Voice[voice].cur_beatfreq_phaseenvelope += .01) > 1)
								BB_Voice[voice].cur_beatfreq_phaseenvelope = 1;
						}
							break;

						// ---------B-------------
						// waterdrops take drop count from BeatFreq
						case BB_VOICETYPE_WATERDROPS: {
							if (null == BB_Voice[voice].Drop)
								break;
							float dropthresh = (float) BB_Voice[voice].cur_basefreq;

							// make rain:
							int p;
							int mixL = 0, mixR = 0;
							for (p = 0; p < BB_Voice[voice].PCM_samples_size; p++) {
								if (0 <= BB_Voice[voice].Drop[p].count) {
									if (0 != BB_Voice[voice].mono) {
										mixL += BB_DropMother[(int) BB_Voice[voice].Drop[p].count];
										mixR += BB_DropMother[(int) BB_Voice[voice].Drop[p].count];
									} else {
										mixL += (int) (BB_DropMother[(int) BB_Voice[voice].Drop[p].count] * BB_Voice[voice].Drop[p].stereoMix);
										mixR += (int) (BB_DropMother[(int) BB_Voice[voice].Drop[p].count] * (1.0f - BB_Voice[voice].Drop[p].stereoMix));
									}
									BB_Voice[voice].Drop[p].count -= BB_Voice[voice].Drop[p].decrement;
								} else if (dropthresh > Math.random()) {
									// load up a drop:
									BB_Voice[voice].Drop[p].count = BB_DROPLEN - 1;
									// give it a random pitch:
									BB_Voice[voice].Drop[p].decrement = (float) ((Math
											.random() * BB_WaterWindow) + BB_DropLowcut);
									// place it somewhere in the stereo mix:
									BB_Voice[voice].Drop[p].stereoMix = (float) Math
											.random();
								}
							}

							// do just a touch of lo-pass filtering on it:
							Sample_left = BB_Voice[voice].noiseL = (((BB_Voice[voice].noiseL * 31) + mixL) >> 5);
							Sample_right = BB_Voice[voice].noiseR = (((BB_Voice[voice].noiseR * 31) + mixR) >> 5);
						}
							break;

						// ---------B-------------
						case BB_VOICETYPE_RAIN: {
							if (null == BB_Voice[voice].Drop)
								break;
							float dropthresh = (float) BB_Voice[voice].cur_basefreq;

							// make rain:
							int p;
							int mixL = 0, mixR = 0;
							for (p = 0; p < BB_Voice[voice].PCM_samples_size; p++) {
								if (0 <= BB_Voice[voice].Drop[p].count) {
									if (0 != BB_Voice[voice].mono) {
										mixL += BB_RainMother[(int) BB_Voice[voice].Drop[p].count];
										mixR += BB_RainMother[(int) BB_Voice[voice].Drop[p].count];
									} else {
										mixL += (int) (BB_RainMother[(int) BB_Voice[voice].Drop[p].count] * BB_Voice[voice].Drop[p].stereoMix);
										mixR += (int) (BB_RainMother[(int) BB_Voice[voice].Drop[p].count] * (1.0f - BB_Voice[voice].Drop[p].stereoMix));
									}
									BB_Voice[voice].Drop[p].count -= BB_Voice[voice].Drop[p].decrement;
								} else if (dropthresh > Math.random()) {
									// load up a drop:
									BB_Voice[voice].Drop[p].count = BB_RAINLEN - 1;
									// give it a random pitch:
									BB_Voice[voice].Drop[p].decrement = (float) ((Math
											.random() * BB_WaterWindow) + BB_RainLowcut);
									// place it somewhere in the stereo mix:
									BB_Voice[voice].Drop[p].stereoMix = (float) Math
											.random();
								}
							}

							// do just a touch of lo-pass filtering on it:
							Sample_left = BB_Voice[voice].noiseL = (((BB_Voice[voice].noiseL * 31) + mixL) >> 5);
							Sample_right = BB_Voice[voice].noiseR = (((BB_Voice[voice].noiseR * 31) + mixR) >> 5);
						}
							break;

						default:
							break;
						}
						// handle stereo/mono mixing:
						if (0 == BB_Voice[voice].mono) {
							sumL += (Sample_left * BB_Voice[voice].CurVolL);
							sumR += (Sample_right * BB_Voice[voice].CurVolR);
						} else {
							Sample_left = (Sample_left + Sample_right) * 0.5;
							sumL += (Sample_left * BB_Voice[voice].CurVolL);
							sumR += (Sample_left * BB_Voice[voice].CurVolR);
						}
					} // END "Voice NOT muted"
					// ####END high priority calculations (done for every
					// sample)
				} // END Voices loop

			BB_InCriticalLoopFlag = false;

			// Finally, load the array with the mixed audio:
			// In C, this is the approach:
			if (0 != BB_Mono) {
				sumL = .5 * (sumL + sumR);
				sumR = sumL;
			}

			// quick and dirty overall volume implementation:
			if (BB_VolumeOverall_left != 1.0) {
				sumL *= BB_VolumeOverall_left;
			}

			if (BB_VolumeOverall_right != 1.0) {
				sumR *= BB_VolumeOverall_right;
			}

			if (0 == BB_StereoSwap) {
				// the big moment (Java variety):
				pSample[pSample_index++] = (byte) (((short) sumL) & 0xFF);
				pSample[pSample_index++] = (byte) (((short) sumL) >> 8);
				pSample[pSample_index++] = (byte) (((short) sumR) & 0xFF);
				pSample[pSample_index++] = (byte) (((short) sumR) >> 8);
			} else {
				pSample[pSample_index++] = (byte) (((short) sumR) & 0xFF);
				pSample[pSample_index++] = (byte) (((short) sumR) >> 8);
				pSample[pSample_index++] = (byte) (((short) sumL) & 0xFF);
				pSample[pSample_index++] = (byte) (((short) sumL) >> 8);
			}

			// In Java, this is the equivalent:
			// pSample[pSample_index++] = (byte) (((short)sumL) & 0xFF);
			// pSample[pSample_index++] = (byte) (((short)sumL) >> 8);
			// pSample[pSample_index++] = (byte) (((short)sumR) & 0xFF);
			// pSample[pSample_index++] = (byte) (((short)sumR) >> 8);

			if (updateperiod == 0) {
				updateperiod = BB_UPDATEPERIOD_SAMPLES;
				BB_CurrentSampleCount += BB_UPDATEPERIOD_SAMPLES; // NOTE: I
				// could
				// also just
				// do
				// BB_CurrentSampleCount++
				// for each
				// sample...
			}
		} // END samplecount
		// END Fill sound buffer
	}

	// ======================================
	// =============================================
	// ~ The McGill Super-Duper Random Number Generator
	// ~ G. Marsaglia, K. Ananthanarayana, N. Paul
	// ~ Incorporating the Ziggurat method of sampling from decreasing
	// ~ or symmetric unimodal density functions.
	// ~ G. Marsaglia, W.W. Tsang
	// ~ Rewritten into C by E. Schneider
	// ~ [Date last modified: 05-Jul-1997]
	// =============================================
	static final int ML_MULT = 69069;
	long mcgn, srgn;

	// Need to also define these in .h file:
	// unsigned long mcgn, srgn;
	// IMPORTANT NOTE:when using a fixed i2, for some reason Seed pairs
	// for i1 like this:
	// even
	// even+1
	// produce idential sequences when r2 returned (r1 >> 12).
	// Practically, this means that 2 and 3
	// produce one landscape; likewise 6 and 7, 100 and 101, etc.
	// This is why i do the dopey "add 1" to i2
	// ALSO, JUST DON'T USE 0 FOR i1 or i2. PLEASE
	void SeedRand(int i1, int i2)
	// void rstart (long i1, long i2)
	{
		if (i2 == (int) -1)
			i2 = i1 + 1; // yech
		mcgn = (long) ((i1 == 0L) ? 0L : i1 | 1L);
		srgn = (long) ((i2 == 0L) ? 0L : (i2 & 0x7FFL) | 1L);
	}

	// ======================================
	// returns int from -2^31 to +2^31; "PM" means "PlusMinus"
	// Be sure to have seeded rand before calling this -- something like this:
	// SeedRand (3676, 2676862); //anything but 0 on either or twins seems OK
	int rand() {
		long r0, r1;
		r0 = (srgn >> 15);
		r1 = srgn ^ r0;
		r0 = (r1 << 17);
		srgn = r0 ^ r1;
		mcgn = ML_MULT * mcgn;
		r1 = mcgn ^ srgn;
		return (int) r1;
	}

	// ///////////////////////////////////////////////////
	// Only called internally; user input should be through
	// BB_SetVolume() and BB_SetBalance().
	void BB_ProcessVolBal() {
		BB_VolumeOverall_right = BB_VolumeOverall_left = BB_OverallVolume;
		if (BB_OverallBalance > 0) { // that means attenuate Left:
			BB_VolumeOverall_left *= (1.f - Math.abs(BB_OverallBalance));
		} else { // that means attenuate Right:
			BB_VolumeOverall_right *= (1.f - Math.abs(BB_OverallBalance));
		}
	}

	// ///////////////////////////////////////////////////
	// Valid Balance input can be from -1.0 to 1.0; 0.0 is middle
	void BB_SetBalance(double range) {
		BB_OverallBalance = range;
		BB_ProcessVolBal();
	}

	// ///////////////////////////////////////////////////
	// Valid Volume input can be from 0.0 to 1.0
	void BB_SetVolume(double range) {
		BB_OverallVolume = range;
		BB_ProcessVolBal();
	}

	// ///////////////////////////////////////////////////
	void BB_Reset() {
		BB_CurrentSampleCount = BB_CurrentSampleCountLooped = 0;
		BB_InfoFlag &= ~BB_COMPLETED;
		BB_LoopCount = BB_Loops;
	}

	// ///////////////////////////////////////////////////
	double BB_GetTopBeatfreq(int voice) {
		double top_beatfreq = 0;
		int e;
		for (e = 0; e < (BB_Voice[voice].EntryCount); e++) {
			if (top_beatfreq < Math
					.abs(BB_Voice[voice].Entry[e].beatfreq_start_HALF)) {
				top_beatfreq = Math
						.abs(BB_Voice[voice].Entry[e].beatfreq_start_HALF);
			}
		}
		return top_beatfreq;
	}

	// ======================================
	// this function is to assist user in cleaning up any PCM data
	// they opened to use here; when voice PCM_samples == NULL, it gets
	// ignored by audio engine.
	void BB_NullAllPCMData() {
		int i;

		for (i = 0; i < BB_VoiceCount; i++) {
			BB_Voice[i].PCM_samples = null;
			BB_Voice[i].PCM_samples_size = 1; // this is set to 1 simply so that
			// there is never a divide by
			// zero situation.
			BB_Voice[i].PCM_samples_currentcount = 0;
		}
	}

	// =========================================
	// 20110616
	void BB_ToneRain(short[] array, float pitch) {
		double p = 0;
		double q = 1.0 / pitch;
		double r = 0;
		double s = 0x7fff / (double) array.length;
		for (int i = 0; i < array.length; i++) {
			array[i] = (short) (r * Math.sin(p * Math.PI * 2.));
			p += q;
			r += s;
		}
	}

	// ///////////////////////////////////////////////////
	// this is a one voice Binaural Beat schedule copying
	// the default used in Gnaural2. Order is:
	// duration, volume_left, volume_right, beatfreq, basefreq,
	static final float BB_DefaultBBSched[] = { 9f, 0.72f, 0.72f, 0f, 262.35f,
			45f, 0.73f, 0.73f, 12f, 262.1f, 60f, 0.73f, 0.73f, 8f, 260.83f,
			60f, 0.73f, 0.73f, 6f, 259.14f, 120f, 0.73f, 0.73f, 5f, 257.45f,
			180f, 0.73f, 0.73f, 4.3f, 254.07f, 180f, 0.74f, 0.74f, 4f, 249f,
			6f, 0.74f, 0.74f, 3.9f, 243.94f, 6f, 0.74f, 0.74f, 7f, 243.77f,
			360f, 0.74f, 0.74f, 3.9f, 243.6f, 6f, 0.75f, 0.75f, 4.2f, 233.47f,
			6f, 0.75f, 0.75f, 7f, 233.3f, 180f, 0.75f, 0.75f, 3.9f, 233.13f,
			180f, 0.76f, 0.76f, 4f, 228.06f, 6f, 0.77f, 0.77f, 3.9f, 222.99f,
			6f, 0.77f, 0.77f, 7f, 222.82f, 340f, 0.77f, 0.77f, 3.9f, 222.66f,
			6f, 0.78f, 0.78f, 4.2f, 213.08f, 6f, 0.78f, 0.78f, 7f, 212.91f,
			180f, 0.78f, 0.78f, 4f, 212.75f, 180f, 0.78f, 0.78f, 4.2f, 207.68f,
			6f, 0.79f, 0.79f, 3.8f, 202.61f, 6f, 0.79f, 0.79f, 7f, 202.44f,
			400f, 0.79f, 0.79f, 3.9f, 202.27f, 6f, 0.8f, 0.8f, 4.2f, 191.01f,
			6f, 0.8f, 0.8f, 7f, 190.84f, 180f, 0.8f, 0.8f, 4.2f, 190.67f, 180f,
			0.8f, 0.8f, 3.9f, 185.61f, 6f, 0.81f, 0.81f, 4f, 180.54f, 6f,
			0.81f, 0.81f, 7f, 180.37f, 300f, 0.81f, 0.81f, 4f, 180.2f, 6f,
			0.82f, 0.82f, 3.8f, 171.76f, 6f, 0.82f, 0.82f, 7f, 171.59f, 180f,
			0.82f, 0.82f, 3.9f, 171.42f, 180f, 0.82f, 0.82f, 4.1f, 166.35f, 6f,
			0.83f, 0.83f, 3.9f, 161.28f, 6f, 0.83f, 0.83f, 7f, 161.11f, 360f,
			0.83f, 0.83f, 3.9f, 160.94f, 6f, 0.84f, 0.84f, 4.1f, 150.81f, 6f,
			0.84f, 0.84f, 7f, 150.64f, 180f, 0.84f, 0.84f, 3.9f, 150.47f, 180f,
			0.84f, 0.84f, 3.6f, 145.41f, 6f, 0.85f, 0.85f, 4f, 140.34f, 6f,
			0.85f, 0.85f, 7f, 140.17f, 64f, 0.85f, 0.85f, 4.3f, 140f };
	// //////////////////////////////////
} // end BinauralBeatSoundEngine class
