/* Class that handles the Transaction Fragment seen in the Checkbook screen
 * Does everything from setting up the view to Add/Delete/Edit Transactions to calculating the balance
 */

package com.databases.example.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;
import com.databases.example.R;
import com.databases.example.data.DatabaseHelper;
import com.databases.example.data.DateTime;
import com.databases.example.data.Money;
import com.databases.example.data.MyContentProvider;
import com.databases.example.data.SearchWidget;
import com.databases.example.data.TransactionRecord;
import com.databases.example.view.TransactionViewFragment;
import com.databases.example.view.TransactionsListViewAdapter;
import com.wizardpager.wizard.WizardDialogFragment;
import com.wizardpager.wizard.model.AbstractWizardModel;
import com.wizardpager.wizard.model.ModelCallbacks;
import com.wizardpager.wizard.model.Page;
import com.wizardpager.wizard.model.PageList;
import com.wizardpager.wizard.model.ReviewItem;
import com.wizardpager.wizard.ui.PageFragmentCallbacks;
import com.wizardpager.wizard.ui.StepPagerStrip;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class Transactions extends SherlockFragment implements OnSharedPreferenceChangeListener, LoaderManager.LoaderCallbacks<Cursor>{
    private static final int TRANS_LOADER = 987654321;
    private static final int TRANS_SEARCH_LOADER = 98765;
    private static final int TRANS_SUBCATEGORY_LOADER = 987;

    private View myFragmentView;

    //Used to determine if fragment should show all transactions
    private boolean showAllTransactions=false;

    private static Button tTime;
    private static Button tDate;

    //ID of account transaction belongs to
    private static int account_id=0;

    private static String sortOrder = "null";

    private ListView lv = null;

    //Constants for ContextMenu
    private final int CONTEXT_MENU_VIEW=5;
    private final int CONTEXT_MENU_EDIT=6;
    private final int CONTEXT_MENU_DELETE=7;

    //ListView Adapter
    private static TransactionsListViewAdapter adapterTransactions = null;

    //For Autocomplete
    private static ArrayList<String> dropdownResults = new ArrayList<String>();

    //Adapter for category spinner
    private static SimpleCursorAdapter adapterCategory;

    //ActionMode
    protected Object mActionMode = null;
    private SparseBooleanArray mSelectedItemsIds;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        account_id=0;
    }//end onCreate

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        myFragmentView = inflater.inflate(R.layout.transactions, null, false);
        lv = (ListView)myFragmentView.findViewById(R.id.transaction_list);

        //Turn clicks on
        lv.setClickable(true);
        lv.setLongClickable(true);

        //Set Listener for regular mouse click
        lv.setOnItemClickListener(new OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> l, View v, int position, long id) {
                if (mActionMode != null) {
                    listItemChecked(position);
                }
                else{
                    int selectionRowID = (int) adapterTransactions.getItemId(position);
                    String item = adapterTransactions.getTransaction(position).name;

                    Toast.makeText(Transactions.this.getActivity(), "Click\nRow: " + selectionRowID + "\nEntry: " + item, Toast.LENGTH_SHORT).show();
                }
            }// end onItemClick

        }//end onItemClickListener
        );//end setOnItemClickListener

        lv.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                if (mActionMode != null) {
                    return false;
                }

                listItemChecked(position);
                return true;
            }
        });


        //Set up a listener for changes in settings menu
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
        prefs.registerOnSharedPreferenceChangeListener(this);

        adapterCategory = new SimpleCursorAdapter(this.getActivity(), android.R.layout.simple_spinner_item, null, new String[] {DatabaseHelper.SUBCATEGORY_NAME}, new int[] { android.R.id.text1 },0);
        adapterCategory.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        adapterTransactions = new TransactionsListViewAdapter(this.getActivity(), null);
        lv.setAdapter(adapterTransactions);

        //Call Loaders to get data
        populate();

        //Arguments
        Bundle bundle=getArguments();

        //bundle is empty if from search, so don't add extra menu options
        if(bundle!=null){
            setHasOptionsMenu(true);
        }

        setRetainInstance(false);

        return myFragmentView;
    }

    //Used for ActionMode
    public void listItemChecked(int position){
        adapterTransactions.toggleSelection(position);
        boolean hasCheckedItems = adapterTransactions.getSelectedCount() > 0;

        if (hasCheckedItems && mActionMode == null){
            mActionMode = getSherlockActivity().startActionMode(new MyActionMode());
        }
        else if (!hasCheckedItems && mActionMode != null){
            ((ActionMode) mActionMode).finish();
        }

        if(mActionMode != null){
            ((ActionMode) mActionMode).invalidate();
            ((ActionMode)mActionMode).setTitle(String.valueOf(adapterTransactions.getSelectedCount()));
        }
    }

    //Populate view with all the transactions of selected account
    protected void populate(){
        Bundle bundle=getArguments();
        boolean searchFragment=true;

        if(bundle!=null){
            if(bundle.getBoolean("showAll")){
                showAllTransactions = true;
            }
            else{
                showAllTransactions = false;
            }

            if(bundle.getBoolean("boolSearch")){
                searchFragment = true;
            }
            else{
                searchFragment = false;
            }

            if(!showAllTransactions && !searchFragment){
                account_id = bundle.getInt("ID");
            }

            Log.v("Transactions-populate","searchFragment="+searchFragment+"\nshowAllTransactions="+showAllTransactions+"\nAccount_id="+account_id);
        }

        if(showAllTransactions){
            Bundle b = new Bundle();
            b.putBoolean("boolShowAll", true);
            Log.v("Transactions-populate","start loader (all transactions)...");
            getLoaderManager().initLoader(TRANS_LOADER, b, this);
        }
        else if(searchFragment){
            String query = getActivity().getIntent().getStringExtra("query");

            try{
                Bundle b = new Bundle();
                b.putBoolean("boolSearch", true);
                b.putString("query", query);
                Log.v("Transactions-populate","start search loader...");
                getLoaderManager().initLoader(TRANS_SEARCH_LOADER, b, this);
            }
            catch(Exception e){
                Log.e("Transactions-populate", "Search Failed. Error e=" + e);
                Toast.makeText(this.getActivity(), "Search Failed\n"+e, Toast.LENGTH_SHORT).show();
                //return;
            }

        }
        else{
            Bundle b = new Bundle();
            b.putInt("aID", account_id);
            Log.v("Transactions-populate","start loader ("+DatabaseHelper.TRANS_ACCT_ID+"="+ account_id + ")...");
            getLoaderManager().initLoader(TRANS_LOADER, b, this);
        }

        //Load the categories
        getLoaderManager().initLoader(TRANS_SUBCATEGORY_LOADER, null, this);
    }

    //For Adding a Transaction
    public void transactionAdd(){
        if(account_id==0){
            Log.e("Transaction-AddDialog", "No account selected before attempting to add transaction...");
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
            alertDialogBuilder.setTitle("No Account Selected");
            alertDialogBuilder.setMessage("Please select an account before attempting to add a transaction");
            alertDialogBuilder.setNeutralButton("Okay", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    dialog.cancel();
                }
            });

            alertDialogBuilder.create().show();
        }
        else{
            DialogExample frag = DialogExample.newInstance(null);
            frag.show(getChildFragmentManager(), "dialogAdd");
        }
    }//end of transactionAdd

    //For Sorting Transactions
    public void transactionSort(){
        DialogFragment newFragment = SortDialogFragment.newInstance();
        newFragment.show(getChildFragmentManager(), "dialogSort");
    }
    //For Menu
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        View account_frame = getActivity().findViewById(R.id.account_frag_frame);

        if(account_frame!=null){
            SubMenu subMMenuTransaction = menu.addSubMenu("Transaction");
            subMMenuTransaction.add(com.actionbarsherlock.view.Menu.NONE, R.id.transaction_menu_add, com.actionbarsherlock.view.Menu.NONE, "Add");
            subMMenuTransaction.add(com.actionbarsherlock.view.Menu.NONE, R.id.transaction_menu_schedule, com.actionbarsherlock.view.Menu.NONE, "Schedule");
            subMMenuTransaction.add(com.actionbarsherlock.view.Menu.NONE, R.id.transaction_menu_sort, com.actionbarsherlock.view.Menu.NONE, "Sort");

            MenuItem subMenu1Item = subMMenuTransaction.getItem();
            subMenu1Item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
        else{
            menu.clear();
            inflater.inflate(R.layout.transaction_menu, menu);
            SearchWidget searchWidget = new SearchWidget(getSherlockActivity(),menu.findItem(R.id.transaction_menu_search).getActionView());
        }

    }

    //For Menu Items
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //Intent intentUp = new Intent(Transactions.this.getActivity(), Main.class);
                //intentUp.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                //startActivity(intentUp);
                //menu.toggle();
                break;

            case R.id.transaction_menu_add:
                transactionAdd();
                return true;

            case R.id.transaction_menu_schedule:
                Intent intentPlans = new Intent(getActivity(), Plans.class);
                getActivity().startActivity(intentPlans);
                return true;

            case R.id.transaction_menu_sort:
                transactionSort();
                return true;

        }

        return super.onOptionsItemSelected(item);
    }

    //Used after a change in settings occurs
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Log.e("Transactions-onSharedPreferenceChanged","Options Changed");
        if(!isDetached()){
            Log.e("Transactions-onSharedPreferenceChanged","Transaction is attached");
            //Toast.makeText(this.getActivity(), "Transaction is attached", Toast.LENGTH_SHORT).show();
            //populate();
        }
        else{
            Log.e("Transactions-onSharedPreferenceChanged","Transaction is detached");
            //Toast.makeText(this.getActivity(), "Transaction is detached", Toast.LENGTH_SHORT).show();
        }
    }

    //Method to help create TimePicker
    public static class TimePickerFragment extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current time as the default values for the picker
            final Calendar cal = Calendar.getInstance();

            SimpleDateFormat dateFormatHour = new SimpleDateFormat("hh");
            SimpleDateFormat dateFormatMinute = new SimpleDateFormat("mm");

            int hour = Integer.parseInt(dateFormatHour.format(cal.getTime()));
            int minute = Integer.parseInt(dateFormatMinute.format(cal.getTime()));

            return new TimePickerDialog(getActivity(), this, hour, minute,
                    false);
        }

        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            DateTime time = new DateTime();
            time.setStringSQL(hourOfDay + ":" + minute);

            if(tTime!=null){
                tTime.setText(time.getReadableTime());
            }

            if(TransactionOptionalFragment.mPage!=null){
                TransactionOptionalFragment.mPage.getData().putString(TransactionOptionalPage.TIME_DATA_KEY, time.getReadableTime());
                TransactionOptionalFragment.mPage.notifyDataChanged();
            }

        }
    }

    //Method to help create DatePicker
    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current date as the default date in the picker
            final Calendar cal = Calendar.getInstance();

            SimpleDateFormat dateFormatYear = new SimpleDateFormat("yyyy");
            SimpleDateFormat dateFormatMonth = new SimpleDateFormat("MM");
            SimpleDateFormat dateFormatDay = new SimpleDateFormat("dd");

            int year = Integer.parseInt(dateFormatYear.format(cal.getTime()));
            int month = Integer.parseInt(dateFormatMonth.format(cal.getTime()))-1;
            int day = Integer.parseInt(dateFormatDay.format(cal.getTime()));

            // Create a new instance of DatePickerDialog and return it
            return new DatePickerDialog(getActivity(), this, year, month, day);
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            DateTime date = new DateTime();
            date.setStringSQL(year + "-" + (month+1) + "-" + day);

            if(tDate!=null){
                tDate.setText(date.getReadableDate());
            }

            if(TransactionOptionalFragment.mPage!=null){
                TransactionOptionalFragment.mPage.getData().putString(TransactionOptionalPage.DATE_DATA_KEY, date.getReadableDate());
                TransactionOptionalFragment.mPage.notifyDataChanged();
            }

        }
    }

    //Class that handles sort dialog
    public static class SortDialogFragment extends SherlockDialogFragment {

        public static SortDialogFragment newInstance() {
            SortDialogFragment frag = new SortDialogFragment();
            Bundle args = new Bundle();
            frag.setArguments(args);
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LayoutInflater li = LayoutInflater.from(this.getSherlockActivity());
            View transactionSortView = li.inflate(R.layout.sort_transactions, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this.getSherlockActivity());

            alertDialogBuilder.setView(transactionSortView);
            alertDialogBuilder.setTitle("Sort");
            alertDialogBuilder.setCancelable(true);

            ListView sortOptions = (ListView)transactionSortView.findViewById(R.id.sort_options);
            sortOptions.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {

                    switch (position) {
                        //Newest
                        case 0:
                            sortOrder = DatabaseHelper.TRANS_DATE + " DESC, " + DatabaseHelper.TRANS_TIME + " DESC";
                            break;

                        //Oldest
                        case 1:
                            sortOrder = DatabaseHelper.TRANS_DATE + " ASC, " + DatabaseHelper.TRANS_TIME + " ASC";
                            break;

                        //Largest
                        case 2:
                            sortOrder = DatabaseHelper.TRANS_TYPE+" ASC, CAST ("+DatabaseHelper.TRANS_VALUE+" AS INTEGER)" + " DESC";
                            break;

                        //Smallest
                        case 3:
                            sortOrder = DatabaseHelper.TRANS_TYPE+" ASC, CAST ("+DatabaseHelper.TRANS_VALUE+" AS INTEGER)" + " ASC";
                            break;

                        //Category
                        case 4:
                            sortOrder = DatabaseHelper.TRANS_CATEGORY + " ASC";
                            break;

                        //Type
                        case 5:
                            sortOrder = DatabaseHelper.TRANS_TYPE + " ASC";
                            break;

                        //Alphabetical
                        case 6:
                            sortOrder = DatabaseHelper.TRANS_NAME + " ASC";
                            break;

                        //None
                        case 7:
                            sortOrder = null;
                            break;

                        default:
                            Log.e("Transactions-SortFragment","Unknown Sorting Option!");
                            break;

                    }//end switch

                    getDialog().cancel();
                }
            });

            return alertDialogBuilder.create();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {
        getSherlockActivity().setSupportProgressBarIndeterminateVisibility(true);

        switch (loaderID) {
            case TRANS_LOADER:
                if(bundle!=null && bundle.getBoolean("boolShowAll")){
                    Log.v("Transactions-onCreateLoader","new loader (ShowAll) created");
                    return new CursorLoader(
                            getActivity(),   	// Parent activity context
                            MyContentProvider.TRANSACTIONS_URI,// Table to query
                            null,     			// Projection to return
                            null,            	// No selection clause
                            null,            	// No selection arguments
                            sortOrder          	// Default sort order
                    );
                }
                else{
                    String selection = DatabaseHelper.TRANS_ACCT_ID+"=" + account_id;
                    Log.v("Transactions-onCreateLoader","new loader created");
                    return new CursorLoader(
                            getActivity(),   			// Parent activity context
                            MyContentProvider.TRANSACTIONS_URI,// Table to query
                            null,     					// Projection to return
                            selection,					// No selection clause
                            null,						// No selection arguments
                            sortOrder             		// Default sort order
                    );
                }
            case TRANS_SEARCH_LOADER:
                String query = getActivity().getIntent().getStringExtra("query");
                Log.v("Transactions-onCreateLoader","new loader (boolSearch "+ query + ") created");
                return new CursorLoader(
                        getActivity(),   	// Parent activity context
                        (Uri.parse(MyContentProvider.TRANSACTIONS_URI + "/SEARCH/" + query)),// Table to query
                        null,     			// Projection to return
                        null,            	// No selection clause
                        null,            	// No selection arguments
                        sortOrder           // Default sort order
                );

            case TRANS_SUBCATEGORY_LOADER:
                Log.v("Transactions-onCreateLoader","new category loader created");
                return new CursorLoader(
                        getActivity(),   	// Parent activity context
                        MyContentProvider.SUBCATEGORIES_URI,// Table to query
                        null,     			// Projection to return
                        null,            	// No selection clause
                        null,            	// No selection arguments
                        sortOrder           // Default sort order
                );

            default:
                Log.e("Transactions-onCreateLoader", "Not a valid CursorLoader ID");
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        TextView footerTV = (TextView)this.myFragmentView.findViewById(R.id.transaction_footer);

        switch(loader.getId()){
            case TRANS_LOADER:
                adapterTransactions.swapCursor(data);
                Log.v("Transactions-onLoadFinished", "loader finished. loader="+loader.getId() + " data="+data + " data size="+data.getCount());

                final int valueColumn = data.getColumnIndex(DatabaseHelper.TRANS_VALUE);
                final int typeColumn = data.getColumnIndex(DatabaseHelper.TRANS_TYPE);
                BigDecimal totalBalance = BigDecimal.ZERO;
                Locale locale=getResources().getConfiguration().locale;

                data.moveToPosition(-1);
                while(data.moveToNext()){
                    if(data.getString(typeColumn).equals("Deposit")){
                        totalBalance = totalBalance.add(new Money(data.getString(valueColumn)).getBigDecimal(locale));
                    }
                    else{
                        totalBalance = totalBalance.subtract(new Money(data.getString(valueColumn)).getBigDecimal(locale));
                    }
                }

                try{
                    TextView noResult = (TextView)myFragmentView.findViewById(R.id.transaction_noTransaction);
                    lv.setEmptyView(noResult);
                    noResult.setText("No Transactions\n\n To Add A Transaction, Please Use The ActionBar On The Top");

                    footerTV.setText("Total Balance: " + new Money(totalBalance).getNumberFormat(locale));
                }
                catch(Exception e){
                    Log.e("Transactions-onLoadFinished", "Error setting balance TextView. e="+e);
                }

                if(account_id!=0){
                    ContentValues values = new ContentValues();
                    values.put(DatabaseHelper.ACCOUNT_BALANCE, totalBalance+"");
                    getActivity().getContentResolver().update(Uri.parse(MyContentProvider.ACCOUNTS_URI+"/"+account_id), values,"AcctID ="+account_id, null);
                }

                break;

            case TRANS_SEARCH_LOADER:
                adapterTransactions.swapCursor(data);
                Log.v("Transactions-onLoadFinished", "loader finished. loader="+loader.getId() + " data="+data + " data size="+data.getCount());

                try{
                    TextView noResult = (TextView)myFragmentView.findViewById(R.id.transaction_noTransaction);
                    lv.setEmptyView(noResult);
                    noResult.setText("No Transactions Found");

                    footerTV.setText("Search Results");
                }
                catch(Exception e){
                    Log.e("Transactions-onLoadFinished", "Error setting search TextView. e="+e);
                }
                break;

            case TRANS_SUBCATEGORY_LOADER:
                adapterCategory.swapCursor(data);
                break;

            default:
                Log.e("Transactions-onLoadFinished", "Error. Unknown loader ("+loader.getId());
                break;
        }

        if(!getSherlockActivity().getSupportLoaderManager().hasRunningLoaders()){
            getSherlockActivity().setSupportProgressBarIndeterminateVisibility(false);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch(loader.getId()){
            case TRANS_LOADER:
                adapterTransactions.swapCursor(null);
                Log.v("Transactions-onLoaderReset", "loader reset. loader="+loader.getId());
                break;

            case TRANS_SEARCH_LOADER:
                adapterTransactions.swapCursor(null);
                Log.v("Transactions-onLoaderReset", "loader reset. loader="+loader.getId());
                break;

            case TRANS_SUBCATEGORY_LOADER:
                adapterCategory.swapCursor(null);
                Log.v("Transactions-onLoaderReset", "loader reset. loader="+loader.getId());
                break;

            default:
                Log.e("Transactions-onLoadFinished", "Error. Unknown loader ("+loader.getId());
                break;
        }
    }

    private final class MyActionMode implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            menu.add(0, CONTEXT_MENU_VIEW, 0, "View").setIcon(android.R.drawable.ic_menu_view);
            menu.add(0, CONTEXT_MENU_EDIT, 1, "Edit").setIcon(android.R.drawable.ic_menu_edit);
            menu.add(0, CONTEXT_MENU_DELETE, 2, "Delete").setIcon(android.R.drawable.ic_menu_delete);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            menu.clear();
            if (adapterTransactions.getSelectedCount() == 1 && mode != null) {
                menu.add(0, CONTEXT_MENU_VIEW, 0, "View").setIcon(android.R.drawable.ic_menu_view);
                menu.add(0, CONTEXT_MENU_EDIT, 1, "Edit").setIcon(android.R.drawable.ic_menu_edit);
                menu.add(0, CONTEXT_MENU_DELETE, 2, "Delete").setIcon(android.R.drawable.ic_menu_delete);
                return true;
            } else if (adapterTransactions.getSelectedCount() > 1) {
                menu.add(0, CONTEXT_MENU_DELETE, 2, "Delete").setIcon(android.R.drawable.ic_menu_delete);
                return true;
            }

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            SparseBooleanArray selected = adapterTransactions.getSelectedIds();

            switch (item.getItemId()) {
                case CONTEXT_MENU_VIEW:
                    for (int i = 0; i < selected.size(); i++){
                        if (selected.valueAt(i)) {
                            DialogFragment newFragment = TransactionViewFragment.newInstance(adapterTransactions.getTransaction(selected.keyAt(i)).id);
                            newFragment.show(getChildFragmentManager(), "dialogView");
                        }
                    }

                    mode.finish();
                    return true;
                case CONTEXT_MENU_EDIT:
                    for (int i = 0; i < selected.size(); i++){
                        if (selected.valueAt(i)) {
                            //DialogFragment newFragment = EditDialogFragment.newInstance(adapterTransactions.getTransaction(selected.keyAt(i)));
                            //newFragment.show(getChildFragmentManager(), "dialogEdit");

                            final TransactionRecord record = adapterTransactions.getTransaction(selected.keyAt(i));

                            final Bundle bundle = new Bundle();

                            final Bundle bdl1 = new Bundle();
                            bdl1.putInt("id", record.id);
                            bdl1.putInt("acct_id", record.acctId);
                            bdl1.putInt("plan_id", record.planId);
                            bdl1.putString("name",record.name);
                            bdl1.putString("value",record.value);
                            bdl1.putString("type",record.type);
                            bdl1.putString("category",record.category);
                            bundle.putBundle("Transaction Info",bdl1);

                            final Bundle bdl2 = new Bundle();
                            bdl2.putString("checknum",record.checknum);
                            bdl2.putString("memo", record.memo);
                            bdl2.putString("date",record.date);
                            bdl2.putString("time",record.time);
                            bdl2.putString("cleared",record.cleared);
                            bundle.putBundle("Optional",bdl2);

                            final DialogExample frag = DialogExample.newInstance(bundle);
                            frag.show(getChildFragmentManager(), "dialogEdit");
                        }
                    }

                    mode.finish();
                    return true;
                case CONTEXT_MENU_DELETE:
                    TransactionRecord record;
                    for (int i = 0; i < selected.size(); i++){
                        if (selected.valueAt(i)) {
                            record = adapterTransactions.getTransaction(selected.keyAt(i));

                            Uri uri = Uri.parse(MyContentProvider.TRANSACTIONS_URI + "/" + record.id);
                            getActivity().getContentResolver().delete(uri, DatabaseHelper.TRANS_ID+"="+record.id, null);

                            Toast.makeText(getActivity(), "Deleted Transaction:\n" + record.name, Toast.LENGTH_SHORT).show();
                        }
                    }

                    mode.finish();
                    return true;

                default:
                    mode.finish();
                    Log.e("Transactions-onActionItemClciked","ERROR. Clicked " + item);
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode=null;
            adapterTransactions.removeSelection();
        }
    }

    @Override
    public void onDestroyView() {
        if(mActionMode!=null){
            ((ActionMode)mActionMode).finish();
        }

        super.onDestroyView();
    }

    public static class DialogExample extends WizardDialogFragment{

        private AbstractWizardModel mWizardModel = new AddWizardModel(getActivity());

        public static DialogExample newInstance(Bundle bundle) {
            DialogExample frag = new DialogExample();
            frag.setArguments(bundle);
            return frag;
        }

        //Set Style & Theme of Dialog
        @SuppressLint("InlinedApi")
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if(android.os.Build.VERSION.SDK_INT>14){
                setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Light_Dialog);
            }
            else{
                setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Dialog);
            }

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
            View myFragmentView = inflater.inflate(R.layout.wizard, null, false);

            ViewPager mPager = (ViewPager) myFragmentView.findViewById(R.id.pager);
            mPager.setOffscreenPageLimit(5);
            StepPagerStrip mStepPagerStrip = (StepPagerStrip) myFragmentView.findViewById(R.id.strip);
            Button mNextButton = (Button) myFragmentView.findViewById(R.id.next_button);
            Button mPrevButton = (Button) myFragmentView.findViewById(R.id.prev_button);
            setControls(mPager, mStepPagerStrip, mNextButton, mPrevButton);

            //Load Data into Wizard
            final Bundle bundle = getArguments();
            if(bundle!=null){
                mWizardModel.load(bundle);
            }

            return myFragmentView;
        }

//		@Override
//		public void onStart() {
//			super.onStart();
//
//			// safety check
//			if (getDialog() == null) {
//				return;
//			}
//
//			int dialogWidth = 500;
//			int dialogHeight = 600;
//
//			getDialog().getWindow().setLayout(dialogWidth, dialogHeight);
//		}

        //Create Wizard
        @Override
        public AbstractWizardModel onCreateModel() {
            return mWizardModel;
        }

        //Method that runs after wizard is finished
        @Override
        public void onSubmit() {
            final Bundle bundleInfo = mWizardModel.findByKey("Transaction Info").getData();
            final Bundle bundleOptional = mWizardModel.findByKey("Optional").getData();
            final Locale locale=getResources().getConfiguration().locale;

            String value="";
            final DateTime transactionDate = new DateTime();
            transactionDate.setStringReadable(bundleOptional.getString(TransactionOptionalPage.DATE_DATA_KEY).trim());
            final DateTime transactionTime = new DateTime();
            transactionTime.setStringReadable(bundleOptional.getString(TransactionOptionalPage.TIME_DATA_KEY).trim());

            //Check to see if value is a number
            boolean validValue = false;
            try{
                Money transactionValue = new Money(bundleInfo.getString(TransactionInfoPage.VALUE_DATA_KEY).trim());
                value = transactionValue.getBigDecimal(locale)+"";
                validValue=true;
            }
            catch(Exception e){
                validValue=false;
                Toast.makeText(getActivity(), "Please enter a valid value", Toast.LENGTH_SHORT).show();
            }

            if(validValue){
                getDialog().cancel();

                if(getArguments()!=null){
                    ContentValues transactionValues=new ContentValues();
                    transactionValues.put(DatabaseHelper.TRANS_ID, bundleInfo.getInt(TransactionInfoPage.ID_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_ACCT_ID, bundleInfo.getInt(TransactionInfoPage.ACCOUNT_ID_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_PLAN_ID, bundleInfo.getInt(TransactionInfoPage.PLAN_ID_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_NAME, bundleInfo.getString(TransactionInfoPage.NAME_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_VALUE, value);
                    transactionValues.put(DatabaseHelper.TRANS_TYPE, bundleInfo.getString(TransactionInfoPage.TYPE_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_CATEGORY, bundleInfo.getString(TransactionInfoPage.CATEGORY_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_CHECKNUM, bundleOptional.getString(TransactionOptionalPage.CHECKNUM_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_MEMO, bundleOptional.getString(TransactionOptionalPage.MEMO_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_TIME, transactionTime.getSQLTime(locale));
                    transactionValues.put(DatabaseHelper.TRANS_DATE, transactionDate.getSQLDate(locale));
                    transactionValues.put(DatabaseHelper.TRANS_CLEARED, bundleOptional.getString(TransactionOptionalPage.CLEARED_DATA_KEY));

                    getActivity().getContentResolver().update(Uri.parse(MyContentProvider.TRANSACTIONS_URI+"/"+bundleInfo.getInt(TransactionInfoPage.ID_DATA_KEY)), transactionValues, DatabaseHelper.TRANS_ID+"="+bundleInfo.getInt(TransactionInfoPage.ID_DATA_KEY), null);
                }
                else{
                    ContentValues transactionValues=new ContentValues();
                    transactionValues.put(DatabaseHelper.TRANS_ACCT_ID, account_id);
                    transactionValues.put(DatabaseHelper.TRANS_PLAN_ID, 0);
                    transactionValues.put(DatabaseHelper.TRANS_NAME, bundleInfo.getString(TransactionInfoPage.NAME_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_VALUE, value);
                    transactionValues.put(DatabaseHelper.TRANS_TYPE, bundleInfo.getString(TransactionInfoPage.TYPE_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_CATEGORY, bundleInfo.getString(TransactionInfoPage.CATEGORY_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_CHECKNUM, bundleOptional.getString(TransactionOptionalPage.CHECKNUM_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_MEMO, bundleOptional.getString(TransactionOptionalPage.MEMO_DATA_KEY));
                    transactionValues.put(DatabaseHelper.TRANS_TIME, transactionTime.getSQLTime(locale));
                    transactionValues.put(DatabaseHelper.TRANS_DATE, transactionDate.getSQLDate(locale));
                    transactionValues.put(DatabaseHelper.TRANS_CLEARED, bundleOptional.getString(TransactionOptionalPage.CLEARED_DATA_KEY));

                    getActivity().getContentResolver().insert(MyContentProvider.TRANSACTIONS_URI, transactionValues);
                }

            }

        }

        //Allow back button to be used to go back a step in the wizard
        @Override
        public boolean useBackForPrevious() {
            return true;
        }

    }

    public static class AddWizardModel extends AbstractWizardModel {
        public AddWizardModel(Context context) {
            super(context);
        }

        @Override
        protected PageList onNewRootPageList() {
            return new PageList(

                    new TransactionInfoPage(this, "Transaction Info")
                            .setRequired(true),

                    new TransactionOptionalPage(this, "Optional")
            );
        }

    }

    public static class TransactionInfoFragment extends SherlockFragment {
        private static final String ARG_KEY = "transaction_info_key";

        private PageFragmentCallbacks mCallbacks;
        private String mKey;
        private TransactionInfoPage mPage;
        private EditText mNameView;
        private EditText mValueView;
        private Spinner mTypeView;
        private Spinner mCategoryView;

        public static TransactionInfoFragment create(String key) {
            Bundle args = new Bundle();
            args.putString(ARG_KEY, key);

            TransactionInfoFragment fragment = new TransactionInfoFragment();
            fragment.setArguments(args);
            return fragment;
        }

        public TransactionInfoFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = getArguments();
            mKey = args.getString(ARG_KEY);
            mPage = (TransactionInfoPage) mCallbacks.onGetPage(mKey);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.transaction_page_info, container, false);
            ((TextView) rootView.findViewById(android.R.id.title)).setText(mPage.getTitle());

            mNameView = ((EditText) rootView.findViewById(R.id.transaction_name));
            mNameView.setText(mPage.getData().getString(TransactionInfoPage.NAME_DATA_KEY));

            mValueView = ((EditText) rootView.findViewById(R.id.transaction_value));
            mValueView.setText(mPage.getData().getString(TransactionInfoPage.VALUE_DATA_KEY));

            mTypeView = (Spinner) rootView.findViewById(R.id.spinner_transaction_type);
            if(mPage.getData().getString(TransactionInfoPage.TYPE_DATA_KEY)==null || mPage.getData().getString(TransactionInfoPage.TYPE_DATA_KEY).equals("Withdraw")){
                mTypeView.setSelection(0);
            }
            else{
                mTypeView.setSelection(1);
            }

            mCategoryView = (Spinner) rootView.findViewById(R.id.spinner_transaction_category);
            mCategoryView.setAdapter(adapterCategory);

            String category = mPage.getData().getString(TransactionInfoPage.CATEGORY_DATA_KEY);
            final int count = adapterCategory.getCount();
            String catName;
            Cursor cursor;

            if(category!=null){
                for (int i = 0; i < count; i++) {
                    cursor = (Cursor) mCategoryView.getItemAtPosition(i);
                    catName = cursor.getString(cursor.getColumnIndex(DatabaseHelper.SUBCATEGORY_NAME));
                    if (catName.contentEquals(category)) {
                        mCategoryView.setSelection(i);
                        break;
                    }
                }
            }

            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);

            if (!(activity instanceof PageFragmentCallbacks)) {
                mCallbacks = (PageFragmentCallbacks) getParentFragment();
            }
            else{
                mCallbacks = (PageFragmentCallbacks) activity;
            }
        }

        @Override
        public void onDetach() {
            super.onDetach();
            mCallbacks = null;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mNameView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1,
                                              int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mPage.getData().putString(TransactionInfoPage.NAME_DATA_KEY,
                            (editable != null) ? editable.toString() : null);
                    mPage.notifyDataChanged();
                }
            });

            mValueView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1,
                                              int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mPage.getData().putString(TransactionInfoPage.VALUE_DATA_KEY,
                            (editable != null) ? editable.toString() : null);
                    mPage.notifyDataChanged();
                }
            });

            mTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    Object item = parent.getItemAtPosition(pos);
                    mPage.getData().putString(TransactionInfoPage.TYPE_DATA_KEY,item.toString());
                    mPage.notifyDataChanged();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            mCategoryView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    Cursor cursor = (Cursor) adapterCategory.getItem(pos);
                    String category = cursor.getString(cursor.getColumnIndex(DatabaseHelper.SUBCATEGORY_NAME));

                    mPage.getData().putString(TransactionInfoPage.CATEGORY_DATA_KEY,category);
                    mPage.notifyDataChanged();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

        }

        @Override
        public void setMenuVisibility(boolean menuVisible) {
            super.setMenuVisibility(menuVisible);

            // In a future update to the support library, this should override setUserVisibleHint
            // instead of setMenuVisibility.
            if (mNameView != null) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                if (!menuVisible) {
                    imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
                }
            }
        }
    }

    public static class TransactionOptionalFragment extends SherlockFragment {
        private static final String ARG_KEY = "transaction_optional_key";

        private PageFragmentCallbacks mCallbacks;
        private String mKey;
        private static TransactionOptionalPage mPage;
        private EditText mCheckNumView;
        private AutoCompleteTextView mMemoView;
        private CheckBox mClearedView;

        public static TransactionOptionalFragment create(String key) {
            Bundle args = new Bundle();
            args.putString(ARG_KEY, key);

            TransactionOptionalFragment fragment = new TransactionOptionalFragment();
            fragment.setArguments(args);
            return fragment;
        }

        public TransactionOptionalFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = getArguments();
            mKey = args.getString(ARG_KEY);
            mPage = (TransactionOptionalPage) mCallbacks.onGetPage(mKey);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            final Bundle data = mPage.getData();

            View rootView = inflater.inflate(R.layout.transaction_page_optional, container, false);
            ((TextView) rootView.findViewById(android.R.id.title)).setText(mPage.getTitle());

            mCheckNumView = ((EditText) rootView.findViewById(R.id.transaction_checknum));
            mCheckNumView.setText(data.getString(TransactionOptionalPage.CHECKNUM_DATA_KEY));

            mMemoView = ((AutoCompleteTextView) rootView.findViewById(R.id.transaction_memo));
            mMemoView.setText(data.getString(TransactionOptionalPage.MEMO_DATA_KEY));

            //Adapter for memo's autocomplete
            ArrayAdapter<String> dropdownAdapter = new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_dropdown_item_1line, dropdownResults);
            mMemoView.setAdapter(dropdownAdapter);

            //Add dictionary back to autocomplete
            TextKeyListener input = TextKeyListener.getInstance(true, TextKeyListener.Capitalize.NONE);
            mMemoView.setKeyListener(input);

            tTime = (Button)rootView.findViewById(R.id.transaction_time);
            tDate = (Button)rootView.findViewById(R.id.transaction_date);

            if(data.getString(TransactionOptionalPage.DATE_DATA_KEY)!=null && data.getString(TransactionOptionalPage.DATE_DATA_KEY).length()>0){
                final DateTime date = new DateTime();
                date.setStringSQL(data.getString(TransactionOptionalPage.DATE_DATA_KEY));
                tDate.setText(date.getReadableDate());
                mPage.getData().putString(TransactionOptionalPage.DATE_DATA_KEY, date.getReadableDate());
            }
            if(data.getString(TransactionOptionalPage.TIME_DATA_KEY)!=null && data.getString(TransactionOptionalPage.TIME_DATA_KEY).length()>0){
                final DateTime time = new DateTime();
                time.setStringSQL(data.getString(TransactionOptionalPage.TIME_DATA_KEY));
                tTime.setText(time.getReadableTime());
                mPage.getData().putString(TransactionOptionalPage.TIME_DATA_KEY, time.getReadableTime());
            }
            else if(data.getString(TransactionOptionalPage.DATE_DATA_KEY)==null && data.getString(TransactionOptionalPage.TIME_DATA_KEY)==null){
                final Calendar c = Calendar.getInstance();
                final DateTime date = new DateTime();
                date.setCalendar(c);

                tDate.setText(date.getReadableDate());
                tTime.setText(date.getReadableTime());
                mPage.getData().putString(TransactionOptionalPage.DATE_DATA_KEY, date.getReadableDate());
                mPage.getData().putString(TransactionOptionalPage.TIME_DATA_KEY, date.getReadableTime());
            }

            mClearedView = (CheckBox) rootView.findViewById(R.id.transaction_cleared);
            if(mPage.getData().getString(TransactionOptionalPage.CLEARED_DATA_KEY)!=null){
                mClearedView.setChecked(Boolean.parseBoolean(mPage.getData().getString(TransactionOptionalPage.CLEARED_DATA_KEY)));
            }
            else{
                mClearedView.setChecked(true);
                mPage.getData().putString(TransactionOptionalPage.CLEARED_DATA_KEY, "true");
            }

            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);

            if (!(activity instanceof PageFragmentCallbacks)) {
                mCallbacks = (PageFragmentCallbacks) getParentFragment();
            }
            else{
                mCallbacks = (PageFragmentCallbacks) activity;
            }
        }

        @Override
        public void onDetach() {
            super.onDetach();
            mCallbacks = null;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mCheckNumView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1,
                                              int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mPage.getData().putString(TransactionOptionalPage.CHECKNUM_DATA_KEY,
                            (editable != null) ? editable.toString() : null);
                    mPage.notifyDataChanged();
                }
            });

            mMemoView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1,
                                              int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mPage.getData().putString(TransactionOptionalPage.MEMO_DATA_KEY,
                            (editable != null) ? editable.toString() : null);
                    mPage.notifyDataChanged();
                }
            });

            mClearedView.setOnCheckedChangeListener(new OnCheckedChangeListener() {

                public void onCheckedChanged(CompoundButton buttonView,
                                             boolean isChecked) {
                    if (mClearedView.isChecked()) {
                        mPage.getData().putString(TransactionOptionalPage.CLEARED_DATA_KEY, "true");
                    }
                    else
                    {
                        mPage.getData().putString(TransactionOptionalPage.CLEARED_DATA_KEY, "false");
                    }

                    mPage.notifyDataChanged();
                }
            });

        }

        @Override
        public void setMenuVisibility(boolean menuVisible) {
            super.setMenuVisibility(menuVisible);

            // In a future update to the support library, this should override setUserVisibleHint
            // instead of setMenuVisibility.
            if (mCheckNumView != null) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                if (!menuVisible) {
                    imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
                }
            }
        }
    }

    public static class TransactionInfoPage extends Page {
        public static final String ID_DATA_KEY = "id";
        public static final String ACCOUNT_ID_DATA_KEY = "acct_id";
        public static final String PLAN_ID_DATA_KEY = "plan_id";

        public static final String NAME_DATA_KEY = "name";
        public static final String VALUE_DATA_KEY = "value";
        public static final String TYPE_DATA_KEY = "type";
        public static final String CATEGORY_DATA_KEY = "category";

        public TransactionInfoPage(ModelCallbacks callbacks, String title) {
            super(callbacks, title);
        }

        @Override
        public Fragment createFragment() {
            return TransactionInfoFragment.create(getKey());
        }

        @Override
        public void getReviewItems(ArrayList<ReviewItem> dest) {
            dest.add(new ReviewItem("Name", mData.getString(NAME_DATA_KEY), getKey(), -1));
            dest.add(new ReviewItem("Value", mData.getString(VALUE_DATA_KEY), getKey(), -1));
            dest.add(new ReviewItem("Type", mData.getString(TYPE_DATA_KEY), getKey(), -1));
            dest.add(new ReviewItem("Category", mData.getString(CATEGORY_DATA_KEY), getKey(), -1));
        }

        @Override
        public boolean isCompleted() {
            return !TextUtils.isEmpty(mData.getString(NAME_DATA_KEY)) && !TextUtils.isEmpty(mData.getString(VALUE_DATA_KEY));
        }
    }

    public static class TransactionOptionalPage extends Page {
        public static final String CHECKNUM_DATA_KEY = "checknum";
        public static final String MEMO_DATA_KEY = "memo";
        public static final String DATE_DATA_KEY = "date";
        public static final String TIME_DATA_KEY = "time";
        public static final String CLEARED_DATA_KEY = "cleared";

        public TransactionOptionalPage(ModelCallbacks callbacks, String title) {
            super(callbacks, title);
        }

        @Override
        public Fragment createFragment() {
            return TransactionOptionalFragment.create(getKey());
        }

        @Override
        public void getReviewItems(ArrayList<ReviewItem> dest) {
            dest.add(new ReviewItem("Check Number", mData.getString(CHECKNUM_DATA_KEY), getKey(), -1));
            dest.add(new ReviewItem("Memo", mData.getString(MEMO_DATA_KEY), getKey(), -1));
            dest.add(new ReviewItem("Date", mData.getString(DATE_DATA_KEY), getKey(), -1));
            dest.add(new ReviewItem("Time", mData.getString(TIME_DATA_KEY), getKey(), -1));
            dest.add(new ReviewItem("Cleared", mData.getString(CLEARED_DATA_KEY), getKey(), -1));
        }
    }

}//end Transactions