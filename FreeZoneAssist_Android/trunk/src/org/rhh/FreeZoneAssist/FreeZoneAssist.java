package org.rhh.FreeZoneAssist;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.TextView;

//2d0:
//	o configuration menu
//		- sounds (error, fatal, timeout)
//		- section time
//	o button feedback
//      - haptic

public class FreeZoneAssist extends Activity implements OnClickListener,
		OnTouchListener {
	static final String TAG = "FZA";
	static final int ver = 17;
	static final int SET_SECTION = 4711;
	static final int SET_DRIVER = 4712;

	// --------------------------------------------------- Fields...
	TextView tvRider, tvPoints, tvSection, tvCountdown;
	boolean btnTwoEnabled, btnFiveEnabled, btnTenEnabled, btnBackEnabled,
			btnStartEnabled, btnStopEnabled, btnGoalEnabled, btnSaveEnabled;

	enum Modes {
		Rating, Saved, Unsaved
	};
	Modes Mode;
	
	boolean KeyInProcess = false;
	int NextDriver;

	Stack<RatingAction> History = new Stack<RatingAction>(); // size?

	private MediaPlayer mpErrorBeep;
	private MediaPlayer mpFatalStopBeep;
	private MediaPlayer mpTimeout;
	private MediaPlayer mpKeyClick;
	Vibrator vibrator;

	// free running counter to emulate watchdog-timer on Arduino
	long frtStartTime = 0L;
	int frtInterval = 100; // in msecs
	private Handler frtHandler = new Handler(); // used as a free running timer
												// (frt)
	// --------------------------- Countdown driven by free running timer
	int CountDownDuration = 60 * 1000; // 60 sec
	int CountDownTickInterval = 1000; // 1000 msec => 1sec
	int CountDownMsecs = CountDownDuration;
	boolean CountDownEnabled = true;

	// --------------------------------------------------- Livecycle Methods...
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		View v;
		// this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Setup listener for all the buttons
		v = findViewById(R.id.two_button);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.five_button);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.ten_button);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.back_button);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.start_button);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.stop_button);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.goal_button);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.save_button);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);

		v = findViewById(R.id.tvCountdown);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.tvPoints);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.tvRider);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);
		v = findViewById(R.id.tvSection);
		v.setOnClickListener(this);
		v.setOnTouchListener(this);

		tvRider = (TextView) findViewById(R.id.tvRider);
		tvCountdown = (TextView) findViewById(R.id.tvCountdown);
		tvSection = (TextView) findViewById(R.id.tvSection);
		tvPoints = (TextView) findViewById(R.id.tvPoints);

		// http://www.soundjay.com/beep-sounds-1.html lots of free beeps here
		mpErrorBeep = MediaPlayer.create(this, R.raw.error_beep);
		mpFatalStopBeep = MediaPlayer.create(this, R.raw.fatalstop_beep);
		mpTimeout = MediaPlayer.create(this, R.raw.timeout_beep);
		mpKeyClick = MediaPlayer.create(this, R.raw.click01);	// first 0.1 sec. of button16
		vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

		startFreeRunningTimer();

		try // warm start...
		{
			Log.d(TAG, "onCreate: warm start!");
			this.Mode = (Modes) savedInstanceState.getSerializable("State");
			this.CountDownMsecs = savedInstanceState.getInt("CountDownMsecs");
			this.NextDriver = savedInstanceState.getInt("NextDriver");
			this.CountDownEnabled = savedInstanceState
					.getBoolean("CountDownEnabled");
			RatingAction.ActualDriver = savedInstanceState.getInt("Driver");
			RatingAction.ActualPoints = savedInstanceState.getInt("Points");
			RatingAction.ActualSection = savedInstanceState.getInt("Section");
			RatingAction.IDCounter = savedInstanceState.getInt("ID");

		} catch (NullPointerException e) // cold start...
		{
			Log.d(TAG, "onCreate: cold start!");
			Mode = Modes.Saved;
			CountDownMsecs = 0;
			CountDownEnabled = false;
			RatingAction ra = InitFromLog();
			RatingAction.ActualDriver = NextDriver = (ra.Driver + 1);
			RatingAction.ActualPoints = 0;
			RatingAction.ActualSection = ra.Section;
			RatingAction.IDCounter = ra.ID;
		}

		EnterMode(Mode);

		this.setTitle(String.format("FreeZoneAssist (v%03d/17feb11)", ver));
		
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mpErrorBeep != null) {
			mpErrorBeep.release();
			mpErrorBeep = null;
		}
		if (mpFatalStopBeep != null) {
			mpFatalStopBeep.release();
			mpFatalStopBeep = null;
		}
		if (mpTimeout != null) {
			mpTimeout.release();
			mpTimeout = null;
		}
		if (mpTimeout != null) {
			mpTimeout.release();
			mpTimeout = null;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putSerializable("State", this.Mode);
		savedInstanceState.putInt("CountDownMsecs", this.CountDownMsecs);
		savedInstanceState.putInt("NextDriver", this.NextDriver);
		savedInstanceState.putBoolean("CountDownEnabled", CountDownEnabled);
		savedInstanceState.putInt("Driver", RatingAction.ActualDriver);
		savedInstanceState.putInt("Points", RatingAction.ActualPoints);
		savedInstanceState.putInt("Section", RatingAction.ActualSection);
		savedInstanceState.putInt("ID", RatingAction.IDCounter);
		// etc.
		super.onSaveInstanceState(savedInstanceState);
	}

	// @Override
	// public void onRestoreInstanceState(Bundle savedInstanceState) {
	// super.onRestoreInstanceState(savedInstanceState);
	// State = (States) savedInstanceState.getSerializable("State");
	// CountDownMsecs = savedInstanceState.getInt("CountDownMsecs");
	// }

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (!KeyInProcess) {
			switch (v.getId()) {
			// ----------------------------- touch buttons
			case R.id.two_button:
				if (btnTwoEnabled)
					KeyClick();
				else
					ErrorBeep();
				break;
			case R.id.five_button:
				if (btnFiveEnabled)
					KeyClick();
				else
					ErrorBeep();
				break;
			case R.id.ten_button:
				if (btnTenEnabled)
					KeyClick();
				else
					ErrorBeep();
				break;
			case R.id.back_button:
				if (btnBackEnabled)
					KeyClick();
				else
					ErrorBeep();
				break;
			case R.id.start_button:
				if (btnStartEnabled)
					KeyClick();
				else
					ErrorBeep();
				break;
			case R.id.stop_button:
				if (btnStopEnabled)
					KeyClick();
				else
					ErrorBeep();
				break;
			case R.id.goal_button:
				if (btnGoalEnabled)
					KeyClick();
				else
					ErrorBeep();
				break;
			case R.id.save_button:
				if (btnSaveEnabled)
					KeyClick();
				else
					ErrorBeep();
				break;
			// --------------------------------- // touch display
			case R.id.tvCountdown:	
			case R.id.tvPoints:
			case R.id.tvRider:
			case R.id.tvSection:
				KeyClick();
				break;
			}
		}
		KeyInProcess = true;
		return false;
	}

	public void onClick(View v) {

		// vibrator.vibrate(100); // milliseconds
		KeyInProcess = false;

		switch (v.getId()) {
		case R.id.two_button:
			btnAdd2_Click();
			break;
		case R.id.five_button:
			btnAdd5_Click();
			break;
		case R.id.ten_button:
			btnAdd10_Click();
			break;
		case R.id.back_button:
			btnBack_Click();
			break;
		case R.id.start_button:
			btnStart_Click();
			break;
		case R.id.stop_button:
			btnStop_Action();
			break;
		case R.id.goal_button:
			btnGoal_Click();
			break;
		case R.id.save_button:
			btnSave_Click();
			break;
		case R.id.tvCountdown:
		case R.id.tvPoints:
		case R.id.tvRider:
		case R.id.tvSection:
			startActivityForResult(new Intent(this, SetDriver.class),
					SET_DRIVER);
			break;
		}
	}

	// --------------------------------------------------------- Context Menu
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.stop_submenu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		boolean Handled = false;

		switch (item.getItemId()) {
		case R.id.stop:
			KeyClick();
			btnStop_Action();
			Handled = true;
			break;
		case R.id.fatal_stop:
			FatalStopBeep();
			btnFatalStop_Action();
			Handled = true;
			break;
		default:
			Handled = super.onContextItemSelected(item);
			break;
		}
		KeyInProcess = false;
		return Handled;
	}

	// --------------------------------------------------------- Options Menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.mm_settings:
			startActivity(new Intent(this, Prefs.class));
			return true;
		case R.id.mm_about:
			Intent i = new Intent(this, About.class);
			startActivity(i);
			break;
		case R.id.mm_set_driver:
			startActivityForResult(new Intent(this, SetDriver.class),
					SET_DRIVER);
			break;
		case R.id.mm_set_section:
			startActivityForResult(new Intent(this, SetSection.class),
					SET_SECTION);
			break;
		case R.id.mm_exit:
			break;

		// More items go here (if any) ...
		}
		return false;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "onActivityResult...");
		// See which child activity is calling us back.
		switch (requestCode) {
		case SET_SECTION:
			if (resultCode == RESULT_CANCELED)
				Log.d(TAG, "SetSection: RESULT_CANCELED");
			else if (resultCode == RESULT_OK)
				Log.d(TAG, "SetSection: RESULT_OK");
			else
				Log.d(TAG, "SetSection: UNKNOWN_RESULT");
			UpdateDisplay();
			break;
		case SET_DRIVER:
			if (resultCode == RESULT_CANCELED)
				Log.d(TAG, "SetDriver: RESULT_CANCELED");
			else if (resultCode == RESULT_OK)
				Log.d(TAG, "SetDriver: RESULT_OK");
			else
				Log.d(TAG, "SetDriver: UNKNOWN_RESULT");
			UpdateDisplay();
			break;
		default:
			break;
		}
	}

	// ----------------------------------------- program logic...
	private void btnStart_Click() {			
		if (Mode == Modes.Saved) {
			History.clear();
			RatingAction.ActualPoints = 0;
			// If the driver-number is not set up to this moment,
			// the driver-number is negated and incremented automatically
			if (Math.abs(RatingAction.ActualDriver) == Math.abs(NextDriver)) // input
																				// forgotten?
			{
				RatingAction.ActualDriver = -Math.abs(++NextDriver);
			} else {
				RatingAction.ActualDriver = NextDriver;
			}
			Log2File(new RatingAction(RatingAction.Types.start));
			EnterMode(Modes.Rating);
		}
	}

	private void btnAdd2_Click() {
		RatingAction ra;

		if (Mode == Modes.Rating || Mode == Modes.Unsaved) {
			ra = new RatingAction(RatingAction.Types.add2);
			History.push(ra);
			Log2File(ra);
			UpdateDisplay();
		}
	}

	private void btnAdd5_Click() {
		RatingAction ra;

		if (Mode == Modes.Rating || Mode == Modes.Unsaved) {
			ra = new RatingAction(RatingAction.Types.add5);
			History.push(ra);
			Log2File(ra);
			UpdateDisplay();
		}
	}

	private void btnAdd10_Click() {
		RatingAction ra;

		if (Mode == Modes.Rating || Mode == Modes.Unsaved) {
			ra = new RatingAction(RatingAction.Types.add10);
			History.push(ra);
			Log2File(ra);
			UpdateDisplay();
		}
	}

	private void btnBack_Click() {
		if (Mode == Modes.Rating || Mode == Modes.Unsaved) {
			if (!History.isEmpty()) {
				switch (History.pop().Type) {
				case add1:
					Log2File(new RatingAction(RatingAction.Types.sub1));
					break;
				case add2:
					Log2File(new RatingAction(RatingAction.Types.sub2));
					break;
				case add5:
					Log2File(new RatingAction(RatingAction.Types.sub5));
					break;
				case add10:
					Log2File(new RatingAction(RatingAction.Types.sub10));
					break;
				}
				UpdateDisplay();
			}
		}
	}

	private void btnSave_Click() {
		if (Mode == Modes.Unsaved) {
			EnterMode(Modes.Saved);
		}
	}

	private void btnGoal_Click() {
		RatingAction ra;

		if (Mode == Modes.Rating || Mode == Modes.Unsaved) {
			ra = new RatingAction(RatingAction.Types.add1);
			History.push(ra);
			Log2File(ra);
			UpdateDisplay();
			EnterMode(Modes.Unsaved);
		}
	}

	private void btnStop_Action() {
		if (Mode == Modes.Rating) {
			Log2File(new RatingAction(RatingAction.Types.stop));
			EnterMode(Modes.Unsaved);
		}
	}

	private void btnFatalStop_Action() {
		if (Mode == Modes.Rating) {
			RatingAction.ActualPoints = 0;
			Log2File(new RatingAction(RatingAction.Types.fatalstop));
			EnterMode(Modes.Unsaved);
		}
	}

	void UpdateDisplay() {

		int sec = CountDownMsecs / 1000;
		int min = sec / 60;
		sec = sec % 60;

		tvRider.setText(String.format("Rid: %03d", RatingAction.ActualDriver));
		tvSection.setText(String.format("Sect: %02d",
				RatingAction.ActualSection));
		tvPoints.setText(String.format("Pt: %04d", RatingAction.ActualPoints));

		switch (Mode) {
		case Rating:
			tvCountdown.setText(String.format("t: %02d:%02d", min, sec));
			break;
		case Unsaved:
			tvCountdown.setText("UNSAVED!");
			break;
		case Saved:
			tvCountdown.setText(" SAVED! ");
			break;
		default:
			tvCountdown.setText("--????--");
			break;
		}
	}

	void EnterMode(Modes m) {
		switch (m) {
		case Saved:
			Mode = Modes.Saved;

			btnTwoEnabled = false;
			btnFiveEnabled = false;
			btnTenEnabled = false;
			btnBackEnabled = false;
			btnStartEnabled = true;
			btnStopEnabled = false;
			btnGoalEnabled = false;
			btnSaveEnabled = true;
			break;

		case Unsaved:
			Mode = Modes.Unsaved;
			CountDownEnabled = false;
			unregisterForContextMenu(findViewById(R.id.stop_button));

			btnTwoEnabled = true;
			btnFiveEnabled = true;
			btnTenEnabled = true;
			btnBackEnabled = true;
			btnStartEnabled = false;
			btnStopEnabled = true;
			btnGoalEnabled = true;
			btnSaveEnabled = true;
			break;

		case Rating:
			Mode = Modes.Rating;
			CountDownMsecs = CountDownDuration;
			CountDownEnabled = true;
			registerForContextMenu(findViewById(R.id.stop_button));

			btnTwoEnabled = true;
			btnFiveEnabled = true;
			btnTenEnabled = true;
			btnBackEnabled = true;
			btnStartEnabled = false;
			btnStopEnabled = true;
			btnGoalEnabled = true;
			btnSaveEnabled = false;
			break;
		}
		UpdateDisplay();

	}

	String GetLogFileName() {
		// create filename on the fly
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
		String FileName = "log_" + sdf.format(new Date()) + ".csv";
		// Log.d(TAG, "FileName: " + FileName);
		return FileName;
	}

	void Log2File(RatingAction ra) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
		String Line = String.format("%d;%s;%s;%d;%d\n", ra.ID,
				ra.Type.toString(), sdf.format(ra.TimeStamp), ra.Driver,
				ra.Points);
		Log.d(TAG, "LINE: " + Line);

		try {
			File sdCard = Environment.getExternalStorageDirectory();
			File dir = new File(sdCard.getAbsolutePath() + "/FreeZoneAssist");
			dir.mkdirs();
			File LogFile = new File(dir, GetLogFileName());

			FileWriter LogWriter = new FileWriter(LogFile, true); // append...
			// BufferedWriter out = new BufferedWriter(LogWriter); // not used,
			// because of robustness...

			LogWriter.append(Line);
			LogWriter.close();

		} catch (IOException e) {
			Log.e(TAG, "Could not write file " + e.getMessage());
		}
	}

	RatingAction InitFromLog() {
		// String Line="";
		// StreamReader sr;

		RatingAction ra = new RatingAction(RatingAction.Types.init);
		// string LogFileName = GetLogFileName();
		//
		// // open corresponding file
		// if (File.Exists(LogFileName))
		// {
		// sr = new StreamReader(LogFileName);
		//
		// // find last line in logfile
		// while (!sr.EndOfStream)
		// {
		// Line = sr.ReadLine();
		// }
		//
		// // set values from last line in file
		// string[] Col = Line.Split(';');
		// ra.ID = int.Parse(Col[0]);
		// ra.Driver = int.Parse(Col[3]);
		//
		// sr.Close();
		// }
		return ra;
	}

	void KeyClick() {
		try {
			mpKeyClick.start();
			HapticFeedback();
		} catch (Exception e) {
			Log.e(TAG, "beep error: " + e.getMessage(), e);
		}
	}

	void HapticFeedback() {
		HapticFeedback(50);		
	}
	
	void HapticFeedback(int duration) {
		vibrator.vibrate(duration);		
	}
	
	void ErrorBeep() {
		try {
			mpErrorBeep.start();
			HapticFeedback();
		} catch (Exception e) {
			Log.e(TAG, "beep error: " + e.getMessage(), e);
		}
	}

	void FatalStopBeep() {
		try {
			mpFatalStopBeep.start();
		} catch (Exception e) {
			Log.e(TAG, "beep error: " + e.getMessage(), e);
		}
	}

	void CountDownBeep() {
		try {
			// mpCountDownOverBeep.setLooping(false);
			mpTimeout.start();
			mpTimeout
					.setOnCompletionListener(new OnCompletionListener() {
						int BeepCount = 0;

						public void onCompletion(MediaPlayer arg0) {
							if (++BeepCount < 3) {
								try {
									Thread.sleep(200);
								} catch (InterruptedException e) {
								}
								arg0.start();
							}
						}
					});
		} catch (Exception e) {
			Log.e(TAG, "beep error: " + e.getMessage(), e);
		}
	}

	// --------------------------------------------------- timer stuff
	void startFreeRunningTimer() {
		if (frtStartTime == 0L) {
			frtStartTime = System.currentTimeMillis();
			frtHandler.removeCallbacks(frtUpdateTimeTask);
			frtHandler.postDelayed(frtUpdateTimeTask, 100);
		}
	}

	void stopFreeRunningTimer() {
		frtHandler.removeCallbacks(frtUpdateTimeTask);
	}

	private Runnable frtUpdateTimeTask = new Runnable() {
		public void run() {
			frt_tick();
			frtHandler.postAtTime(this, SystemClock.uptimeMillis()
					+ frtInterval);
		}
	};

	// generic FreeRunningCounter Tick-Handler!
	protected void frt_tick() {
		if (CountDownEnabled) {
			// ------------------------------------------ countdown tick action
			if (CountDownMsecs % CountDownTickInterval < frtInterval) {
				UpdateDisplay();
			}
			CountDownMsecs -= frtInterval;
			// ------------------------------------------ end of countdown
			if (CountDownMsecs <= 0) {
				CountDownEnabled = false;
				Log2File(new RatingAction(RatingAction.Types.timeout));
				CountDownBeep();
				EnterMode(Modes.Unsaved);
			}
		}
	}

}