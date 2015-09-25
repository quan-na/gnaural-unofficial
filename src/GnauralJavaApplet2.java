/*
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
///////////////////////////////////////////////////////////////////////////////
/*
 File: GnauralApplet.java
 Author: Bret Logan
 First Release Date: March 17, 2007
 next: 20080305
 Current Release: 20110422
 */
///////////////////////////////////////////////////////////////////////////////

import java.applet.*;
import java.awt.*;
import java.awt.event.*; //import java.awt.event.MouseEvent;

//import java.io.Serializable;
import java.io.*;
import java.util.Date;
import java.text.SimpleDateFormat;

//TODO:
// Urgent:
// - solve "wait audio thread is dead" approach -- it keeps getting in endless loops
// - see if using ctrlGain.setValue(gain) is better than the signal processing approach I currently use
// - hitting stop currently leaves nothing ready to play; keep last played schedule in queue.
// - currently, pause/play doesn't act immediately; seems to drain dataline, and passes some crap at startup.
// - in status area, have label indicating playing or not, etc.
// - implement one-second update timer for info
// - figure out why cpu usage goes off the chart when I'm paused or reloaded a bunch of times
// Nonurgent:
// - put in a panel with graph views; can't edit it, but it will at least show current schedule views
public class GnauralJavaApplet2 extends Applet implements Runnable, ActionListener,
		AdjustmentListener, ItemListener, Serializable, MouseListener {
	private static final long serialVersionUID = 7526471155622776147L;
	private static final int GNAURAL_SLIDER_MAX = 1000;
	private static final float GNAURAL_SLIDER_INV = 1.035f / GNAURAL_SLIDER_MAX;
	private static final int GNAURAL_SLIDER_WIDTH = 32;
	private static final float GNAURAL_FREQBASE_MAX = 2000.f;
	private static final float GNAURAL_FREQBEAT_MAX = 16.f;

	GnauralGraphView MyGnauralGraphView;
	BinauralBeatSoundEngine BB;
	GnauralReadXMLFile MyScheduleXML;
	AudioThread MyAudioThread;
	WAVWriteThread MyWAVWriteThread = null;
	static final String sFiller = "abcdefghijklmnopqrs";
	String sPlay = " Play ";
	String sPause = "Pause";
	String sStop = "Stop";
	String sFastForward = "Forward";
	String sRewind = "Rewind";
	String sLoad = "Load File";
	String sSave = "Save Data";
	String sSaveWAV = "Save WAV File";
	// START GUI Components:
	Button button_load, button_save, button_saveWAV, button_play, button_stop,
			button_fastforward, button_rewind;
	Label lFilename, lInfo, lCurTime, lSliderBalanceOverall, lEndtime,
			lSliderVolumeOverall, lSliderVolumeTone, lSliderVolumeNoise,
			lTTime, lError, lFreqBeat, lFreqBase;
	Scrollbar sliderVolumeOverall, sliderBalanceOverall, sliderManualBasefreq,
			sliderManualBeatfreq, sliderVolumeTone, sliderVolumeNoise;
	Panel pDrawingPanel, pProgressPanel;
	Checkbox cbManualBeatfreq, cbManualBasefreq, cbManualVolumeTone,
			cbManualVolumeNoise;

	Thread tDisplay;
	boolean bQuit = false;
	boolean bNewFile = false;
	String schedule_filename;

	// int GUI_GraphPositionX=0;//the X equivalent to where we are in the
	// Schedule; used to determine if GUI updates are needed in
	// graph/progressbar, etc.
	// int GUI_GraphPositionX_old=0xFFFFF;
	// boolean GUI_UpdateFlag = true;
	// END GUI Components:

	// The method that will be automatically called when the applet is started
	public void init() {
		// String schedule_filename;
		initGUI();

		tDisplay = new Thread(this);
		tDisplay.start();

		// for a default voice:
		System.out.println("Current working directory: "
				+ System.getProperty("user.dir"));
		try {
			schedule_filename = getParameter("schedule_filename");
			System.out.println("Gnaural: Using file " + schedule_filename);
		} catch (Exception e) {
			System.out
					.println("Gnaural: couldn't a user designated file, so looking for a default schedule.gnaural");
			schedule_filename = "schedule.gnaural";
		}

		// System.out.println("Current Directory: "+System.getProperty("user.dir"));
		// System.out.println(System.getProperty("user.dir")+File.separator+schedule_filename);
		BB = new BinauralBeatSoundEngine(44100, 2);
		MyGnauralGraphView = new GnauralGraphView(pDrawingPanel, BB);
		BB.BB_PauseFlag = false;// 20110412: required; seems like a good place
		MyScheduleXML = new GnauralReadXMLFile(schedule_filename, BB);
		MyGnauralGraphView.Graph_CalibrateEvents();
		bNewFile = true; // This just alerts user to update stuff; user must set
							// this false after done

		MyAudioThread = new AudioThread(BB);
	}

	// This method gets called when the applet is terminated
	// That's when the user goes to another page or exits the browser.
	public void stop() {
		MyAudioThread.quit = true;
		bQuit = true;
		// MyAudioThread.Cleanup();
	}

	public void paint(Graphics g) {
		// g.drawString(System.getProperty("user.dir"), 3, 10);
		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// g.drawString("Sound Finished", 10, 25);
//		if (bNewFile == false) MyGnauralGraphView.Graph_Draw();
		//MyGnauralGraphView.Graph_Draw();
		UpdateGUI_Primary();
	}

	public void run() {
		while (bQuit == false) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}
			UpdateGUI_Primary();
		}
	}

	public void initGUI() {
		lFilename = new Label("");
		lInfo = new Label("Welcome To Gnaural Java!");
		lCurTime = new Label("lCurTime");
		lTTime = new Label("lTTime");
		lEndtime = new Label("lEndtime");
		lEndtime.setForeground(java.awt.Color.red);
		lSliderVolumeOverall = new Label(sFiller);
		lSliderBalanceOverall = new Label(sFiller);
		lError = new Label(
				"Gnaural Binaural Beat Generator for Java   see http://gnaural.sourceforge.net   Bret Logan (c) 20150925");
		lError.setForeground(java.awt.Color.pink);
		lError.setBackground(java.awt.Color.black);
		lFreqBeat = new Label(sFiller);
		lFreqBase = new Label(sFiller);
		lSliderVolumeTone = new Label(sFiller);
		lSliderVolumeNoise = new Label(sFiller);

		button_load = new Button(sLoad);
		button_load.addActionListener(this);

		button_save = new Button(sSave);
		button_save.addActionListener(this);

		button_saveWAV = new Button(sSaveWAV);
		button_saveWAV.addActionListener(this);

		button_play = new Button(sPlay);
		button_play.addActionListener(this);

		button_rewind = new Button(sRewind);
		button_rewind.addActionListener(this);

		button_fastforward = new Button(sFastForward);
		button_fastforward.addActionListener(this);

		button_stop = new Button(sStop);
		button_stop.addActionListener(this);

		cbManualBeatfreq = new Checkbox("Manual Beat"); // Default state is
														// "off" (false).
		cbManualBeatfreq.addItemListener(this);
		cbManualBasefreq = new Checkbox("Manual Base");
		cbManualBasefreq.addItemListener(this);
		cbManualVolumeTone = new Checkbox("Manual Tone Vol.");
		cbManualVolumeTone.addItemListener(this);
		cbManualVolumeNoise = new Checkbox("Manual Noise Vol.");
		cbManualVolumeNoise.addItemListener(this);

		sliderVolumeOverall = new Scrollbar(Scrollbar.HORIZONTAL, 0,
				GNAURAL_SLIDER_WIDTH, 0, GNAURAL_SLIDER_MAX);
		sliderVolumeOverall.addAdjustmentListener(this);
		sliderBalanceOverall = new Scrollbar(Scrollbar.HORIZONTAL, 0,
				GNAURAL_SLIDER_WIDTH, 0, GNAURAL_SLIDER_MAX);
		sliderBalanceOverall.addAdjustmentListener(this);
		sliderManualBeatfreq = new Scrollbar(Scrollbar.HORIZONTAL, 0,
				GNAURAL_SLIDER_WIDTH, 0, GNAURAL_SLIDER_MAX);
		sliderManualBeatfreq.addAdjustmentListener(this);
		sliderManualBasefreq = new Scrollbar(Scrollbar.HORIZONTAL, 0,
				GNAURAL_SLIDER_WIDTH, 0, GNAURAL_SLIDER_MAX);
		sliderManualBasefreq.addAdjustmentListener(this);
		sliderVolumeTone = new Scrollbar(Scrollbar.HORIZONTAL, 0,
				GNAURAL_SLIDER_WIDTH, 0, GNAURAL_SLIDER_MAX);
		sliderVolumeTone.addAdjustmentListener(this);
		sliderVolumeNoise = new Scrollbar(Scrollbar.HORIZONTAL, 0,
				GNAURAL_SLIDER_WIDTH, 0, GNAURAL_SLIDER_MAX);
		sliderVolumeNoise.addAdjustmentListener(this);

		pDrawingPanel = new Panel();
		pProgressPanel = new Panel();

		// Set layout:
		GridBagLayout gb = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		setLayout(gb);

		// START filling the layout:
		// remember: constraints only get applied if you call gb.setConstraints
		// on the component
		c.anchor = GridBagConstraints.NORTH;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.weighty = 0.0;
		c.gridx = GridBagConstraints.RELATIVE;
		c.gridy = GridBagConstraints.RELATIVE;
		c.ipadx = 0;
		c.ipady = 0;
		c.insets = new Insets(0, 0, 0, 0);

		addGrabLabel(lInfo, gb, c);
		addGrabLabel(lFilename, gb, c);
		addGrabLabel(lCurTime, gb, c);
		addGrabLabel(lTTime, gb, c);

		// Manual beat:
		c.gridwidth = 1;
		addGrabLabel(lFreqBeat, gb, c);
		add(cbManualBeatfreq);
		c.gridwidth = GridBagConstraints.REMAINDER;
		gb.setConstraints(sliderManualBeatfreq, c);
		add(sliderManualBeatfreq);

		// Manual base:
		c.gridwidth = 1;
		addGrabLabel(lFreqBase, gb, c);
		add(cbManualBasefreq);
		c.gridwidth = GridBagConstraints.REMAINDER;
		gb.setConstraints(sliderManualBasefreq, c);
		add(sliderManualBasefreq);

		// Noise Volume:
		c.gridwidth = 1;
		addGrabLabel(lSliderVolumeNoise, gb, c);
		add(cbManualVolumeNoise);
		c.gridwidth = GridBagConstraints.REMAINDER;
		gb.setConstraints(sliderVolumeNoise, c);
		add(sliderVolumeNoise);

		// Tone Volume:
		c.gridwidth = 1;
		addGrabLabel(lSliderVolumeTone, gb, c);
		add(cbManualVolumeTone);
		c.gridwidth = GridBagConstraints.REMAINDER;
		gb.setConstraints(sliderVolumeTone, c);
		add(sliderVolumeTone);

		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weighty = 0.0;
		c.weightx = 0.0;
		addGrabLabel(lError, gb, c);

		c.gridwidth = 1;
		// c.fill = GridBagConstraints.HORIZONTAL;
		c.fill = GridBagConstraints.BOTH;
		c.weighty = 0.1;
		c.weightx = 1.0;
		gb.setConstraints(button_play, c);
		add(button_play);
		gb.setConstraints(button_rewind, c);
		add(button_rewind);
		gb.setConstraints(button_fastforward, c);
		add(button_fastforward);
		c.gridwidth = GridBagConstraints.REMAINDER;
		gb.setConstraints(button_stop, c);
		add(button_stop);
		// c.gridwidth = GridBagConstraints.REMAINDER;
		// gb.setConstraints(button_load, c);
		// add(button_load);

		// Setup the Progress Panel:
		c.anchor = GridBagConstraints.CENTER;
		c.weighty = .1;
		c.weightx = 1.0;
		gb.setConstraints(pProgressPanel, c);
		add(pProgressPanel);
		pProgressPanel.addMouseListener(this);

		c.anchor = GridBagConstraints.CENTER;
		c.weighty = 1.0;
		c.weightx = 1.0;
		gb.setConstraints(pDrawingPanel, c);
		add(pDrawingPanel);
		pDrawingPanel.addMouseListener(this);

		// Reset some GUI stuff before continuing:
		c.anchor = GridBagConstraints.NORTH;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.weighty = 0.0;
		c.gridx = GridBagConstraints.RELATIVE;
		c.gridy = GridBagConstraints.RELATIVE;
		c.ipadx = 0;
		c.ipady = 0;
		c.insets = new Insets(0, 0, 0, 0);

		// file handling buttons:
		c.gridwidth = 1;
		gb.setConstraints(button_load, c);
		add(button_load);
		gb.setConstraints(button_save, c);
		add(button_save);
		c.gridwidth = GridBagConstraints.REMAINDER;
		gb.setConstraints(button_saveWAV, c);
		add(button_saveWAV);

		c.gridwidth = 1;
		// c.gridwidth = GridBagConstraints.REMAINDER;
		addGrabLabel(lSliderBalanceOverall, gb, c);
		c.gridwidth = GridBagConstraints.REMAINDER;
		gb.setConstraints(sliderBalanceOverall, c);
		add(sliderBalanceOverall);

		// Overall Volume:
		c.gridwidth = 1;
		// c.gridwidth = GridBagConstraints.REMAINDER;
		addGrabLabel(lSliderVolumeOverall, gb, c);
		c.gridwidth = GridBagConstraints.REMAINDER;
		gb.setConstraints(sliderVolumeOverall, c);
		add(sliderVolumeOverall);
		// END filling the layout:
		validate();
	}

	// //////////////////////////////////////
	protected void addGrabLabel(Label lbl, GridBagLayout gb,
			GridBagConstraints c) {
		gb.setConstraints(lbl, c);
		add(lbl);
	}

	// //////////////////////////////////////
	// precision is how many digits to return; 5 max
	public float Truncate(float val, int precision) {
		switch (precision) {
		case 0:
			val = Math.round(val);
			break;

		case 1:
			val = Math.round(val * 10) * .1f;
			break;

		case 2:
			val = Math.round(val * 100) * .01f;
			break;

		case 3:
			val = Math.round(val * 1000) * .001f;
			break;

		case 4:
			val = Math.round(val * 10000) * .0001f;
			break;

		case 5:
			val = Math.round(val * 100000) * .00001f;
			break;
		}
		return val;
	}

	// //////////////////////////////////////
	public void SetManualSliderTexts() {
		if (BB.BB_VoiceCount > 1) {
			lFreqBase.setText("Base Frequency: "
					+ (float) BB.BB_Voice[0].cur_basefreq);
			// cbManualBasefreq.setLabel("Base Freq.: " + (float)
			// BB.BB_Voice[0].cur_basefreq);
			lFreqBeat.setText("Beat Frequency: "
					+ (float) (2.f * Math.abs(BB.BB_Voice[0].cur_beatfreq)));
			lSliderVolumeTone.setText("Tone Volume: "
					+ (float) BB.BB_Voice[0].CurVolL);
			lSliderVolumeNoise.setText("Noise Volume: "
					+ (float) BB.BB_Voice[1].CurVolL);

			// cbManualBeatfreq.setLabel("Beat Freq.: "+ (float)
			// (2.f*Math.abs(BB.BB_Voice[0].cur_beatfreq)));
			/*
			 * cbManualBasefreq.setLabel("Base Freq.: " +
			 * (int)(sliderManualBasefreq.getValue() * 1.f)); if (true ==
			 * cbManualBeatfreq.getState()){
			 * cbManualBeatfreq.setLabel("Beat Freq.: "
			 * +(int)(sliderManualBeatfreq.getValue() * 1.f)); } else {
			 * cbManualBeatfreq.setLabel("Man. Beat: Off"); }
			 */
		} else {
			lFreqBase.setText("");
			lFreqBeat.setText("");
			lSliderVolumeTone.setText("");
			lSliderVolumeNoise.setText("");
		}
	}

	// //////////////////////////////////////
	public void SetVolumeSliderTexts() {
		lSliderVolumeOverall.setText("Overall Volume: "
				+ (float) (BB.BB_OverallVolume));
		lSliderBalanceOverall.setText("Overall Balance: "
				+ (int) (BB.BB_OverallBalance * 100.f));
	}

	// ==========START EVENT HANDLERS==========
	// scrollbar handler:
	public void adjustmentValueChanged(AdjustmentEvent ae) {
		Adjustable source = ae.getAdjustable();

		if (source == sliderVolumeOverall) {
			// this is BB's way of doing Volume:
			BB.BB_SetVolume(LinearToExponential(ae.getValue()
					* GNAURAL_SLIDER_INV));
			SetVolumeSliderTexts();
			// System.out.println(BB.BB_VolumeOverall);
			return;
		}

		if (source == sliderBalanceOverall) {
			// this is BB's way of doing Volume:
			float tmpy = (-.5f + (ae.getValue() * GNAURAL_SLIDER_INV)) * 2.f;
			BB.BB_SetBalance(tmpy);
			// BB.BB_SetBalance(LinearToExponential(ae.getValue() *
			// GNAURAL_SLIDER_INV));
			SetVolumeSliderTexts();
			// System.out.println(BB.BB_VolumeOverall);
			return;
		}

		if (source == sliderManualBeatfreq) {
			SetManuals();
			// System.out.println(BB.BB_VolumeOverall);
			return;
		}

		if (source == sliderManualBasefreq) {
			SetManuals();
			// System.out.println(BB.BB_VolumeOverall);
			return;
		}

		if (source == sliderVolumeNoise) {
			SetManuals();
			// System.out.println("VolNoise");
			return;
		}

		if (source == sliderVolumeTone) {
			SetManuals();
			// System.out.println("VolTone");
			return;
		}
	}

	// button handler:
	public void actionPerformed(ActionEvent ae) {
		if (ae.getActionCommand().equals(sLoad)) {
			// this just creates a dialog box, but security makes debugging with
			// it excruciating.
			// see here for info on signing a jar file:
			// http://www.captain.at/programming/java/
			schedule_filename = loadFile(new Frame(),
					"Choose a Gnaural schedule file:", ".gnaural", "*.gnaural",
					FileDialog.LOAD);
			if (schedule_filename != null) {
				main_Stop();
				MyScheduleXML = new GnauralReadXMLFile(schedule_filename, BB);
				bNewFile = true;
				/*
				 * MyAudioThread.quit = true; while (MyAudioThread.ThreadIsAlive
				 * == true) try {
				 * System.out.println("waiting for Audio thread to quit...");
				 * Thread.sleep (1000); } catch (InterruptedException e) {
				 * e.printStackTrace(); } MyAudioThread = null; MyAudioThread =
				 * new AudioThread(schedule_filename);
				 */
				button_play.setLabel(sPlay);
				MyGnauralGraphView.Graph_CalibrateEvents();
			}
			return;
		}

		if (ae.getActionCommand().equals(sSave)) {
			String tmpfilename = loadFile(new Frame(),
					"Save Gnaural schedule file:", ".gnaural",
					"schedule.gnaural", FileDialog.SAVE);
			if (tmpfilename != null) {
				File file = new File(tmpfilename);
				if (file.exists()) {
					lInfo
							.setText("Output file existed, deleting it... Wrote to "
									+ tmpfilename);
				} else {
					lInfo.setText("Wrote schedule file to " + tmpfilename);
				}
				WriteScheduleToFile(tmpfilename);
			}
			return;
		}

		if (ae.getActionCommand().equals(sSaveWAV)) {
			if (BB.BB_Loops < 1) {
				lInfo
						.setText("Not writing file because schedule in in endess loop mode.");
				return;
			} // simply won't allow user to create WAV file in infinite loop
				// mode
			if (null != MyWAVWriteThread) {
				lInfo
						.setText("Not writing file because one is already being written.");
				return;
			}
			String tmpfilename = loadFile(new Frame(),
					"Create WAV Sound file from schedule file:", ".gnaural",
					"Gnaural.wav", FileDialog.SAVE);
			if (tmpfilename != null) {
				File file = new File(tmpfilename);
				if (file.exists()) {
					lInfo
							.setText("Output file existed, deleting it... Writing WAV file to "
									+ tmpfilename);
				} else {
					lInfo.setText("Writing WAV file to " + tmpfilename);
				}
				MyAudioThread.pause = true;
				main_Stop();
				BB.BB_Reset(); // got to just hope user doesn't start pressing
								// stuff while WAV is being made!
				MyWAVWriteThread = new WAVWriteThread(BB, tmpfilename);
			}
			return;
		}

		if (ae.getActionCommand().equals(sPlay)) {
			button_play.setLabel(sPause);
			MyAudioThread.pause = false;
			return;
		}

		if (ae.getActionCommand().equals(sPause)) {
			button_play.setLabel(sPlay);
			MyAudioThread.pause = true;
			return;
		}

		if (ae.getActionCommand().equals(sRewind)) {
			ForwardRewind(-.06f);
			return;
		}

		if (ae.getActionCommand().equals(sFastForward)) {
			ForwardRewind(.05f);
			return;
		}

		if (ae.getActionCommand().equals(sStop)) {
			main_Stop();
			return;
		}
	}

	// checkbox handler:
	public void itemStateChanged(ItemEvent e) {
		// Object source = e.getItem();
		Object source = e.getItemSelectable();
		if (BB.BB_VoiceCount > 1) {
			if (source == cbManualBeatfreq) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					BB.BB_Voice[0].ManualFreqBeatControl = 1;
					SetManuals();
				} else {
					BB.BB_Voice[0].ManualFreqBeatControl = 0;
				}
				return;
			}

			if (source == cbManualBasefreq) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					BB.BB_Voice[0].ManualFreqBaseControl = 1;
					SetManuals();
				} else {
					BB.BB_Voice[0].ManualFreqBaseControl = 0;
				}
				return;
			}

			if (source == cbManualVolumeNoise) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					BB.BB_Voice[1].ManualVolumeControl = 1;
					SetManuals();
				} else {
					BB.BB_Voice[1].ManualVolumeControl = 0;
				}
				return;
			}

			if (source == cbManualVolumeTone) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					BB.BB_Voice[0].ManualVolumeControl = 1;
					SetManuals();
				} else {
					BB.BB_Voice[0].ManualVolumeControl = 0;
				}
				return;
			}
		}
	}

	public void eventOutput(String tmpstr, MouseEvent e) {

		System.out.println("MouseEvent: Description: " + tmpstr + " Event: "
				+ e);
	}

	// now a biggie: Mouse handlers:
	public void mousePressed(MouseEvent e) {
		// eventOutput("Mouse pressed (# of clicks: " + e.getClickCount() + ")",
		// e);
		// System.out.println(e.getMouseModifiersText(InputEvent.SHIFT_MASK |
		// InputEvent.ALT_MASK | InputEvent.CTRL_MASK));
		// System.out.println(java.awt.event.MouseEvent.getMouseModifiersText(e.getModifiers()));
		// System.out.println(java.awt.event.MouseEvent.getMouseModifiersText(InputEvent.SHIFT_MASK));
		if ((e.getModifiers() & InputEvent.BUTTON1_MASK) != 0) {
			if (e.getSource().equals(pProgressPanel)) {
				ProgressBarButtonPress(e.getX());
				// System.out.println ("In Progress Panel, x=" + x);
			} else {
				eventOutput("button1 pressed", e);
			}
		}
		if ((e.getModifiers() & InputEvent.BUTTON2_MASK) != 0) {
			eventOutput("button2 pressed", e);
		}
		if ((e.getModifiers() & InputEvent.BUTTON3_MASK) != 0) {
			eventOutput("button3 pressed", e);
		}
		if ((e.getModifiers() & InputEvent.ALT_MASK) != 0) {
			eventOutput("alt pressed", e);
		}
		if ((e.getModifiers() & InputEvent.META_MASK) != 0) {
			eventOutput("meta pressed", e);
		}
	}

	public void mouseReleased(MouseEvent e) {
		// eventOutput("Mouse released (# of clicks: " + e.getClickCount() +
		// ")", e);
	}

	public void mouseEntered(MouseEvent e) {
		// eventOutput("Mouse entered", e);
	}

	public void mouseExited(MouseEvent e) {
		// eventOutput("Mouse exited", e);
	}

	public void mouseClicked(MouseEvent e) {
		// eventOutput("Mouse clicked (# of clicks: " + e.getClickCount() + ")",
		// e);
	}

	// ==========END EVENT HANDLERS==========

	public void UnsetAllCheckboxes() {
		cbManualVolumeTone.setState(false);
		cbManualVolumeNoise.setState(false);
		cbManualBasefreq.setState(false);
		cbManualBeatfreq.setState(false);
	}

	// give this a linear 0-1 value, and it returns a non-linear 0-1:
	public float LinearToExponential(float linear) {
		if (linear > 1) {
			linear = 1;
		} else if (linear < 0) {
			linear = 0;
		}

		linear *= linear;
		return linear;
	}

	public void SetManuals() {
		if (BB.BB_VoiceCount < 2) {
			return;
		}

		if (0 != BB.BB_Voice[0].ManualFreqBaseControl) {
			BB.BB_Voice[0].cur_basefreq = GNAURAL_FREQBASE_MAX
					* LinearToExponential(sliderManualBasefreq.getValue()
							* GNAURAL_SLIDER_INV);
		}

		if (0 != BB.BB_Voice[0].ManualFreqBeatControl) {
			BB.BB_Voice[0].cur_beatfreq = GNAURAL_FREQBEAT_MAX
					* LinearToExponential(sliderManualBeatfreq.getValue()
							* GNAURAL_SLIDER_INV);
		}

		if (0 != BB.BB_Voice[0].ManualVolumeControl) {
			BB.BB_Voice[0].CurVolL = BB.BB_Voice[0].CurVolR = LinearToExponential(sliderVolumeTone
					.getValue()
					* GNAURAL_SLIDER_INV);
		}

		if (0 != BB.BB_Voice[1].ManualVolumeControl) {
			BB.BB_Voice[1].CurVolL = BB.BB_Voice[1].CurVolR = LinearToExponential(sliderVolumeNoise
					.getValue()
					* GNAURAL_SLIDER_INV);
		}
		SetManualSliderTexts();
	}

	// 20070313: This does not disengage sound system input
	// anymore; that was too drastic. Now it just pauses and resets.
	public void main_Stop() {
		MyAudioThread.pause = true;
		if (null != MyWAVWriteThread) {
			MyWAVWriteThread.quit = true;
		}
		// MyAudioThread.quit = true;
		button_play.setLabel(sPlay);
		// while (MyAudioThread.ThreadIsAlive == true)
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		BB.BB_Reset();
	}

	// position can be 0 to 1:
	public void GnauralProgressBar(float position) {
		Graphics g = pProgressPanel.getGraphics();
		if (g != null) {
			int w = pProgressPanel.getSize().width;
			int h = pProgressPanel.getSize().height;
			if (h > 16) {
				h = 16;
			}
			g.setColor(Color.red);
			int start = (int) (position * w);
			g.fillRect(0, 0, start, h);
			g.setColor(Color.gray);
			g.fillRect(start, 0, w, h);
			g.setColor(Color.black);
			g.drawString("(click in this area to move around schedule)", 0,
					h - 3);
		}
	}

	// //////////////////////////////////
	// a20080307
	void ProgressBarButtonPress(int x_loc) {
		// System.out.println ("In Progress Panel, x=" + x_loc);
		/*
		 * while (TRUE == BB.BB_InCriticalLoopFlag) { SG_DBGOUT
		 * ("In critical loop, waiting"); main_Sleep (100); }
		 */
		float x = x_loc / (float) pProgressPanel.getSize().width;
		double samplecount_oneloop = BB.BB_TotalDuration
				* BinauralBeatSoundEngine.BB_AUDIOSAMPLERATE;

		if (1 < BB.BB_Loops) {
			// System.out.println ("factoring progressbar loops: " +
			// BB.BB_Loops);
			double samplecount_total = samplecount_oneloop * BB.BB_Loops;

			// first get the total number of samples user clicked at:
			BB.BB_CurrentSampleCountLooped = (long) (x * samplecount_total);
			// now translate that to BB.BB_LoopCount:
			BB.BB_LoopCount = BB.BB_Loops
					- (int) (BB.BB_CurrentSampleCountLooped / (long) samplecount_oneloop);
			// now figured out how far in a single schedule pass we were:
			BB.BB_CurrentSampleCount = (BB.BB_CurrentSampleCountLooped % (long) samplecount_oneloop);
			// now subtract that many samples from
			// BB.BB_CurrentSampleCountLooped:
			BB.BB_CurrentSampleCountLooped -= BB.BB_CurrentSampleCount;
			// System.out.println ("New BB.BB_LoopCount: " + BB.BB_LoopCount);
			main_UpdateGUI_ProjectedRuntime();
		} else {
			// System.out.println ("factoring simple progressbar");
			// easy way:
			BB.BB_CurrentSampleCount = (long) (samplecount_oneloop * x);
		}
	}

	// ///////////////////////////////////////////////////
	// negative amount rewinds, positive fast-forwards -1.0 to 1.0
	public void ForwardRewind(float amount) {
		int i = (int) (BB.BB_CurrentSampleCount + (BB.BB_TotalDuration * amount * BinauralBeatSoundEngine.BB_AUDIOSAMPLERATE));
		if (i < 0) {
			i = 0;
		} else {
			if (i > (BB.BB_TotalDuration * BinauralBeatSoundEngine.BB_AUDIOSAMPLERATE)) {
				i = (int) (BB.BB_TotalDuration * BinauralBeatSoundEngine.BB_AUDIOSAMPLERATE);
			}
		}
		BB.BB_CurrentSampleCount = i;
	}


	// ///////////////////////////////////////////////////
	// 20070105 updated to measure looping
	public void main_UpdateGUI_ProjectedRuntime() {
		if (BB.BB_Loops != 0) {
			int total = (int) (BB.BB_TotalDuration * BB.BB_Loops);
			lTTime.setText("Projected Runtime: " + total / 60 + " min. "
					+ total % 60 + "sec.");
		} else {
			lTTime.setText("Projected Runtime: Forever");
		}
	}

	public void CheckBB_InfoFlag() {
		// START check bb->InfoFlag
		// now update things that BinauralBeat says are changed:
		if (BB.BB_InfoFlag != 0) {
			// .........................
			// if schedule is done:
			if (((BB.BB_InfoFlag) & BinauralBeatSoundEngine.BB_COMPLETED) != 0) {
				// gtk_label_set_text (LabelProgramStatus,
				// "Schedule Completed");
				// MyAudioThread.pause = false; //so bbl_OnButton_Play() thinks
				// I was running
				// main_OnButton_Play (); // I'm simulating user button push to
				// pause
				main_Stop();
				// main_UpdateGUI_Status ("Schedule Completed");
				// bb->loopcount = bb->loops;
				// gtk_progress_bar_set_fraction (ProgressBar_Overall, 1.0);
			}
			// .........................
			// if starting a new loop:
			if (((BB.BB_InfoFlag) & BinauralBeatSoundEngine.BB_COMPLETED) != 0) {
				// reset the "new loop" bit of InfoFlag:
				BB.BB_InfoFlag &= ~BinauralBeatSoundEngine.BB_NEWLOOP;
				// bbl_UpdateGUI_LoopInfo ();
			}
		} // END check bb->InfoFlag
	}

	// ///////////////////////////////////////
	int guicounter = 0;

	public void UpdateGUI_Primary() {
		// -----------------------------------------------------------------------
		// now do stuff that gets updated every second:
		// lLFreq.setText("" + brainjav.leftFrequency);
		// lRFreq.setText("" + brainjav.rightFrequency);
		// lBFreq.setText("Beat Frequency: " + Math.abs(brainjav.leftFrequency -
		// brainjav.rightFrequency));
		// lCurTime.setText("Period time: " + (brainjav.currenttime / 1000));
		// if (brainjav.start) totaltime = ((new Date().getTime() - starttime) /
		// 1000);
		// lTTime.setText("Total elapsed time: " + (totaltime / 60) + " min. " +
		// (totaltime % 60) + " sec." );
		// ShowFrequencies();
		long total = (BB.BB_CurrentSampleCountLooped + BB.BB_CurrentSampleCount)
				/ (int) BinauralBeatSoundEngine.BB_AUDIOSAMPLERATE;

		switch (guicounter++) {

		case 0:
			if (BB.BB_Loops != 0) {
				lCurTime.setText("Current Progress: " + (total / 60) + "min. "
						+ (total % 60) + "sec. " + "Loop: "
						+ (BB.BB_Loops - BB.BB_LoopCount + 1) + "/"
						+ BB.BB_Loops);
			} else {
				lCurTime.setText("Current Progress: " + (total / 60) + "min. "
						+ (total % 60) + "sec. " + "Loop: "
						+ ((-BB.BB_LoopCount) + 1) + "/inf.");
			}
			break;

		case 1:
			if (BB.BB_Loops > 0) {
				GnauralProgressBar(total
						/ (float) (BB.BB_TotalDuration * BB.BB_Loops));
			} else {
				GnauralProgressBar((BB.BB_CurrentSampleCount / (int) BinauralBeatSoundEngine.BB_AUDIOSAMPLERATE)
						/ (float) BB.BB_TotalDuration);
			}
			break;

		case 2:
			if (null != MyWAVWriteThread) {
				if (MyWAVWriteThread.ThreadIsAlive == false) {
					lInfo.setText(new String("Done writing WAV file  "
							+ MyWAVWriteThread.WT_filename));
					MyWAVWriteThread = null;
				} else {
					lInfo.setText(new String("Writing WAV file to "
							+ MyWAVWriteThread.WT_filename
							+ "  - press Stop to abort"));
				}
			}
			break;

		case 3:
			// this is sort of obscenely labor intensive, but I want to
			// draw the mark every move:
			// first see if we even need to update anything:
			//MyGnauralGraphView.Graph_Draw();
			break;

		case 4:
			// Now do stuff that doesn't require constant updates:
			// NOTE: Not yet sure know how to call this sporadically; thread
			// issues mean some numbers aren't
			// filled-in yet if I call it just under file-opening, etc.
			if (bNewFile == true) {
				bNewFile = false;
				main_UpdateGUI_ProjectedRuntime();
				if (MyScheduleXML.success == true) {
					lFilename.setText("Current File: " + schedule_filename);
				} else {
					lFilename
							.setText("No valid user file found. Using Internal Default File.");
				}
				sliderVolumeOverall.setValue((int) (sliderVolumeOverall
						.getMaximum() * BB.BB_OverallVolume));
				sliderBalanceOverall
						.setValue((int) (GNAURAL_SLIDER_MAX * .5f * (1.f + BB.BB_OverallBalance)));
				SetVolumeSliderTexts();
				UnsetAllCheckboxes();
				MyGnauralGraphView.Graph_Draw();
				lInfo.setText("Welcome to Gnaural Java!");
			}
			break;

		case 5:
			CheckBB_InfoFlag();
			break;

		case 6:
			SetManualSliderTexts();
			break;

		default:
			guicounter = 0;
			break;
		}
	}

	// /////////////////////////////////////
	public void WriteScheduleToFile(String file_name) {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(file_name));
			// ///////////
			out.write("<?xml version=\"1.0\"?>\n");
			out.write("<!-- See http://gnaural.sourceforge.net -->\n");
			out.write("<schedule>\n");
			out
					.write("<gnauralfile_version>1.20101006</gnauralfile_version>\n");
			out.write("<gnaural_version>1.0.20110215</gnaural_version>\n");
			Date d = new Date();
			String strDateFormat = "yyyyMMdd kk:mm:ss";
			SimpleDateFormat format = new SimpleDateFormat(strDateFormat);
			out.write("<date>" + format.format(d) + "</date>\n");
			out.write("<title>Gnaural Java File</title>\n");
			out
					.write("<schedule_description>GnauralJava</schedule_description>\n");
			out.write("<author>Gnaural Java</author>\n");
			out.write("<totaltime>" + (float) BB.BB_TotalDuration
					+ "</totaltime>\n");
			out.write("<voicecount>" + BB.BB_VoiceCount + "</voicecount>\n");
			int v;
			int DP_Count = 0;
			for (v = 0; v < BB.BB_VoiceCount; v++) {
				DP_Count += BB.BB_Voice[v].EntryCount;
			}
			out.write("<totalentrycount>" + DP_Count + "</totalentrycount>\n");
			out.write("<loops>" + BB.BB_Loops + "</loops>\n");
			out.write("<overallvolume_left>" + (float) BB.BB_VolumeOverall_left
					+ "</overallvolume_left>\n");
			out.write("<overallvolume_right>"
					+ (float) BB.BB_VolumeOverall_right
					+ "</overallvolume_right>\n");
			out.write("<stereoswap>" + BB.BB_StereoSwap + "</stereoswap>\n");
			out.write("<graphview>1</graphview>\n");

			for (v = 0; v < BB.BB_VoiceCount; v++) {
				out.write("<voice>\n");
				out.write("<description>Java Gnaural</description>\n");
				out.write("<id>" + v + "</id>\n");
				out.write("<type>" + BB.BB_Voice[v].type + "</type>\n");

				// next two don't exist without graph editor so fudging here:
				out.write("<voice_state>0</voice_state>\n");
				out.write("<voice_hide>0</voice_hide>\n");

				out.write("<voice_mute>" + BB.BB_Voice[v].mute
						+ "</voice_mute>\n");
				out.write("<voice_mono>" + BB.BB_Voice[v].mono
						+ "</voice_mono>\n");
				out.write("<entrycount>" + BB.BB_Voice[v].EntryCount
						+ "</entrycount>\n");
				out.write("<entries>\n");
				// write the BB Tone entries:
				int e;
				for (e = 0; e < BB.BB_Voice[v].EntryCount; e++) {
					out.write("<entry parent=\"" + v + "\" duration=\""
							+ (float) BB.BB_Voice[v].Entry[e].duration + "\" ");
					out.write("volume_left=\""
							+ (float) BB.BB_Voice[v].Entry[e].volL_start
							+ "\" ");
					out.write("volume_right=\""
							+ (float) BB.BB_Voice[v].Entry[e].volR_start
							+ "\" ");
					out
							.write("beatfreq=\""
									+ (float) (2.f * BB.BB_Voice[v].Entry[e].beatfreq_start_HALF)
									+ "\" ");
					out.write("basefreq=\""
							+ (float) BB.BB_Voice[v].Entry[e].basefreq_start
							+ "\" state=\"0\"/>\n");
				}
				// finish that voice:
				out.write("</entries>\n");
				out.write("</voice>\n");
			} // end voices
			// finish the schedule:
			out.write("</schedule>\n");

			// ///////////
			out.close();
		} catch (IOException e) {
		}
	}

	// ////////////////////////////////////
	// mode can be FileDialog.LOAD or FileDialog.SAVE
	public String loadFile(Frame f, String title, String defDir,
			String fileType, int mode) {
		FileDialog fd = new FileDialog(f, title, mode);
		fd.setFile(fileType);
		fd.setDirectory(defDir);
		fd.setLocation(50, 50);
		System.out.println("" + fd.getDirectory());
		// 20100212 Deprecated ".show" replaced with ".setVisible(true)":
		// fd.show ();
		fd.setVisible(true);
		if (fd.getFile() == null) {
			return null;
		}
		return (fd.getDirectory() + fd.getFile());
		// return fd.getDirectory() + File.separator + fd.getFile();
		// NOTE: the following will return SOLELY the filename:
		// return fd.getFile();
	}
}
