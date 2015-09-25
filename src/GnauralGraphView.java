import java.awt.Color;
import java.awt.Graphics;
import java.awt.Panel;

public class GnauralGraphView {
	final static int SG_GRAPHTYPE_BEATFREQ = 1;
	final static int SG_GRAPHTYPE_BASEFREQ = 3;
	final static int SG_GRAPHTYPE_VOLUME = 7;
	final static int SG_GRAPHTYPE_VOLUME_BALANCE = 15;
	final int GV_CIRCLED = 2;
	final int[] mColors16 = { 0xFF311ebf, 0xFFbf1e1e, 0xFF22bf1e, 0xFFbf1ea3,
			0xFF1e71bf, 0xFFbf5e1e, 0xFF97bf1e, 0xFF621ebf, 0xFFf33f00,
			0xFF001df3, 0xFF00f31d, 0xFFf300df, 0xFF960012, 0xFF322068,
			0xFF236820, 0xFF682064 };
	Panel pDrawingPanel;
	BinauralBeatSoundEngine BB;
	int mGraphType = SG_GRAPHTYPE_BEATFREQ;
	int mHeight;
	int mWidth;
	Color [] MyColors;


	GnauralGraphView(Panel tmpDrawingPanel, BinauralBeatSoundEngine tmpBB) {
		pDrawingPanel = tmpDrawingPanel;
		BB = tmpBB;
		mHeight = pDrawingPanel.getHeight();
		mWidth = pDrawingPanel.getWidth();
		MyColors = new Color[mColors16.length];
		for (int j = 0; j < mColors16.length; j++) {
			MyColors[j] = new Color(mColors16[j]);
		}

	}

	
	// /////////////////////////////
	// the big one of the Graph_ family:
	public void Graph_Draw() {
		Graphics g = pDrawingPanel.getGraphics();
		if (g != null) {
			int width = pDrawingPanel.getSize().width;
			int height = pDrawingPanel.getSize().height;
			int x; // Top-left corner of square

			// paint panel white:
			g.setColor(Color.white);
			g.fillRect(0, 0, width, height);
			
			GV_DrawGrid(g);
			GV_DrawData(g);
			
			// draw circle or line in current place in schedule:
			g.setColor(Color.red);
			x = (int) ((width / BB.BB_TotalDuration) * BB.BB_CurrentSampleCount / BinauralBeatSoundEngine.BB_AUDIOSAMPLERATE);
			g.drawLine(x, 0, x, height);
		} // end if g!=null
	} // end function GnauralDraw
	
	
//////////////////////////////////////

	// ------------------------
	// this must be called with any changes to Event data OR Graph dimensions
	public void Graph_CalibrateEvents() {
		int width = mWidth;
		int height = mHeight;
		int col; // Column number, from 0 to 7
		double x_factor;
		double totaldur, totaldur_max = 0;
		double y_factor;
		int voice;
		float y_max = 1;

		if (0 != BB.BB_TotalDuration) {
			x_factor = (width / BB.BB_TotalDuration);
		} else {
			x_factor = 0;
		}

		switch (mGraphType) {
		case SG_GRAPHTYPE_BASEFREQ:
			y_max = GV_GetTopBase();
			break;

		case SG_GRAPHTYPE_BEATFREQ:
			y_max = GV_GetTopBeat();
			break;

		case SG_GRAPHTYPE_VOLUME:
			y_max = 1f;
			break;

		case SG_GRAPHTYPE_VOLUME_BALANCE:
			y_max = 2f;
			break;

		default:
			break;
		}

		if (0 != y_max)
			y_factor = (height / y_max);
		else
			y_factor = 0;

		for (voice = 0; voice < BB.BB_VoiceCount; voice++) {
			totaldur = 0;
			for (col = 0; col < (BB.BB_Voice[voice].EntryCount); col++) {
				switch (mGraphType) {
				case SG_GRAPHTYPE_BASEFREQ:
					BB.BB_Voice[voice].Entry[col].Y = (int) ((height - Math
							.abs(BB.BB_Voice[voice].Entry[col].basefreq_start
									* y_factor)) + .5f);
					break;

				case SG_GRAPHTYPE_BEATFREQ:
					BB.BB_Voice[voice].Entry[col].Y = (int) ((height - Math
							.abs(BB.BB_Voice[voice].Entry[col].beatfreq_start_HALF
									* y_factor)) + .5f);
					break;

				case SG_GRAPHTYPE_VOLUME: {
					float higher = (float) ((BB.BB_Voice[voice].Entry[col].volL_start > BB.BB_Voice[voice].Entry[col].volR_start) ? BB.BB_Voice[voice].Entry[col].volL_start
							: BB.BB_Voice[voice].Entry[col].volR_start);

					BB.BB_Voice[voice].Entry[col].Y = (int) ((height - Math
							.abs(higher * y_factor)) + .5f);
				}
					break;

				case SG_GRAPHTYPE_VOLUME_BALANCE:// "balance"
					// philosophy here: Y is determined by the proportion
					// between the lower
					// to the higher of the two channels. Confusingly, if the DP
					// is above half-graph,
					// it means that the right channel is the lower than right;
					// and vice-versa.
				{
					if (BB.BB_Voice[voice].Entry[col].volL_start > BB.BB_Voice[voice].Entry[col].volR_start) { // see
						// if
						// left
						// is
						// bigger
						BB.BB_Voice[voice].Entry[col].Y = (int) (height - (y_factor * (2 - (BB.BB_Voice[voice].Entry[col].volR_start / BB.BB_Voice[voice].Entry[col].volL_start))));
						// SG_DBGOUT_FLT("Left Bigger:",
						// (BB.BB_Voice[voice].Entry[col].volR_start /
						// BB.BB_Voice[voice].Entry[col].volL_start));
					} else if (BB.BB_Voice[voice].Entry[col].volL_start < BB.BB_Voice[voice].Entry[col].volR_start) { // see
						// if
						// right
						// is
						// bigger:
						BB.BB_Voice[voice].Entry[col].Y = (int) (height - (y_factor
								* BB.BB_Voice[voice].Entry[col].volL_start / BB.BB_Voice[voice].Entry[col].volR_start));
						// SG_DBGOUT_FLT("Right Bigger:",
						// (BB.BB_Voice[voice].Entry[col].volL_start /
						// BB.BB_Voice[voice].Entry[col].volR_start));
					} else
					// logically, they must be equal -- but I don't know if
					// volume was zero:
					{
						BB.BB_Voice[voice].Entry[col].Y = (int) y_factor;
					}
				}
					break;
				}

				// all Graph Types need these two:
				BB.BB_Voice[voice].Entry[col].X = (int) ((totaldur * x_factor) + .5);
				totaldur += BB.BB_Voice[voice].Entry[col].duration;
			}
			if (totaldur > totaldur_max)
				totaldur_max = totaldur;// i guess this is not needed
		}// end for-voice
	}

//////////////////////////////////////
	// ------------------------
	public void GV_DrawData(Graphics g) {
		int col, x, y;
		int oldx = 0, oldy = 0, origy = 0;
		boolean dograph = true;
		int voice;

		for (voice = 0; voice < BB.BB_VoiceCount; voice++) {
			//mPaint.setColor(mColors16[(voice & 0xF)]);
			g.setColor(MyColors[voice]); //need to increment this
			dograph = true;
			// don't graph noise or WAV when in beat or base view:
			if (SG_GRAPHTYPE_BASEFREQ == mGraphType
					|| SG_GRAPHTYPE_BEATFREQ == mGraphType) {
				if (BinauralBeatSoundEngine.BB_VOICETYPE_PINKNOISE == BB.BB_Voice[voice].type
						|| BinauralBeatSoundEngine.BB_VOICETYPE_PCM == BB.BB_Voice[voice].type)
					dograph = false;
			}

			if (0 != BB.BB_Voice[voice].mute) {
				dograph = false;
			}

			// -------
			if (true == dograph) {
				for (col = 0; col < (BB.BB_Voice[voice].EntryCount); col++) {
					y = BB.BB_Voice[voice].Entry[col].Y;
					x = BB.BB_Voice[voice].Entry[col].X;
					if (col != 0) {
						g.drawLine(oldx, oldy, x, y);
						g.setColor(Color.BLACK);
						g.drawOval((x - GV_CIRCLED), (y - GV_CIRCLED), GV_CIRCLED<<1, GV_CIRCLED<<1);
						g.setColor(MyColors[voice]);
					} else {
						origy = y;
					}
					oldx = x;
					oldy = y;
				}
				g.drawLine(oldx, oldy, mWidth, origy);
			}
		}
	}
	
	
//////////////////////////////////////
	// ------------------------
	public void GV_DrawGrid(Graphics g) {
		final int SG_GRIDY_MINSTEP = 12;
		final int SG_GRIDY_MAXSTEP = 32;
		final int SG_GRIDY_UNIT = 1;
		final int SG_GRIDX_MINSTEP = 32;
		final int SG_GRIDX_MAXSTEP = 64;
		final int SG_GRIDX_UNIT = 60;
		float index;
		float textindex;
		float textstep;
		float step;
		int width = mWidth;
		index = mHeight;
		textindex = 0;
		float vscale = 1;

		switch (mGraphType) {
		case SG_GRAPHTYPE_BASEFREQ:
			vscale = GV_GetTopBase();
			break;

		case SG_GRAPHTYPE_BEATFREQ:
			vscale = 2 * GV_GetTopBeat();
			break;

		case SG_GRAPHTYPE_VOLUME:
			vscale = 1f;
			break;

		case SG_GRAPHTYPE_VOLUME_BALANCE:
			vscale = -1f;
			break;
		}

		// first deal with potential divide by zero issue:
		if (vscale == 0) {
			vscale = 1.0f;
		}
		step = index / vscale;
		// deal with negative vscale of type Mix:
		if (vscale < 0) {
			textindex = vscale;
			step /= -2;
		}
		textstep = SG_GRIDY_UNIT; // this is the basis of one "unit" of vertical
		// climb
		// Bruteforce way to get grid-steps that are within a user-friendly
		// range:
		// these next two uglies could be speeded up in a number of ways, no?
		if (step < SG_GRIDY_MINSTEP) {
			do {
				step *= 2;
				textstep *= 2;
			} while (step < SG_GRIDY_MINSTEP);
		} else if (step > SG_GRIDY_MAXSTEP) {
			do {
				step *= .5;
				textstep *= .5;
			} while (step > SG_GRIDY_MAXSTEP);
		}

		//mPaint.setColor(Color.GRAY);
		g.setColor(Color.GRAY);

		int ax;
		while ((ax = (int) ((index -= step) + .5)) > -1) {
			g.drawLine(0, ax, width, ax);
			textindex += textstep;
			//g.drawText("" + textindex, 0, ax, mPaint);
			g.drawString("" + textindex, 0, ax);
		}

		// Now draw "friendly-to-read" (and extremely computationally intensive)
		// vertical grid lines:
		index = 0;
		textindex = 0;
		textstep = SG_GRIDX_UNIT; // this is the basis of one "unit" of
		// horizontal movement
		if (0 != BB.BB_TotalDuration) {
			step = (float) ((SG_GRIDX_UNIT * width) / BB.BB_TotalDuration);
		} else {
			step = (float) ((SG_GRIDX_UNIT * width) / .01);
		}
		int minutes, seconds;
		// Bruteforce way to get grid-steps that are within a user-friendly
		// range:
		if (step < SG_GRIDX_MINSTEP) {
			do {
				step *= 2;
				textstep *= 2;
			} while (step < SG_GRIDX_MINSTEP);
		} else if (step > SG_GRIDX_MAXSTEP) {
			do {
				step *= .5;
				textstep *= .5;
			} while (step > SG_GRIDX_MAXSTEP);
		}
		while ((ax = (int) ((index += step) + .5)) < width) {
			g.drawLine(ax, 0, ax, mHeight);

			textindex += textstep;
			String graphtext;
			// first handle if there are no minutes at all:
			if ((minutes = ((int) (textindex + .5)) / SG_GRIDX_UNIT) < 1) {
				graphtext = "" + ((int) (textindex + .5)) + "s";
			} else if ((seconds = ((int) (textindex + .5)) % SG_GRIDX_UNIT) == 0) {
				graphtext = "" + minutes + "m";
			} else {
				graphtext = "" + minutes + "m" + seconds + "s";
			}
			g.drawString(graphtext, ax, mHeight);
		}
	}
	

	// ------------------------
	public float GV_GetTopBeat() {
		float top = 0, h = 0;
		int voice;
		for (voice = 0; voice < BB.BB_VoiceCount; voice++) {
			if (0 == BB.BB_Voice[voice].mute) {
				h = (float) BB.BB_GetTopBeatfreq(voice);
				if (h > top)
					top = h;
			}
		}
		return top;
	}

	// ------------------------
	public float GV_GetTopBase() {
		float top = 0;
		int e;
		int voice;
		for (voice = 0; voice < BB.BB_VoiceCount; voice++) {
			if (0 == BB.BB_Voice[voice].mute)
				for (e = 0; e < (BB.BB_Voice[voice].EntryCount); e++) {
					if (top < BB.BB_Voice[voice].Entry[e].basefreq_start) {
						top = (float) BB.BB_Voice[voice].Entry[e].basefreq_start;
					}
				}
		}
		return top;
	}
	
	
}
