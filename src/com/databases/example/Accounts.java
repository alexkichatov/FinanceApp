package com.databases.example;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.CursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ListView;

import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.app.LoaderManager;
import com.actionbarsherlock.app.SherlockDialogFragment;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;

public class Accounts extends SherlockFragment implements OnSharedPreferenceChangeListener, LoaderManager.LoaderCallbacks<Cursor> {

	final int PICKFILE_RESULT_CODE = 1;

	private static final int ACCOUNTS_LOADER = 123456789;

	private static DatabaseHelper dh = null;

	//Constants for ContextMenu
	int CONTEXT_MENU_OPEN=1;
	int CONTEXT_MENU_EDIT=2;
	int CONTEXT_MENU_DELETE=3;
	int CONTEXT_MENU_ATTACH=4;

	//Spinners for transfers
	static Spinner transferSpinnerTo;
	static Spinner transferSpinnerFrom;
	static SimpleCursorAdapter transferSpinnerAdapterFrom = null;
	static SimpleCursorAdapter transferSpinnerAdapterTo = null;

	View myFragmentView;

	//Date Format to use for time (01:42 PM)
	final static SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a");

	//Date Format to use for date (03-26-2013)
	final static SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy");		

	ListView lv = null;
	static UserItemAdapter adapterAccounts = null;

	//Method called upon first creation
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.getLoaderManager();

		dh = new DatabaseHelper(getActivity());

		//Arguments
		Bundle bundle=getArguments();

		//bundle is empty if from search, so don't add extra menu options
		if(bundle!=null || savedInstanceState!=null){
			setHasOptionsMenu(true);
		}

		setRetainInstance(false);

	}// end onCreate

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		//myFragmentView = inflater.inflate(R.layout.accounts, container, false);	
		myFragmentView = inflater.inflate(R.layout.accounts, null, false);

		lv = (ListView)myFragmentView.findViewById(R.id.account_list);

		//Turn clicks on
		lv.setClickable(true);
		lv.setLongClickable(true);

		//Set Listener for regular mouse click
		lv.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> l, View v, int position, long id) {
				int selectionRowID = (int) adapterAccounts.getItemId(position);
				//NOTE: LIMIT *position*,*how many after*
				//				String sqlCommand = "SELECT * FROM " + tblAccounts + 
				//						" WHERE AcctID IN (SELECT AcctID FROM (SELECT AcctID FROM " + tblAccounts + 
				//						" LIMIT " + (selectionRowID-1) + ",1)AS tmp)";

				Cursor c = getActivity().getContentResolver().query(Uri.parse(MyContentProvider.ACCOUNTS_URI+"/"+(selectionRowID)), null, null, null, null);

				c.moveToFirst();
				int	entry_id = c.getInt(0);

				c.close();

				View checkbook_frame = getActivity().findViewById(R.id.checkbook_frag_frame);

				if(checkbook_frame!=null){
					//Data to send to transaction fragment
					Bundle args = new Bundle();
					args.putInt("ID",entry_id);

					//Add the fragment to the activity, pushing this transaction
					//on to the back stack.
					Transactions tran_frag = new Transactions();
					tran_frag.setArguments(args);
					FragmentTransaction ft = getFragmentManager().beginTransaction();
					ft.replace(R.id.checkbook_frag_frame, tran_frag);
					ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
					ft.addToBackStack(null);
					ft.commit();
					getFragmentManager().executePendingTransactions();
				}
				else{

					//Data to send to transaction fragment
					Bundle args = new Bundle();
					args.putBoolean("showAll", false);
					args.putBoolean("boolSearch", false);
					args.putInt("ID",entry_id);

					//Add the fragment to the activity, pushing this transaction
					//on to the back stack.
					Transactions tran_frag = new Transactions();
					tran_frag.setArguments(args);
					FragmentTransaction ft = getFragmentManager().beginTransaction();
					ft.replace(R.id.transaction_frag_frame, tran_frag);
					ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
					ft.commit();
					getFragmentManager().executePendingTransactions();
				}

			}// end onItemClick

		}//end onItemClickListener
				);//end setOnItemClickListener


		//Allows Context Menus for each item of the list view
		registerForContextMenu(lv);

		//Set up a listener for changes in settings menu
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
		prefs.registerOnSharedPreferenceChangeListener(this);

		TextView noResult = (TextView)myFragmentView.findViewById(R.id.account_noTransaction);
		lv.setEmptyView(noResult);

		adapterAccounts = new UserItemAdapter(this.getActivity(), null);
		lv.setAdapter(adapterAccounts);

		populate();

		return myFragmentView;
	}

	//Method called after creation, populates list with account information
	protected void populate() {
		Log.e("Accounts","populating");
		//Arguments sent by Account Fragment
		Bundle bundle=getArguments();
		boolean searchFragment=true;

		if(bundle!=null){
			searchFragment = bundle.getBoolean("boolSearch");
		}

		//Fragment is a search fragment
		if(searchFragment){

			//Word being searched
			String query = getActivity().getIntent().getStringExtra(SearchManager.QUERY);			

			try{
				Bundle b = new Bundle();
				b.putBoolean("boolSearch", true);
				b.putString("query", query);
				getLoaderManager().initLoader(ACCOUNTS_LOADER, b, this);
			}
			catch(Exception e){
				Log.e("Accounts-populate","Search Failed. Error e="+e);
				Toast.makeText(this.getActivity(), "Search Failed\n"+e, Toast.LENGTH_LONG).show();
			}

		}

		//Not A Search Fragment
		else{
			getLoaderManager().initLoader(ACCOUNTS_LOADER, bundle, this);
		}

		calculateBalance();

	}//end populate

	//Creates menu for long presses
	@Override  
	public void onCreateContextMenu(ContextMenu menu, View v,ContextMenuInfo menuInfo) {  
		super.onCreateContextMenu(menu, v, menuInfo);

		AdapterView.AdapterContextMenuInfo itemInfo = (AdapterView.AdapterContextMenuInfo)menuInfo;
		String name = "" + adapterAccounts.getAccount(itemInfo.position).name;

		menu.setHeaderTitle(name);  
		menu.add(0, CONTEXT_MENU_OPEN, 0, "Open");  
		menu.add(0, CONTEXT_MENU_EDIT, 1, "Edit");
		menu.add(0, CONTEXT_MENU_DELETE, 2, "Delete");
		menu.add(0, CONTEXT_MENU_ATTACH, 3, "Attach");

	}  

	//Handles which methods are called when using the long presses menu
	@Override  
	public boolean onContextItemSelected(android.view.MenuItem item) {

		if(item.getItemId()==CONTEXT_MENU_OPEN){
			accountOpen(item);
			return true;
		}  
		else if(item.getItemId()==CONTEXT_MENU_EDIT){
			accountEdit(item);
			return true;
		}
		else if(item.getItemId()==CONTEXT_MENU_DELETE){
			accountDelete(item);
			return true;
		}
		else if(item.getItemId()==CONTEXT_MENU_ATTACH){
			accountAttach(item);
			return true;
		}
		else {
			//return false;
			//return super.onContextItemSelected(item);
		}  

		return super.onContextItemSelected(item);  
	}  

	//For Opening an Account
	public void accountOpen(android.view.MenuItem item){  
		AdapterView.AdapterContextMenuInfo itemInfo = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		String id = adapterAccounts.getAccount(itemInfo.position).id;

		DialogFragment newFragment = ViewDialogFragment.newInstance(id);
		newFragment.show(getChildFragmentManager(), "dialogView");

	}

	//For Editing an Account
	public void accountEdit(android.view.MenuItem item){
		final AdapterView.AdapterContextMenuInfo itemInfo = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		final AccountRecord record = adapterAccounts.getAccount(itemInfo.position);

		DialogFragment newFragment = EditDialogFragment.newInstance(record);
		newFragment.show(getChildFragmentManager(), "dialogEdit");
	}

	//For Attaching to an Account
	public void accountAttach(android.view.MenuItem item){
		final AdapterView.AdapterContextMenuInfo itemInfo = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		final AccountRecord record = adapterAccounts.getAccount(itemInfo.position);

		Intent intentLink = new Intent(this.getActivity(), Links.class);
		intentLink.putExtra("AcctID", record.id);
		intentLink.putExtra("AcctName", record.name);
		startActivityForResult(intentLink, PICKFILE_RESULT_CODE);

	}

	//For Deleting an Account
	public void accountDelete(android.view.MenuItem item){
		AdapterView.AdapterContextMenuInfo itemInfo = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		AccountRecord record = adapterAccounts.getAccount(itemInfo.position);
		Uri uri = Uri.parse(MyContentProvider.ACCOUNTS_URI + "/" + record.id);

		//Delete Account
		getActivity().getContentResolver().delete(uri,"AcctID="+record.id, null);

		//Delete Transactions of that account
		uri = Uri.parse(MyContentProvider.TRANSACTIONS_URI + "/" + 0);
		getActivity().getContentResolver().delete(uri,"ToAcctID="+record.id, null);

		Toast.makeText(this.getActivity(), "Deleted Item:\n" + record.name, Toast.LENGTH_SHORT).show();
	}//end of accountDelete

	//For Adding an Account
	public void accountAdd(){
		DialogFragment newFragment = AddDialogFragment.newInstance();
		newFragment.show(getChildFragmentManager(), "dialogAdd");
	}	

	//For Transferring from an Account
	public void accountTransfer(){
		DialogFragment newFragment = TransferDialogFragment.newInstance();
		newFragment.show(getChildFragmentManager(), "dialogTransfer");		
	}	

	//Method to get the list of accounts for spinner
	public void accountPopulate(){
		Cursor accountCursor1 = getActivity().getContentResolver().query(MyContentProvider.ACCOUNTS_URI, null, null, null, null);
		Cursor accountCursor2 = getActivity().getContentResolver().query(MyContentProvider.ACCOUNTS_URI, null, null, null, null);
		getActivity().startManagingCursor(accountCursor1);
		getActivity().startManagingCursor(accountCursor2);
		String[] from = new String[] {"AcctName", "_id"}; 
		int[] to = new int[] { android.R.id.text1};

		transferSpinnerAdapterFrom = new SimpleCursorAdapter(getActivity(), android.R.layout.simple_spinner_item, accountCursor1, from, to);
		transferSpinnerAdapterFrom.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		transferSpinnerAdapterTo = new SimpleCursorAdapter(getActivity(), android.R.layout.simple_spinner_item, accountCursor2, from, to);
		transferSpinnerAdapterTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		transferSpinnerTo.setAdapter(transferSpinnerAdapterTo);
		transferSpinnerFrom.setAdapter(transferSpinnerAdapterFrom);
	}//end of accountPopulate


	//Handle closing database properly to avoid corruption
	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	//For Menu
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);

		View transaction_frame = getActivity().findViewById(R.id.transaction_frag_frame);

		//Clear any leftover junk
		menu.clear();

		if(transaction_frame!=null){

			//Show Search
			MenuItem menuSearch = menu.add(com.actionbarsherlock.view.Menu.NONE, R.id.account_menu_search, com.actionbarsherlock.view.Menu.NONE, "Search");
			menuSearch.setIcon(android.R.drawable.ic_menu_search);
			menuSearch.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

			SubMenu subMenu1 = menu.addSubMenu("Account");
			subMenu1.add(com.actionbarsherlock.view.Menu.NONE, R.id.account_menu_add, com.actionbarsherlock.view.Menu.NONE, "Add");
			subMenu1.add(com.actionbarsherlock.view.Menu.NONE, R.id.account_menu_transfer, com.actionbarsherlock.view.Menu.NONE, "Transfer");
			subMenu1.add(com.actionbarsherlock.view.Menu.NONE, R.id.account_menu_unknown, com.actionbarsherlock.view.Menu.NONE, "Unknown");

			MenuItem subMenu1Item = subMenu1.getItem();
			subMenu1Item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
		}
		else{
			inflater.inflate(R.layout.account_menu, menu);
		}

	}

	//For Menu Items
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:    
			//Intent intentUp = new Intent(Accounts.this.getActivity(), Main.class);
			//intentUp.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			//startActivity(intentUp);
			//menu.toggle();
			break;

		case R.id.account_menu_add:    
			accountAdd();
			return true;

		case R.id.account_menu_search:    
			getActivity().onSearchRequested();
			return true;

		case R.id.account_menu_transfer:    
			accountTransfer();
			return true;

		case R.id.account_menu_unknown:    
			//Insert Unknown Code Here
			pickFile(null);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	//Used after a change in settings occurs
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		populate();
	}

	//Calculates the balance
	public void calculateBalance(){
		float totalBalance = 0;

		Cursor c = dh.sumAccounts();
		c.moveToFirst();
		try{
			totalBalance = c.getFloat(0);
			TextView balance = (TextView)this.myFragmentView.findViewById(R.id.account_total_balance);
			balance.setText("Total Balance: " + totalBalance);
		}
		catch(Exception e){
			Log.e("Accounts-calculateBalance", "No Accounts? Error e="+e);
			TextView balance = (TextView)this.myFragmentView.findViewById(R.id.account_total_balance);
			balance.setText("Total Balance: " + "BALANCED" );
		}

		c.close();
	}

	//Method used to handle picking a file
	void pickFile(File aFile) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		startActivityForResult(intent,PICKFILE_RESULT_CODE);
	}

	//Method called after picking a file
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data){
		switch (requestCode) {
		case PICKFILE_RESULT_CODE:
			if(resultCode==getActivity().RESULT_OK){
				Log.e("Accounts-onActivityResult", "OK");
				/******CALL POPULATE AGAIN TO SHOW THE ATTACHMENT ICON*******/

			}

			if(resultCode==getActivity().RESULT_CANCELED){
				Log.e("Accounts-onActivityResult", "canceled");
			}

			break;
		}

	}

	//Close dialogs to prevent window leaks
	@Override
	public void onPause() {
		if(dh!=null){
			dh.close();
		}
		super.onPause();
	}

	public class UserItemAdapter extends CursorAdapter {
		private Context context;

		public UserItemAdapter(Context context, Cursor accounts) {
			super(context, accounts);
			this.context = context;
			//this.accounts = accounts;
		}

		public AccountRecord getAccount(long position){
			Cursor group = getCursor();

			group.moveToPosition((int) position);
			int NameColumn = group.getColumnIndex("AcctName");
			int BalanceColumn = group.getColumnIndex("AcctBalance");
			int TimeColumn = group.getColumnIndex("AcctTime");
			int DateColumn = group.getColumnIndex("AcctDate");

			String id = group.getString(0);
			String name = group.getString(NameColumn);
			String balance = group.getString(BalanceColumn);
			String time = group.getString(TimeColumn);
			String date = group.getString(DateColumn);

			AccountRecord record = new AccountRecord(id, name, balance, time, date);
			return record;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			View v = view;
			Cursor user = getCursor();

			//For Custom View Properties
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
			boolean useDefaults = prefs.getBoolean("checkbox_default", true);

			if (user != null) {
				TextView TVname = (TextView) v.findViewById(R.id.account_name);
				TextView TVbalance = (TextView) v.findViewById(R.id.account_balance);
				TextView TVdate = (TextView) v.findViewById(R.id.account_date);
				TextView TVtime = (TextView) v.findViewById(R.id.account_time);

				int NameColumn = user.getColumnIndex("AcctName");
				int BalanceColumn = user.getColumnIndex("AcctBalance");
				int TimeColumn = user.getColumnIndex("AcctTime");
				int DateColumn = user.getColumnIndex("AcctDate");

				String id = user.getString(0);
				String name = user.getString(NameColumn);
				String balance = user.getString(BalanceColumn);
				String time = user.getString(TimeColumn);
				String date = user.getString(DateColumn);

				//Change gradient
				try{
					LinearLayout l;
					l=(LinearLayout)v.findViewById(R.id.account_gradient);
					GradientDrawable defaultGradientPos = new GradientDrawable(
							GradientDrawable.Orientation.BOTTOM_TOP,
							new int[] {0xFF00FF33,0xFF000000});

					GradientDrawable defaultGradientNeg = new GradientDrawable(
							GradientDrawable.Orientation.BOTTOM_TOP,
							new int[] {0xFFFF0000,0xFF000000});

					if(useDefaults){
						if(Float.parseFloat(balance) >=0){
							l.setBackgroundDrawable(defaultGradientPos);
						}
						else{
							l.setBackgroundDrawable(defaultGradientNeg);
						}

					}
					else{
						if(Float.parseFloat(balance) >=0){
							l.setBackgroundDrawable(defaultGradientPos);
						}
						else{
							l.setBackgroundDrawable(defaultGradientNeg);
						}
					}

				}
				catch(Exception e){
					Toast.makeText(Accounts.this.getActivity(), "Could Not Set Custom gradient", Toast.LENGTH_SHORT).show();
				}

				if (name != null) {
					TVname.setText(name);
				}

				if(balance != null) {
					TVbalance.setText("Balance: " + balance );
				}

				if(date != null) {
					TVdate.setText("Date: " + date );
				}

				if(time != null) {
					TVtime.setText("Time: " + time );
				}

			}

		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			LayoutInflater inflater = LayoutInflater.from(context);
			View v = inflater.inflate(R.layout.account_item, parent, false);

			TextView TVname = (TextView)v.findViewById(R.id.account_name);
			TextView TVbalance = (TextView)v.findViewById(R.id.account_balance);
			TextView TVtime = (TextView)v.findViewById(R.id.account_time);
			TextView TVdate = (TextView)v.findViewById(R.id.account_date);

			//For Custom View Properties
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Accounts.this.getActivity());
			boolean useDefaults = prefs.getBoolean("checkbox_default", true);

			//Change Background Colors
			try{
				LinearLayout l;
				l=(LinearLayout)v.findViewById(R.id.account_layout);
				int startColor = prefs.getInt("key_account_startBackgroundColor", Color.parseColor("#E8E8E8"));
				int endColor = prefs.getInt("key_account_endBackgroundColor", Color.parseColor("#FFFFFF"));
				GradientDrawable defaultGradient = new GradientDrawable(
						GradientDrawable.Orientation.BOTTOM_TOP,
						new int[] {startColor,endColor});

				if(useDefaults){
					l.setBackgroundResource(R.drawable.account_list_style);
				}
				else{
					l.setBackgroundDrawable(defaultGradient);
				}

			}
			catch(Exception e){
				Toast.makeText(Accounts.this.getActivity(), "Could Not Set Custom Background Color", Toast.LENGTH_SHORT).show();
			}

			//Change Size of main field
			try{
				String DefaultSize = prefs.getString(Accounts.this.getString(R.string.pref_key_account_nameSize), "16");

				if(useDefaults){
					TVname.setTextSize(16);
				}
				else{
					TVname.setTextSize(Integer.parseInt(DefaultSize));
				}

			}
			catch(Exception e){
				Toast.makeText(Accounts.this.getActivity(), "Could Not Set Custom Name Size", Toast.LENGTH_SHORT).show();
			}

			try{
				int DefaultColor = prefs.getInt("key_account_nameColor", Color.parseColor("#000000"));

				if(useDefaults){
					TVname.setTextColor(Color.parseColor("#000000"));
				}
				else{
					TVname.setTextColor(DefaultColor);
				}

			}
			catch(Exception e){
				Toast.makeText(Accounts.this.getActivity(), "Could Not Set Custom Name Size", Toast.LENGTH_SHORT).show();
			}

			try{
				String DefaultSize = prefs.getString(Accounts.this.getString(R.string.pref_key_account_fieldSize), "10");

				if(useDefaults){
					TVbalance.setTextSize(10);
					TVdate.setTextSize(10);
					TVtime.setTextSize(10);
				}
				else{
					TVbalance.setTextSize(Integer.parseInt(DefaultSize));
					TVdate.setTextSize(Integer.parseInt(DefaultSize));
					TVtime.setTextSize(Integer.parseInt(DefaultSize));
				}

			}
			catch(Exception e){
				Toast.makeText(Accounts.this.getActivity(), "Could Not Set Custom Field Size", Toast.LENGTH_SHORT).show();
			}

			try{
				int DefaultColor = prefs.getInt("key_account_fieldColor", Color.parseColor("#0099CC"));

				if(useDefaults){
					TVbalance.setTextColor(Color.parseColor("#0099CC"));
					TVdate.setTextColor(Color.parseColor("#0099CC"));
					TVtime.setTextColor(Color.parseColor("#0099CC"));
				}
				else{
					TVbalance.setTextColor(DefaultColor);
					TVdate.setTextColor(DefaultColor);
					TVtime.setTextColor(DefaultColor);
				}

			}
			catch(Exception e){
				Toast.makeText(Accounts.this.getActivity(), "Could Not Set Custom Field Color", Toast.LENGTH_SHORT).show();
			}


			//For User-Defined Field Visibility
			if(useDefaults||prefs.getBoolean("checkbox_account_nameField", true)){
				TVname.setVisibility(View.VISIBLE);
			}
			else{
				TVname.setVisibility(View.GONE);
			}

			if(useDefaults||prefs.getBoolean("checkbox_account_balanceField", true)){
				TVbalance.setVisibility(View.VISIBLE);
			}
			else{
				TVbalance.setVisibility(View.GONE);
			}

			if(useDefaults||prefs.getBoolean("checkbox_account_dateField", true)){
				TVdate.setVisibility(View.VISIBLE);
			}
			else{
				TVdate.setVisibility(View.GONE);
			}

			if(useDefaults||prefs.getBoolean("checkbox_account_timeField", true)){
				TVtime.setVisibility(View.VISIBLE);
			}
			else{
				TVtime.setVisibility(View.GONE);
			}

			return v;

		}
	}

	//An Object Class used to hold the data of each account record
	public class AccountRecord {
		protected String id;
		protected String name;
		protected String balance;
		protected String date;
		protected String time;

		public AccountRecord(String id, String name, String balance, String date, String time) {
			this.id = id;
			this.name = name;
			this.balance = balance;
			this.date = date;
			this.time = time;
		}
	}

	//Class that handles view fragment
	public static class ViewDialogFragment extends SherlockDialogFragment {

		public static ViewDialogFragment newInstance(String id) {
			ViewDialogFragment frag = new ViewDialogFragment();
			Bundle args = new Bundle();
			args.putString("id", id);
			frag.setArguments(args);
			return frag;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			final String ID = getArguments().getString("id");

			Cursor c = getActivity().getContentResolver().query(Uri.parse(MyContentProvider.ACCOUNTS_URI+"/"+(ID)), null, null, null, null);

			int entry_id = 0;
			String entry_name = null;
			String entry_balance = null;
			String entry_time = null;
			String entry_date = null;

			c.moveToFirst();
			do{
				entry_id = c.getInt(c.getColumnIndex("AcctID"));
				entry_name = c.getString(c.getColumnIndex("AcctName"));
				entry_balance = c.getString(c.getColumnIndex("AcctBalance"));
				entry_time = c.getString(c.getColumnIndex("AcctTime"));
				entry_date = c.getString(c.getColumnIndex("AcctDate"));
			}while(c.moveToNext());

			LayoutInflater li = LayoutInflater.from(this.getSherlockActivity());
			View accountStatsView = li.inflate(R.layout.account_stats, null);

			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
					this.getSherlockActivity());

			alertDialogBuilder.setView(accountStatsView);

			//Set Title
			alertDialogBuilder.setTitle("View Account");

			//Set dialog message
			alertDialogBuilder
			.setCancelable(true);

			//Set Statistics
			TextView statsName = (TextView)accountStatsView.findViewById(R.id.TextAccountName);
			statsName.setText(entry_name);
			TextView statsValue = (TextView)accountStatsView.findViewById(R.id.TextAccountValue);
			statsValue.setText(entry_balance);
			TextView statsDate = (TextView)accountStatsView.findViewById(R.id.TextAccountDate);
			statsDate.setText(entry_date);
			TextView statsTime = (TextView)accountStatsView.findViewById(R.id.TextAccountTime);
			statsTime.setText(entry_time);

			c.close();
			return alertDialogBuilder.create();

		}
	}

	//Class that handles edit fragment
	public static class EditDialogFragment extends SherlockDialogFragment {

		public static EditDialogFragment newInstance(AccountRecord record) {
			EditDialogFragment frag = new EditDialogFragment();
			Bundle args = new Bundle();
			args.putString("id", record.id);
			args.putString("name", record.name);
			args.putString("balance", record.balance);
			args.putString("date", record.date);
			args.putString("time", record.time);
			frag.setArguments(args);
			return frag;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			final String ID = getArguments().getString("id");
			final String name = getArguments().getString("name");
			final String balance = getArguments().getString("balance");

			LayoutInflater li = LayoutInflater.from(getActivity());
			final View promptsView = li.inflate(R.layout.account_add, null);

			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());

			// set account_add.xml to AlertDialog builder
			alertDialogBuilder.setView(promptsView);

			//set Title
			alertDialogBuilder.setTitle("Edit An Account");

			//Add the previous info into the fields, remove unnecessary fields
			final EditText aName = (EditText) promptsView.findViewById(R.id.EditAccountName);
			final EditText aBalance = (EditText) promptsView.findViewById(R.id.EditAccountBalance);
			TextView aBalanceText = (TextView)promptsView.findViewById(R.id.BalanceTexts);
			aName.setText(name);
			aBalance.setVisibility(View.GONE);
			aBalanceText.setVisibility(View.GONE);

			// set dialog message
			alertDialogBuilder
			.setCancelable(false)
			.setPositiveButton("Save",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					// CODE FOR "OK"
					String accountName = null;
					String accountTime = null;
					String accountBalance = null;
					String accountDate = null;

					accountName = aName.getText().toString().trim();
					accountBalance = balance.trim();
					final Calendar c = Calendar.getInstance();
					accountTime = timeFormat.format(c.getTime());
					accountDate = dateFormat.format(c.getTime());

					try{
						//Delete Old Record
						Uri uri = Uri.parse(MyContentProvider.ACCOUNTS_URI + "/" + ID);
						getActivity().getContentResolver().delete(uri, "AcctID="+ID, null);

						ContentValues accountValues=new ContentValues();
						accountValues.put("AcctID",ID);
						accountValues.put("AcctName",accountName);
						accountValues.put("AcctBalance",accountBalance);
						accountValues.put("AcctTime",accountTime);
						accountValues.put("AcctDate",accountDate);

						//Make new record with same ID
						getActivity().getContentResolver().insert(MyContentProvider.ACCOUNTS_URI, accountValues);

					}
					catch(Exception e){
						Toast.makeText(getActivity(), "Error Editing Account!\nDid you enter valid input? ", Toast.LENGTH_SHORT).show();
					}

				}//end onClick "OK"
			})
			.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					// CODE FOR "Cancel"
					dialog.cancel();
				}
			});

			return alertDialogBuilder.create();

		}
	}

	//Class that handles add fragment
	public static class AddDialogFragment extends SherlockDialogFragment {

		public static AddDialogFragment newInstance() {
			AddDialogFragment frag = new AddDialogFragment();
			Bundle args = new Bundle();
			frag.setArguments(args);
			return frag;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			LayoutInflater li = LayoutInflater.from(getActivity());
			final View promptsView = li.inflate(R.layout.account_add, null);

			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());

			alertDialogBuilder.setView(promptsView);

			//Set Title
			alertDialogBuilder.setTitle("Add An Account");

			//Set dialog message
			alertDialogBuilder
			.setCancelable(false)
			.setPositiveButton("Save",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					String accountName = null;
					String accountTime = null;
					String accountBalance = null;
					String accountDate = null;

					//Variables for adding the account
					EditText aName = (EditText) promptsView.findViewById(R.id.EditAccountName);
					EditText aBalance = (EditText) promptsView.findViewById(R.id.EditAccountBalance);
					accountName = aName.getText().toString().trim();
					accountBalance = aBalance.getText().toString().trim();

					final Calendar cal = Calendar.getInstance();
					accountTime = timeFormat.format(cal.getTime());
					accountDate = dateFormat.format(cal.getTime());

					//Variables for adding Starting Balance transaction
					final String transactionName = "STARTING BALANCE";
					float transactionValue;
					final String transactionPlanId = "0";
					final String transactionCategory = "STARTING BALANCE";
					final String transactionCheckNum = "None";
					final String transactionMemo = "This is an automatically generated transaction created when you add an account";
					final String transactionTime = accountTime;
					final String transactionDate = accountDate;
					final String transactionCleared = "true";
					String transactionType = "Unknown";

					//Check Value to see if it's valid
					try{
						transactionValue = Float.parseFloat(accountBalance);
					}
					catch(Exception e){
						transactionValue = (float) 0.00;
						accountBalance = "0";
					}				

					try{
						if(Float.parseFloat(accountBalance)>=0){
							transactionType = "Deposit";
						}
						else{
							transactionType = "Withdrawl";
							transactionValue = transactionValue * -1;
						}
					}
					catch(Exception e){
						Toast.makeText(getActivity(), "Error\nWas balance a valid format?", Toast.LENGTH_SHORT).show();
					}

					try{
						if (accountName.length()>0) {

							ContentValues accountValues=new ContentValues();
							accountValues.put("AcctName",accountName);
							accountValues.put("AcctBalance",accountBalance);
							accountValues.put("AcctTime",accountTime);
							accountValues.put("AcctDate",accountDate);

							//Insert values into accounts table
							Uri u = getActivity().getContentResolver().insert(MyContentProvider.ACCOUNTS_URI, accountValues);

							ContentValues transactionValues=new ContentValues();
							transactionValues.put("ToAcctID", Long.parseLong(u.getLastPathSegment()));
							transactionValues.put("ToPlanID", transactionPlanId);
							transactionValues.put("TransName", transactionName);
							transactionValues.put("TransValue", transactionValue);
							transactionValues.put("TransType", transactionType);
							transactionValues.put("TransCategory", transactionCategory);
							transactionValues.put("TransCheckNum", transactionCheckNum);
							transactionValues.put("TransMemo", transactionMemo);
							transactionValues.put("TransTime", transactionTime);
							transactionValues.put("TransDate", transactionDate);
							transactionValues.put("TransCleared", transactionCleared);

							//Insert values into accounts table
							getActivity().getContentResolver().insert(MyContentProvider.TRANSACTIONS_URI, transactionValues);
						} 

						else {
							Toast.makeText(getActivity(), "Needs a Name", Toast.LENGTH_SHORT).show();
						}

					}
					catch(Exception e){
						Log.e("Accounts-AddDialog", "Exception e="+e);
						Toast.makeText(getActivity(), "Error Adding Account!\nDid you enter valid input? ", Toast.LENGTH_SHORT).show();
					}

				}//end onClick "OK"
			})
			.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					// CODE FOR "Cancel"
					dialog.cancel();
				}
			});

			return alertDialogBuilder.create();
		}
	}

	//Class that handles transfers fragment
	public static class TransferDialogFragment extends SherlockDialogFragment {

		public static TransferDialogFragment newInstance() {
			TransferDialogFragment frag = new TransferDialogFragment();
			Bundle args = new Bundle();
			frag.setArguments(args);
			return frag;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			LayoutInflater li = LayoutInflater.from(getActivity());
			final View promptsView = li.inflate(R.layout.account_transfer, null);

			final EditText tAmount = (EditText) promptsView.findViewById(R.id.EditAccountAmount);

			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());

			alertDialogBuilder.setView(promptsView);

			//Set Title
			alertDialogBuilder.setTitle("Transfer Money");

			transferSpinnerFrom = (Spinner)promptsView.findViewById(R.id.SpinnerAccountFrom);
			transferSpinnerTo = (Spinner)promptsView.findViewById(R.id.SpinnerAccountTo);

			//Populate Account Drop-down List
			((Accounts) getParentFragment()).accountPopulate();

			//Set dialog message
			alertDialogBuilder
			.setCancelable(false)
			.setPositiveButton("Transfer",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {

					//Needed to get account's name from DB-populated spinner
					int accountPosition1 = transferSpinnerFrom.getSelectedItemPosition();
					Cursor cursorAccount1 = (Cursor) transferSpinnerAdapterFrom.getItem(accountPosition1);				

					int accountPosition2 = transferSpinnerTo.getSelectedItemPosition();
					Cursor cursorAccount2 = (Cursor) transferSpinnerAdapterTo.getItem(accountPosition2);

					String transferAmount = tAmount.getText().toString().trim();
					String transferFrom = null;
					String transferTo = null;
					String transferToID = null;
					String transferFromID = null;

					try{
						transferFrom = cursorAccount1.getString(cursorAccount1.getColumnIndex("AcctName"));
						transferFromID = cursorAccount1.getString(cursorAccount1.getColumnIndex("_id"));
						transferTo = cursorAccount2.getString(cursorAccount2.getColumnIndex("AcctName"));
						transferToID = cursorAccount2.getString(cursorAccount2.getColumnIndex("_id"));
					}
					catch(Exception e){
						Log.e("Account-transferDialog","No Accounts? Exception e=" + e);
						dialog.cancel();
						Toast.makeText(getActivity(), "No Accounts \n\nUse The ActionBar To Create Accounts", Toast.LENGTH_LONG).show();
						return;
					}					

					Log.d("Account-Transfer", "From:"+transferFrom + " To:"+transferTo + " Amount:" + transferAmount);

					//Transfer From
					final Calendar cal = Calendar.getInstance();
					String transferTime = timeFormat.format(cal.getTime());
					String transferDate = dateFormat.format(cal.getTime());

					float tAmount;
					final String transferName = "TRANSFER";
					final String transferPlanId = "0";
					final String transferCategory = "TRANSFER";
					final String transferCheckNum = "None";
					final String transferMemo = "This is an automatically generated transaction created when you transfer money";
					final String transferCleared = "true";
					String transferType = "Withdraw";

					//Check Value to see if it's valid
					try{
						tAmount = Float.parseFloat(transferAmount);
					}
					catch(Exception e){
						Log.e("Accounts-transfer", "Invalid amount? Error e="+e);
						return;
					}				

					try{
						ContentValues transferFromValues=new ContentValues();
						transferFromValues.put("ToAcctID", transferFromID);
						transferFromValues.put("ToPlanID", transferPlanId);
						transferFromValues.put("TransName", transferName);
						transferFromValues.put("TransValue", tAmount);
						transferFromValues.put("TransType", transferType);
						transferFromValues.put("TransCategory", transferCategory);
						transferFromValues.put("TransCheckNum", transferCheckNum);
						transferFromValues.put("TransMemo", transferMemo);
						transferFromValues.put("TransTime", transferTime);
						transferFromValues.put("TransDate", transferDate);
						transferFromValues.put("TransCleared", transferCleared);

						//Insert values into transaction table
						getActivity().getContentResolver().insert(MyContentProvider.TRANSACTIONS_URI, transferFromValues);

						//Update Account Info
						ContentValues accountValues=new ContentValues();

						Cursor c = getActivity().getContentResolver().query(Uri.parse(MyContentProvider.ACCOUNTS_URI+"/"+transferFromID), null, null, null, null);

						int entry_id = 0;
						String entry_name = null;
						String entry_balance = null;
						String entry_time = null;
						String entry_date = null;

						c.moveToFirst();
						do{
							entry_id = c.getInt(c.getColumnIndex("AcctID"));
							entry_name = c.getString(c.getColumnIndex("AcctName"));
							entry_balance = Float.parseFloat(c.getString(c.getColumnIndex("AcctBalance")))-tAmount+"";
							entry_time = c.getString(c.getColumnIndex("AcctTime"));
							entry_date = c.getString(c.getColumnIndex("AcctDate"));
						}while(c.moveToNext());

						accountValues.put("AcctID",entry_id);
						accountValues.put("AcctName",entry_name);
						accountValues.put("AcctBalance",entry_balance);
						accountValues.put("AcctTime",entry_time);
						accountValues.put("AcctDate",entry_date);

						getActivity().getContentResolver().update(Uri.parse(MyContentProvider.ACCOUNTS_URI+"/"+transferFromID), accountValues,"AcctID ="+transferFromID, null);
						c.close();

					} catch(Exception e){
						Log.e("Accounts-transferDialog", "Transfer From failed. Exception e="+e);
						Toast.makeText(getActivity(), "Error Transferring!\n Did you enter valid input? ", Toast.LENGTH_SHORT).show();
						return;
					}

					//Transfer To
					transferType = "Deposit";

					try{
						ContentValues transferToValues=new ContentValues();
						transferToValues.put("ToAcctID", transferToID);
						transferToValues.put("ToPlanID", transferPlanId);
						transferToValues.put("TransName", transferName);
						transferToValues.put("TransValue", tAmount);
						transferToValues.put("TransType", transferType);
						transferToValues.put("TransCategory", transferCategory);
						transferToValues.put("TransCheckNum", transferCheckNum);
						transferToValues.put("TransMemo", transferMemo);
						transferToValues.put("TransTime", transferTime);
						transferToValues.put("TransDate", transferDate);
						transferToValues.put("TransCleared", transferCleared);

						//Insert values into transaction table
						getActivity().getContentResolver().insert(MyContentProvider.TRANSACTIONS_URI, transferToValues);

						//Update Account Info
						ContentValues accountValues=new ContentValues();

						Cursor c = getActivity().getContentResolver().query(Uri.parse(MyContentProvider.ACCOUNTS_URI+"/"+transferToID), null, null, null, null);

						int entry_id = 0;
						String entry_name = null;
						String entry_balance = null;
						String entry_time = null;
						String entry_date = null;

						c.moveToFirst();
						do{
							entry_id = c.getInt(c.getColumnIndex("AcctID"));
							entry_name = c.getString(c.getColumnIndex("AcctName"));
							entry_balance = Float.parseFloat(c.getString(c.getColumnIndex("AcctBalance")))+tAmount+"";
							entry_time = c.getString(c.getColumnIndex("AcctTime"));
							entry_date = c.getString(c.getColumnIndex("AcctDate"));
						}while(c.moveToNext());

						accountValues.put("AcctID",entry_id);
						accountValues.put("AcctName",entry_name);
						accountValues.put("AcctBalance",entry_balance);
						accountValues.put("AcctTime",entry_time);
						accountValues.put("AcctDate",entry_date);

						getActivity().getContentResolver().update(Uri.parse(MyContentProvider.ACCOUNTS_URI+"/"+transferToID), accountValues,"AcctID ="+transferToID, null);
						c.close();

					} catch(Exception e){
						Log.e("Accounts-transferDialog", "Transfer To failed. Exception e="+e);
						Toast.makeText(getActivity(), "Error Transferring!\n Did you enter valid input? ", Toast.LENGTH_SHORT).show();
						return;
					}

				}//end onClick "OK"
			})
			.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					// CODE FOR "Cancel"
					dialog.cancel();
				}
			});

			return alertDialogBuilder.create();
		}
	}


	@Override
	public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {		
		Log.e("Accounts-onCreateLoader", "calling create loader...");
		switch (loaderID) {
		case ACCOUNTS_LOADER:
			if(bundle!=null && bundle.getBoolean("boolSearch")){
				String query = getActivity().getIntent().getStringExtra(SearchManager.QUERY);
				return new CursorLoader(
						getActivity(),   	// Parent activity context
						(Uri.parse(MyContentProvider.ACCOUNTS_URI + "/SEARCH/" + query)),// Table to query
						null,     			// Projection to return
						null,            	// No selection clause
						null,            	// No selection arguments
						null             	// Default sort order
						);
			}
			else{
				return new CursorLoader(
						getActivity(),   	// Parent activity context
						MyContentProvider.ACCOUNTS_URI,// Table to query
						null,     			// Projection to return
						null,            	// No selection clause
						null,            	// No selection arguments
						null             	// Default sort order
						);				
			}
		default:
			Log.e("Accounts-onCreateLoader", "Not a valid CursorLoader ID");
			return null;
		}
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		Log.e("Accounts", "load done. loader="+loader + " data="+data);
		adapterAccounts.swapCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		Log.e("Accounts", "loaderReset on " + loader);
		//Not sure what should go here....
		adapterAccounts.swapCursor(null);
	}

}//End Accounts