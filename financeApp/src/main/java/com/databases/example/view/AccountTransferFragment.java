package com.databases.example.view;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.databases.example.R;
import com.databases.example.app.Accounts;
import com.databases.example.data.DatabaseHelper;
import com.databases.example.data.DateTime;
import com.databases.example.data.MyContentProvider;

import java.util.Calendar;
import java.util.Locale;

//Class that handles transfers fragment
public class AccountTransferFragment extends SherlockDialogFragment{
    /**TODO Spinners aren't populated with the account names anymore!**/

    public static AccountTransferFragment newInstance() {
        AccountTransferFragment frag = new AccountTransferFragment();
        Bundle args = new Bundle();
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LayoutInflater li = LayoutInflater.from(getActivity());
        final View promptsView = li.inflate(R.layout.account_transfer, null);
        final EditText tAmount = (EditText) promptsView.findViewById(R.id.EditAccountAmount);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setView(promptsView);
        alertDialogBuilder.setTitle("Transfer Money");

        Accounts.transferSpinnerFrom = (Spinner)promptsView.findViewById(R.id.SpinnerAccountFrom);
        Accounts.transferSpinnerTo = (Spinner)promptsView.findViewById(R.id.SpinnerAccountTo);

        //Populate Account Drop-down List
        ((Accounts) getParentFragment()).accountPopulate();

        //Set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("Transfer",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {

                                //Needed to get account's name from DB-populated spinner
                                int accountPosition1 = Accounts.transferSpinnerFrom.getSelectedItemPosition();
                                Cursor cursorAccount1 = (Cursor) Accounts.transferSpinnerAdapterFrom.getItem(accountPosition1);

                                int accountPosition2 = Accounts.transferSpinnerTo.getSelectedItemPosition();
                                Cursor cursorAccount2 = (Cursor) Accounts.transferSpinnerAdapterTo.getItem(accountPosition2);

                                String transferAmount = tAmount.getText().toString().trim();
                                String transferFrom = null;
                                String transferTo = null;
                                String transferToID = null;
                                String transferFromID = null;

                                try{
                                    transferFrom = cursorAccount1.getString(cursorAccount1.getColumnIndex(DatabaseHelper.ACCOUNT_NAME));
                                    transferFromID = cursorAccount1.getString(cursorAccount1.getColumnIndex("_id"));
                                    transferTo = cursorAccount2.getString(cursorAccount2.getColumnIndex(DatabaseHelper.ACCOUNT_NAME));
                                    transferToID = cursorAccount2.getString(cursorAccount2.getColumnIndex("_id"));
                                }
                                catch(Exception e){
                                    Log.e("Account-transferDialog", "No Accounts? Exception e=" + e);
                                    dialog.cancel();
                                    Toast.makeText(getActivity(), "No Accounts \n\nUse The ActionBar To Create Accounts", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                Log.d("Account-Transfer", "From:"+transferFrom + " To:"+transferTo + " Amount:" + transferAmount);

                                //Transfer From
                                final Calendar cal = Calendar.getInstance();
                                Locale locale=getResources().getConfiguration().locale;
                                DateTime transferDate = new DateTime();
                                transferDate.setDate(cal.getTime());

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

                                ContentValues transferValues=new ContentValues();

                                try{
                                    transferValues.put(DatabaseHelper.TRANS_ACCT_ID, transferFromID);
                                    transferValues.put(DatabaseHelper.TRANS_PLAN_ID, transferPlanId);
                                    transferValues.put(DatabaseHelper.TRANS_NAME, transferName);
                                    transferValues.put(DatabaseHelper.TRANS_VALUE, tAmount);
                                    transferValues.put(DatabaseHelper.TRANS_TYPE, transferType);
                                    transferValues.put(DatabaseHelper.TRANS_CATEGORY, transferCategory);
                                    transferValues.put(DatabaseHelper.TRANS_CHECKNUM, transferCheckNum);
                                    transferValues.put(DatabaseHelper.TRANS_MEMO, transferMemo);
                                    transferValues.put(DatabaseHelper.TRANS_TIME, transferDate.getSQLTime(locale));
                                    transferValues.put(DatabaseHelper.TRANS_DATE, transferDate.getSQLDate(locale));
                                    transferValues.put(DatabaseHelper.TRANS_CLEARED, transferCleared);

                                    //Insert values into transaction table
                                    getActivity().getContentResolver().insert(MyContentProvider.TRANSACTIONS_URI, transferValues);

                                    //Update Account Info
                                    ContentValues accountValues=new ContentValues();

                                    Cursor c = getActivity().getContentResolver().query(Uri.parse(MyContentProvider.ACCOUNTS_URI + "/" + transferFromID), null, null, null, null);

                                    int entry_id = 0;
                                    String entry_name = null;
                                    String entry_balance = null;
                                    String entry_time = null;
                                    String entry_date = null;

                                    c.moveToFirst();
                                    do{
                                        entry_id = c.getInt(c.getColumnIndex(DatabaseHelper.ACCOUNT_ID));
                                        entry_name = c.getString(c.getColumnIndex(DatabaseHelper.ACCOUNT_NAME));
                                        entry_balance = Float.parseFloat(c.getString(c.getColumnIndex(DatabaseHelper.ACCOUNT_BALANCE)))-tAmount+"";
                                        entry_time = c.getString(c.getColumnIndex(DatabaseHelper.ACCOUNT_TIME));
                                        entry_date = c.getString(c.getColumnIndex(DatabaseHelper.ACCOUNT_DATE));
                                    }while(c.moveToNext());

                                    accountValues.put(DatabaseHelper.ACCOUNT_ID,entry_id);
                                    accountValues.put(DatabaseHelper.ACCOUNT_NAME,entry_name);
                                    accountValues.put(DatabaseHelper.ACCOUNT_BALANCE,entry_balance);
                                    accountValues.put(DatabaseHelper.ACCOUNT_TIME,entry_time);
                                    accountValues.put(DatabaseHelper.ACCOUNT_DATE,entry_date);

                                    getActivity().getContentResolver().update(Uri.parse(MyContentProvider.ACCOUNTS_URI+"/"+transferFromID), accountValues,DatabaseHelper.ACCOUNT_ID+"="+transferFromID, null);
                                    c.close();

                                } catch(Exception e){
                                    Log.e("Accounts-transferDialog", "Transfer From failed. Exception e="+e);
                                    Toast.makeText(getActivity(), "Error Transferring!\n Did you enter valid input? ", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                //Transfer To
                                transferType = "Deposit";

                                try{
                                    transferValues.clear();
                                    transferValues.put(DatabaseHelper.TRANS_ACCT_ID, transferToID);
                                    transferValues.put(DatabaseHelper.TRANS_PLAN_ID, transferPlanId);
                                    transferValues.put(DatabaseHelper.TRANS_NAME, transferName);
                                    transferValues.put(DatabaseHelper.TRANS_VALUE, tAmount);
                                    transferValues.put(DatabaseHelper.TRANS_TYPE, transferType);
                                    transferValues.put(DatabaseHelper.TRANS_CATEGORY, transferCategory);
                                    transferValues.put(DatabaseHelper.TRANS_CHECKNUM, transferCheckNum);
                                    transferValues.put(DatabaseHelper.TRANS_MEMO, transferMemo);
                                    transferValues.put(DatabaseHelper.TRANS_TIME, transferDate.getSQLTime(locale));
                                    transferValues.put(DatabaseHelper.TRANS_DATE, transferDate.getSQLDate(locale));
                                    transferValues.put(DatabaseHelper.TRANS_CLEARED, transferCleared);

                                    //Insert values into transaction table
                                    getActivity().getContentResolver().insert(MyContentProvider.TRANSACTIONS_URI, transferValues);

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
                                        entry_id = c.getInt(c.getColumnIndex(DatabaseHelper.ACCOUNT_ID));
                                        entry_name = c.getString(c.getColumnIndex(DatabaseHelper.ACCOUNT_NAME));
                                        entry_balance = Float.parseFloat(c.getString(c.getColumnIndex(DatabaseHelper.ACCOUNT_BALANCE)))+tAmount+"";
                                        entry_time = c.getString(c.getColumnIndex(DatabaseHelper.ACCOUNT_TIME));
                                        entry_date = c.getString(c.getColumnIndex(DatabaseHelper.ACCOUNT_DATE));
                                    }while(c.moveToNext());

                                    accountValues.put(DatabaseHelper.ACCOUNT_ID,entry_id);
                                    accountValues.put(DatabaseHelper.ACCOUNT_NAME,entry_name);
                                    accountValues.put(DatabaseHelper.ACCOUNT_BALANCE,entry_balance);
                                    accountValues.put(DatabaseHelper.ACCOUNT_TIME,entry_time);
                                    accountValues.put(DatabaseHelper.ACCOUNT_DATE,entry_date);

                                    getActivity().getContentResolver().update(Uri.parse(MyContentProvider.ACCOUNTS_URI+"/"+transferToID), accountValues,DatabaseHelper.ACCOUNT_ID+"="+transferToID, null);
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
