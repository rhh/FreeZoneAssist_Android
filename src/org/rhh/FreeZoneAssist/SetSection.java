package org.rhh.FreeZoneAssist;

import org.rhh.FreeZoneAssist.NumberPicker.OnChangedListener;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class SetSection extends Activity implements  OnClickListener, OnChangedListener
{
	NumberPicker sd001, sd010;
	
	boolean KeyInProcess = false;
	
	int SectionNumber=123, SectionDigit001, SectionDigit010;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	View v;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_section);
        
        SectionNumber = Math.abs(RatingAction.ActualSection);	
        SectionDigit010 = SectionNumber % 100 / 10;
        SectionDigit001 = SectionNumber % 10;
        
        // register and initialize NumberPicker widgets
        sd001 = (NumberPicker) findViewById(R.id.section_digit_001);
        sd001.setOnChangeListener(this);
        sd001.mCurrent = SectionDigit001; 
        sd001.updateView();

        sd010 = (NumberPicker) findViewById(R.id.section_digit_010);
        sd010.setOnChangeListener(this);
        sd010.mCurrent = SectionDigit010; 
        sd010.updateView();
        
        // register buttons
        v = findViewById(R.id.section_ok);
        v.setOnClickListener(this);

        v = findViewById(R.id.section_abort);
        v.setOnClickListener(this);
    }
    
	@Override
	public void onChanged(NumberPicker picker, int oldVal, int newVal) 
	{
		switch(picker.getId())
		{
			case R.id.section_digit_001:
				SectionDigit001 = newVal;
				break;
			case R.id.section_digit_010:
				SectionDigit010 = newVal;			
				break;
		}
		SectionNumber = SectionDigit010 * 10 + SectionDigit001;
		FreeZoneAssist.KeyClick();
		FreeZoneAssist.HapticFeedback();
	}

	@Override
	public void onClick(View v) 
	{
		switch(v.getId())
		{
			case R.id.section_ok:
				SectionDigit001 = sd001.mCurrent;
				SectionDigit010 = sd010.mCurrent;
				SectionNumber = SectionDigit010 * 10 + SectionDigit001;
				RatingAction.ActualSection = SectionNumber;
				setResult(RESULT_OK);
				finish();
				break;
			case R.id.section_abort:				
				setResult(RESULT_CANCELED);
				this.finish();
				break;
		}		
		FreeZoneAssist.KeyClick();
		FreeZoneAssist.HapticFeedback();
	}

}
