package com.databases.example;

import group.pals.android.lib.ui.lockpattern.LockPatternActivity;

import java.util.ArrayList;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;

public class Main extends SherlockActivity {	
	//Flag used for lockscreen
	private static final int _ReqSignIn = 1;

	//SlidingMenu
	private SliderMenu menu;

	private SQLiteOpenHelper dh = null;

	//Variables for the ListView
	public ArrayList<String> results = new ArrayList<String>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//For Clear preferences!!!!!! REMOVE EVENTUALLY
		//SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		//Editor editor = settings.edit();
		//editor.clear();
		//editor.commit();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Main.this);
		boolean lockEnabled = prefs.getBoolean("checkbox_lock_enabled", false);

		if(lockEnabled){
			confirmPattern();
		}
		
		setContentView(R.layout.cards);
		
		//Add Sliding Menu
		menu = new SliderMenu(this);
		menu.attachToActivity(this, SlidingMenu.SLIDING_CONTENT);
				
	}// end onCreate

	//Method handling 'mouse-click'
	public OnClickListener buttonListener = new OnClickListener() {
		public void onClick(View view) {
			switch (view.getId()) {

			case R.id.dashboard_checkbook:
				createDatabase();
				Intent intentCheckbook = new Intent(Main.this, Checkbook.class);
				startActivity(intentCheckbook);
				break;

			case R.id.slidingmenu_checkbook:
				Toast.makeText(Main.this, "Here...", Toast.LENGTH_LONG).show();
				break;

			case R.id.dashboard_manage:
				//	createDatabase();
				Intent intentManage = new Intent(Main.this, SD.class);
				startActivity(intentManage);
				break;

			case R.id.dashboard_schedules:
				//	createDatabase();
				Intent intentSchedules = new Intent(Main.this, Plans.class);
				startActivity(intentSchedules);
				//confirmPattern();
				break;

			case R.id.dashboard_statistics:
				//	createDatabase();
				//	Intent intentStats = new Intent(Main.this, Accounts.class);
				//	startActivity(intentStats);
				//drawPattern();
				break;

			case R.id.dashboard_exit:
				Main.this.finish();
				//android.os.Process.killProcess(android.os.Process.myPid());				
				onDestroy();
				//Intent i = new Intent();
				//i.setAction(Intent.ACTION_MAIN);
				//i.addCategory(Intent.CATEGORY_HOME);
				//startActivity(i); 
				finish(); 
				break;	

			default:
				Log.e("Main", "Default onClickListner fired?");
				break;	
			}

		}// end onClick
	};// end onClickListener

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	//Over-rode method to handle database closing, prevent corruption
	@Override
	public void onPause(){
		if(dh!=null){
			dh.close();
		}
		super.onPause();
	}
	//Method for Creating Database
	public void createDatabase(){

		//If this is the first time running program...
		if(true){
			try {
				dh = new DatabaseHelper(this);
				dh.getWritableDatabase();
			} 
			catch (Exception e) {
				Log.e("Main-createDatabase", "Error e=" + e);
				Toast.makeText(this, "Error Creating Database!!!\n\n" + e, Toast.LENGTH_LONG).show();
			}

		}//end if

	}//end createDatabase

	//For Menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.layout.main_menu, menu);
		return true;
	}

	//For Menu Items
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case android.R.id.home:
			menu.toggle();
			break;

		case R.id.main_menu_search:    
			onSearchRequested();
			break;

		}
		return true;
	}

	//Override method to send the search extra data, letting it know which class called it
	@Override
	public boolean onSearchRequested() {
		Bundle appData = new Bundle();
		startSearch(null, false, appData, false);
		return true;
	}

	//Confirm Lockscreen
	public void confirmPattern(){
		Intent intent = new Intent(Main.this, LockPatternActivity.class);
		intent.putExtra(LockPatternActivity._Mode, LockPatternActivity.LPMode.ComparePattern);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Main.this);
		String savedPattern = prefs.getString("myPattern", null);

		if(savedPattern!=null){
			intent.putExtra(LockPatternActivity._Pattern, savedPattern);
			startActivityForResult(intent, _ReqSignIn);
		}
		else{
			Toast.makeText(Main.this, "Cannot Use Lockscreen\nNo Pattern Set Yet", Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case _ReqSignIn:
			if (resultCode == RESULT_OK) {
				// signing in ok
				Toast.makeText(Main.this, "Sign In\nAccepted", Toast.LENGTH_SHORT).show();
			} else {
				// signing in failed
				Toast.makeText(Main.this, "Sign In\nFailed", Toast.LENGTH_SHORT).show();
				this.finish();
				this.moveTaskToBack(true);
				super.onDestroy();
			}
			break;

		}
	}

}// end Main 