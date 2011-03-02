package org.rhh.FreeZoneAssist;

import org.rhh.FreeZoneAssist.NumberPicker.OnChangedListener;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class SetDriver extends Activity implements  OnClickListener, OnChangedListener
{
	NumberPicker dd001, dd010, dd100;	// DriverDigits
	
	int DriverNumber=123, DriverDigit001, DriverDigit010, DriverDigit100;
	boolean KeyInProcess = false;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	View v;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_driver);
        
        DriverNumber = RatingAction.ActualDriver;	
        DriverDigit100 = DriverNumber % 1000 / 100;
        DriverDigit010 = DriverNumber % 100 / 10;
        DriverDigit001 = DriverNumber % 10;
        
        // register and initialize NumberPicker widgets
        dd001 = (NumberPicker) findViewById(R.id.driver_digit_001);
        dd001.setOnChangeListener(this);
        dd001.mCurrent = DriverDigit001; 
        dd001.updateView();

        dd010 = (NumberPicker) findViewById(R.id.driver_digit_010);
        dd010.setOnChangeListener(this);
        dd010.mCurrent = DriverDigit010; 
        dd010.updateView();

        dd100 = (NumberPicker) findViewById(R.id.driver_digit_100);
        dd100.setOnChangeListener(this);
        dd100.mCurrent = DriverDigit100; 
        dd100.updateView();
        
        // register buttons
        v = findViewById(R.id.driver_ok);
        v.setOnClickListener(this);
        v = findViewById(R.id.driver_abort);
        v.setOnClickListener(this);
    }
    
	@Override
	public void onChanged(NumberPicker picker, int oldVal, int newVal) 
	{
		switch(picker.getId())
		{
			case R.id.driver_digit_001:
				DriverDigit001 = newVal;
				break;
			case R.id.driver_digit_010:
				DriverDigit010 = newVal;			
				break;
			case R.id.driver_digit_100:
				DriverDigit100 = newVal;			
				break;
		}
		DriverNumber = DriverDigit100 * 100 + DriverDigit010 * 10 + DriverDigit001;
		FreeZoneAssist.KeyClick();
		FreeZoneAssist.HapticFeedback();
	}

	@Override
	public void onClick(View v) 
	{
		switch(v.getId())
		{
			case R.id.driver_ok:
				DriverDigit001 = dd001.mCurrent;
				DriverDigit010 = dd010.mCurrent;
				DriverDigit100 = dd100.mCurrent;
				DriverNumber = DriverDigit100 * 100 + DriverDigit010 * 10 + DriverDigit001;
				RatingAction.ActualDriver = DriverNumber;
				setResult(RESULT_OK);
				finish();
				break;
			case R.id.driver_abort:				
				setResult(RESULT_CANCELED);
				this.finish();
				break;
		}		
		FreeZoneAssist.KeyClick();
		FreeZoneAssist.HapticFeedback();
	}

}
